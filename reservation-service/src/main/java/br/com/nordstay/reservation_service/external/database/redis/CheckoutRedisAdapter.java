package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckoutPort;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckoutInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CheckoutRedisAdapter implements RegistrarCheckoutPort {

    private static final long IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60;
    private static final String OUTBOX_KEY = "outbox:reservation";
    private static final String OUTBOX_PARTITION_KEYS_KEY = "outbox:reservation:partition-keys";

    private static final DefaultRedisScript<String> REGISTRAR_CHECKOUT_SCRIPT =
            new DefaultRedisScript<>("""
                    local respostaExistente = redis.call('GET', KEYS[2])
                    if respostaExistente then
                        return 'DUPLICATE\\n' .. respostaExistente
                    end

                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 'NOT_FOUND'
                    end

                    local statusAtual = redis.call('HGET', KEYS[1], 'status')
                    if statusAtual ~= 'CHECKED_IN' then
                        return 'INVALID_STATUS\\n' .. (statusAtual or '')
                    end

                    local quartoId = redis.call('HGET', KEYS[1], 'quartoId')
                    if not quartoId or quartoId == '' then
                        return 'INVALID_RESERVATION'
                    end

                    local tipoOutbox = redis.call('TYPE', KEYS[3]).ok
                    local tipoChavesOutbox = redis.call('TYPE', KEYS[4]).ok
                    if (tipoOutbox ~= 'none' and tipoOutbox ~= 'list')
                        or (tipoChavesOutbox ~= 'none' and tipoChavesOutbox ~= 'list') then
                        return 'INVALID_OUTBOX_TYPE'
                    end

                    redis.call('HSET', KEYS[1],
                        'status', 'CHECKOUT_SOLICITADO',
                        'checkoutEm', ARGV[2])
                    redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])
                    redis.call('RPUSH', KEYS[3], ARGV[4])
                    redis.call('RPUSH', KEYS[4], quartoId)

                    return 'CREATED\\n' .. ARGV[1]
                    """, String.class);

    private final StringRedisTemplate redisTemplate;

    public CheckoutRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<CheckoutAceito> buscarResposta(String reservaId, UUID idempotencyKey) {
        String resposta = redisTemplate.opsForValue().get(chaveIdempotencia(reservaId, idempotencyKey));
        if (resposta == null) {
            return Optional.empty();
        }
        return Optional.of(desserializarResposta(resposta));
    }

    @Override
    public CheckoutAceito registrar(
            Reserva reserva,
            UUID idempotencyKey,
            CheckoutAceito resposta,
            OffsetDateTime checkoutEm) {
        String respostaSerializada = serializarResposta(resposta);
        String evento = serializarEvento(reserva, resposta, checkoutEm);
        String reservaKey = chaveReserva(reserva.reservaId());
        String idempotenciaKey = chaveIdempotencia(reserva.reservaId(), idempotencyKey);

        String resultado = redisTemplate.execute(
                REGISTRAR_CHECKOUT_SCRIPT,
                List.of(reservaKey, idempotenciaKey, OUTBOX_KEY, OUTBOX_PARTITION_KEYS_KEY),
                respostaSerializada,
                checkoutEm.toString(),
                Long.toString(IDEMPOTENCY_TTL_SECONDS),
                evento
        );

        if (resultado == null) {
            throw new IllegalStateException("Redis não retornou o resultado do checkout");
        }
        if (resultado.equals("NOT_FOUND")) {
            throw new ReservaNaoEncontradaException();
        }
        if (resultado.startsWith("INVALID_STATUS\n")) {
            throw statusInvalido(
                    reserva.reservaId(),
                    resultado.substring("INVALID_STATUS\n".length())
            );
        }
        if (resultado.equals("INVALID_RESERVATION")) {
            throw new IllegalStateException("Hash da reserva sem quartoId");
        }
        if (resultado.equals("INVALID_OUTBOX_TYPE")) {
            throw new IllegalStateException("A chave da outbox não é uma lista Redis");
        }
        if (resultado.startsWith("CREATED\n") || resultado.startsWith("DUPLICATE\n")) {
            return desserializarResposta(resultado.substring(resultado.indexOf('\n') + 1));
        }

        throw new IllegalStateException("Resultado inesperado do Redis ao registrar checkout");
    }

    private String serializarResposta(CheckoutAceito resposta) {
        return List.of(
                        resposta.reservaId(),
                        resposta.status(),
                        resposta.correlationId().toString()
                ).stream()
                .map(this::codificarCampo)
                .collect(Collectors.joining("."));
    }

    private CheckoutAceito desserializarResposta(String valor) {
        String[] campos = valor.split("\\.", -1);
        if (campos.length != 3) {
            throw new IllegalStateException("Resposta idempotente do checkout inválida no Redis");
        }
        try {
            return new CheckoutAceito(
                    decodificarCampo(campos[0]),
                    decodificarCampo(campos[1]),
                    UUID.fromString(decodificarCampo(campos[2]))
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Resposta idempotente do checkout inválida no Redis", exception);
        }
    }

    private String serializarEvento(
            Reserva reserva,
            CheckoutAceito resposta,
            OffsetDateTime checkoutEm) {
        return """
                {"eventId":"%s","tipo":"CHECKOUT_SOLICITADO","reservaId":"%s","quartoId":"%s","ocorridoEm":"%s","correlationId":"%s"}"""
                .formatted(
                        UUID.randomUUID(),
                        escaparJson(reserva.reservaId()),
                        escaparJson(reserva.quartoId()),
                        checkoutEm,
                        resposta.correlationId()
                );
    }

    private String escaparJson(String valor) {
        StringBuilder escapado = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char caractere = valor.charAt(i);
            switch (caractere) {
                case '"' -> escapado.append("\\\"");
                case '\\' -> escapado.append("\\\\");
                case '\b' -> escapado.append("\\b");
                case '\f' -> escapado.append("\\f");
                case '\n' -> escapado.append("\\n");
                case '\r' -> escapado.append("\\r");
                case '\t' -> escapado.append("\\t");
                default -> {
                    if (caractere < 0x20) {
                        escapado.append("\\u%04x".formatted((int) caractere));
                    } else {
                        escapado.append(caractere);
                    }
                }
            }
        }
        return escapado.toString();
    }

    private String codificarCampo(String valor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(valor.getBytes(StandardCharsets.UTF_8));
    }

    private String decodificarCampo(String valor) {
        return new String(Base64.getUrlDecoder().decode(valor), StandardCharsets.UTF_8);
    }

    private StatusCheckoutInvalidoException statusInvalido(String reservaId, String status) {
        try {
            return new StatusCheckoutInvalidoException(reservaId, StatusReserva.valueOf(status));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Status inválido armazenado na reserva " + reservaId,
                    exception
            );
        }
    }

    private String chaveReserva(String reservaId) {
        return "reserva:{" + reservaId + "}";
    }

    private String chaveIdempotencia(String reservaId, UUID idempotencyKey) {
        return "idem-http:{" + reservaId + "}:" + idempotencyKey;
    }
}
