package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckoutInvalidoException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.TransicaoCheckoutInvalidaException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "reservation.outbox.enabled=false")
@Import(TestcontainersConfiguration.class)
class CheckoutRedisAdapterIntegrationTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String RESERVA_KEY = "reserva:{" + RESERVA_ID + "}";
    private static final String OUTBOX_KEY = "outbox:reservation";
    private static final String OUTBOX_PARTITION_KEYS_KEY = "outbox:reservation:partition-keys";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("0f8e3b6a-1d2c-4e5f-9a7b-6c4d2e1f0a93");
    private static final String IDEMPOTENCY_REDIS_KEY =
            "idem-http:{" + RESERVA_ID + "}:" + IDEMPOTENCY_KEY;
    private static final OffsetDateTime CHECKOUT_EM =
            OffsetDateTime.parse("2026-11-23T10:41:37-03:00");

    @Autowired
    private CheckoutRedisAdapter adapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void limparRedis() {
        redisTemplate.delete(RESERVA_KEY);
        redisTemplate.delete(IDEMPOTENCY_REDIS_KEY);
        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        if (eventos != null) {
            for (String evento : eventos) {
                if (evento.contains(RESERVA_ID)) {
                    redisTemplate.opsForList().remove(OUTBOX_KEY, 1, evento);
                    redisTemplate.opsForList().remove(OUTBOX_PARTITION_KEYS_KEY, 1, "Q-214");
                }
            }
        }
    }

    @Test
    void atualizaReservaGuardaRespostaEEnfileiraEventoAtomicamente() {
        criarReservaNoRedis("CHECKED_IN");
        CheckoutAceito resposta = criarResposta();

        CheckoutAceito registrada = adapter.registrar(
                criarReserva(StatusReserva.CHECKED_IN),
                IDEMPOTENCY_KEY,
                resposta,
                CHECKOUT_EM
        );

        assertEquals(resposta, registrada);
        assertEquals("CHECKOUT_SOLICITADO", redisTemplate.opsForHash().get(RESERVA_KEY, "status"));
        assertEquals(CHECKOUT_EM.toString(), redisTemplate.opsForHash().get(RESERVA_KEY, "checkoutEm"));
        assertEquals(resposta, adapter.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY).orElseThrow());
        Long ttl = redisTemplate.getExpire(IDEMPOTENCY_REDIS_KEY);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 24 * 60 * 60);

        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        assertNotNull(eventos);
        assertEquals(1, eventos.stream().filter(evento -> evento.contains(RESERVA_ID)).count());
        String evento = eventos.stream()
                .filter(item -> item.contains(RESERVA_ID))
                .findFirst()
                .orElseThrow();
        assertTrue(evento.contains("\"tipo\":\"CHECKOUT_SOLICITADO\""));
        assertTrue(evento.contains("\"quartoId\":\"Q-214\""));
        assertTrue(evento.contains("\"ocorridoEm\":\"" + CHECKOUT_EM + "\""));
        assertTrue(evento.contains("\"correlationId\":\"" + resposta.correlationId() + "\""));
        assertEquals("Q-214", redisTemplate.opsForList().index(OUTBOX_PARTITION_KEYS_KEY, 0));
    }

    @Test
    void repeticaoDeChaveDevolveRespostaOriginalSemNovoEvento() {
        criarReservaNoRedis("CHECKED_IN");
        CheckoutAceito respostaOriginal = criarResposta();

        CheckoutAceito primeira = adapter.registrar(
                criarReserva(StatusReserva.CHECKED_IN),
                IDEMPOTENCY_KEY,
                respostaOriginal,
                CHECKOUT_EM
        );
        CheckoutAceito repetida = adapter.registrar(
                criarReserva(StatusReserva.CHECKED_IN),
                IDEMPOTENCY_KEY,
                new CheckoutAceito(RESERVA_ID, "CHECKOUT_SOLICITADO", UUID.randomUUID()),
                CHECKOUT_EM.plusMinutes(1)
        );

        assertEquals(primeira, repetida);
        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        assertNotNull(eventos);
        assertEquals(1, eventos.stream().filter(evento -> evento.contains(RESERVA_ID)).count());
    }

    @Test
    void rejeitaStatusInvalidoSemAlterarReservaNemOutbox() {
        criarReservaNoRedis("CHECKED_OUT");

        assertThrows(
                StatusCheckoutInvalidoException.class,
                () -> adapter.registrar(
                        criarReserva(StatusReserva.CHECKED_IN),
                        IDEMPOTENCY_KEY,
                        criarResposta(),
                        CHECKOUT_EM
                )
        );

        assertEquals("CHECKED_OUT", redisTemplate.opsForHash().get(RESERVA_KEY, "status"));
        assertTrue(adapter.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY).isEmpty());
        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        assertTrue(eventos == null
                || eventos.stream().noneMatch(evento -> evento.contains(RESERVA_ID)));
    }

    @Test
    void concluiCheckoutETrataRepeticaoComoSucesso() {
        criarReservaNoRedis("CHECKOUT_SOLICITADO");

        adapter.marcarComoConcluido(RESERVA_ID);
        adapter.marcarComoConcluido(RESERVA_ID);

        assertEquals("CHECKED_OUT", redisTemplate.opsForHash().get(RESERVA_KEY, "status"));
    }

    @Test
    void rejeitaConclusaoQuandoReservaEstaEmEstadoIncompativel() {
        criarReservaNoRedis("CHECKED_IN");

        assertThrows(
                TransicaoCheckoutInvalidaException.class,
                () -> adapter.marcarComoConcluido(RESERVA_ID)
        );

        assertEquals("CHECKED_IN", redisTemplate.opsForHash().get(RESERVA_KEY, "status"));
    }

    @Test
    void rejeitaConclusaoQuandoReservaNaoExiste() {
        assertThrows(
                ReservaNaoEncontradaException.class,
                () -> adapter.marcarComoConcluido(RESERVA_ID)
        );
    }

    private void criarReservaNoRedis(String status) {
        redisTemplate.opsForHash().putAll(RESERVA_KEY, Map.of(
                "reservaId", RESERVA_ID,
                "quartoId", "Q-214",
                "status", status,
                "hospedeNome", "Hóspede de teste",
                "hospedeDocumentoMascarado", "***.***.*91-32",
                "hospedeDocumentoFinal", "9132",
                "dataCheckin", "2026-11-20",
                "checkoutPrevisto", "2026-11-23T12:00:00-03:00",
                "diariasCentavos", "186000"
        ));
    }

    private static Reserva criarReserva(StatusReserva status) {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                status,
                new Hospede("Hóspede de teste", new Documento("***.***.*91-32", "9132")),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );
    }

    private static CheckoutAceito criarResposta() {
        return new CheckoutAceito(
                RESERVA_ID,
                "CHECKOUT_SOLICITADO",
                UUID.fromString("e3b9a1c7-5d2f-4a86-b0c4-9f7e1d3a5b62")
        );
    }
}
