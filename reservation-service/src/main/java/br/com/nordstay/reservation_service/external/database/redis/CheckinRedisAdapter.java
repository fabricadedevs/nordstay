package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckinPort;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckinInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CheckinRedisAdapter implements RegistrarCheckinPort {

    private static final long IDEMPOTENCY_TTL_SECONDS = 24 * 60 * 60;
    private static final String OUTBOX_KEY = "outbox:reservation";

    private static final DefaultRedisScript<String> REGISTRAR_CHECKIN_SCRIPT =
            new DefaultRedisScript<>("""
                    local respostaExistente = redis.call('GET', KEYS[2])
                    if respostaExistente then
                        return 'DUPLICATE\\n' .. respostaExistente
                    end

                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 'NOT_FOUND'
                    end

                    local statusAtual = redis.call('HGET', KEYS[1], 'status')
                    if statusAtual ~= 'CONFIRMADA' then
                        return 'INVALID_STATUS\\n' .. (statusAtual or '')
                    end

                    local tipoOutbox = redis.call('TYPE', KEYS[3]).ok
                    if tipoOutbox ~= 'none' and tipoOutbox ~= 'list' then
                        return 'INVALID_OUTBOX_TYPE'
                    end

                    redis.call('HSET', KEYS[1],
                        'status', 'CHECKED_IN',
                        'checkinEm', ARGV[2],
                        'acompanhantes', ARGV[3],
                        'previsaoChegada', ARGV[4])
                    redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[5])
                    redis.call('RPUSH', KEYS[3], ARGV[6])

                    return 'CREATED\\n' .. ARGV[1]
                    """, String.class);

    private final StringRedisTemplate redisTemplate;

    public CheckinRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<CheckinAceito> buscarResposta(String reservaId, UUID idempotencyKey) {
        String resposta = redisTemplate.opsForValue().get(chaveIdempotencia(reservaId, idempotencyKey));
        if (resposta == null) {
            return Optional.empty();
        }
        return Optional.of(desserializarResposta(resposta));
    }

    @Override
    public CheckinAceito registrar(
            Reserva reserva,
            UUID idempotencyKey,
            CheckinAceito resposta,
            OffsetDateTime checkinEm,
            Integer acompanhantes,
            LocalTime previsaoChegada) {
        String respostaJson = serializar(resposta);
        String eventoJson = serializarEvento(reserva, resposta, checkinEm);
        String reservaKey = chaveReserva(reserva.reservaId());
        String idempotenciaKey = chaveIdempotencia(reserva.reservaId(), idempotencyKey);

        String resultado = redisTemplate.execute(
                REGISTRAR_CHECKIN_SCRIPT,
                List.of(reservaKey, idempotenciaKey, OUTBOX_KEY),
                respostaJson,
                checkinEm.toString(),
                acompanhantes.toString(),
                previsaoChegada == null ? "" : previsaoChegada.toString(),
                Long.toString(IDEMPOTENCY_TTL_SECONDS),
                eventoJson
        );

        if (resultado == null) {
            throw new IllegalStateException("Redis não retornou o resultado do check-in");
        }
        if (resultado.equals("NOT_FOUND")) {
            throw new ReservaNaoEncontradaException();
        }
        if (resultado.startsWith("INVALID_STATUS\n")) {
            throw statusInvalido(reserva.reservaId(), resultado.substring("INVALID_STATUS\n".length()));
        }
        if (resultado.equals("INVALID_OUTBOX_TYPE")) {
            throw new IllegalStateException("A chave da outbox não é uma lista Redis");
        }
        if (resultado.startsWith("CREATED\n")) {
            return desserializarResposta(resultado.substring("CREATED\n".length()));
        }
        if (resultado.startsWith("DUPLICATE\n")) {
            return desserializarResposta(resultado.substring("DUPLICATE\n".length()));
        }

        throw new IllegalStateException("Resultado inesperado do Redis ao registrar check-in");
    }

    private String serializar(CheckinAceito resposta) {
        return List.of(
                        resposta.reservaId(),
                        resposta.quartoId(),
                        resposta.status(),
                        resposta.correlationId().toString(),
                        resposta.websocket()
                ).stream()
                .map(this::codificarCampo)
                .collect(Collectors.joining("."));
    }

    private CheckinAceito desserializarResposta(String json) {
        String[] campos = json.split("\\.", -1);
        if (campos.length != 5) {
            throw new IllegalStateException("Resposta idempotente do check-in inválida no Redis");
        }
        try {
            return new CheckinAceito(
                    decodificarCampo(campos[0]),
                    decodificarCampo(campos[1]),
                    decodificarCampo(campos[2]),
                    UUID.fromString(decodificarCampo(campos[3])),
                    decodificarCampo(campos[4])
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Resposta idempotente do check-in inválida no Redis", exception);
        }
    }

    private String serializarEvento(Reserva reserva, CheckinAceito resposta, OffsetDateTime checkinEm) {
        OffsetDateTime checkoutPrevisto =
                reserva.saida().atTime(12, 0).atOffset(checkinEm.getOffset());
        return """
                {"eventId":"%s","tipo":"CHECKIN_REALIZADO","reservaId":"%s","quartoId":"%s","checkoutPrevisto":"%s","ocorridoEm":"%s","correlationId":"%s"}"""
                .formatted(
                        UUID.randomUUID(),
                        escaparJson(reserva.reservaId()),
                        escaparJson(reserva.quartoId()),
                        checkoutPrevisto,
                        checkinEm,
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

    private StatusCheckinInvalidoException statusInvalido(String reservaId, String status) {
        try {
            return new StatusCheckinInvalidoException(reservaId, StatusReserva.valueOf(status));
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
