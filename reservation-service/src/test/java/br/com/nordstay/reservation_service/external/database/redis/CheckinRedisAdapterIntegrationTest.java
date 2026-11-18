package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;
import br.com.nordstay.reservation_service.application.port.out.EventoPendente;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckinInvalidoException;
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
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "reservation.outbox.enabled=false")
@Import(TestcontainersConfiguration.class)
class CheckinRedisAdapterIntegrationTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String RESERVA_KEY = "reserva:{" + RESERVA_ID + "}";
    private static final String OUTBOX_KEY = "outbox:reservation";
    private static final String OUTBOX_PARTITION_KEYS_KEY = "outbox:reservation:partition-keys";
    private static final String OUTBOX_LOCK_KEY = "outbox:reservation:publisher-lock";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("6c0d6a5e-3f2b-4b1a-9e8c-2d7f4a1b9c30");
    private static final String IDEMPOTENCY_REDIS_KEY =
            "idem-http:{" + RESERVA_ID + "}:" + IDEMPOTENCY_KEY;
    private static final OffsetDateTime CHECKIN_EM =
            OffsetDateTime.parse("2026-11-20T14:02:11-03:00");

    @Autowired
    private CheckinRedisAdapter adapter;

    @Autowired
    private OutboxRedisAdapter outboxAdapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void limparRedis() {
        redisTemplate.delete(RESERVA_KEY);
        redisTemplate.delete(IDEMPOTENCY_REDIS_KEY);
        redisTemplate.delete(OUTBOX_LOCK_KEY);
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
    void persisteEstadoRespostaIdempotenteEEventoNaOutbox() throws Exception {
        criarReservaNoRedis("CONFIRMADA");

        CheckinAceito resposta = criarResposta();
        CheckinAceito registrada = adapter.registrar(
                criarReserva(StatusReserva.CONFIRMADA),
                IDEMPOTENCY_KEY,
                resposta,
                CHECKIN_EM,
                1,
                LocalTime.of(15, 0)
        );

        assertEquals(resposta, registrada);
        assertEquals("CHECKED_IN", redisTemplate.opsForHash().get(RESERVA_KEY, "status"));
        assertEquals(CHECKIN_EM.toString(), redisTemplate.opsForHash().get(RESERVA_KEY, "checkinEm"));
        assertEquals("1", redisTemplate.opsForHash().get(RESERVA_KEY, "acompanhantes"));
        assertEquals("15:00", redisTemplate.opsForHash().get(RESERVA_KEY, "previsaoChegada"));

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
        assertTrue(evento.contains("\"tipo\":\"nordstay.checkin-realizado\""));
        assertTrue(evento.contains("\"versao\":1"));
        assertTrue(evento.contains("\"origem\":\"reservation-service\""));
        assertTrue(evento.contains("\"correlacaoId\":\"" + RESERVA_ID + "\""));
        assertTrue(evento.contains("\"quartoId\":\"Q-214\""));
        assertTrue(evento.contains("\"reservaId\":\"" + RESERVA_ID + "\""));
        assertTrue(evento.contains("\"numeroHospedes\":2"));
        assertTrue(evento.contains("\"checkinEm\":\"2026-11-20T17:02:11Z\""));
        assertTrue(evento.contains("\"checkoutPrevisto\":\"2026-11-23T15:00Z\""));
        assertEquals("Q-214", redisTemplate.opsForList().index(OUTBOX_PARTITION_KEYS_KEY, 0));
    }

    @Test
    void reservaSomenteUmPublicadorEConfirmaAsDuasListasAtomicas() {
        String evento = "{\"id\":\"evento-outbox\",\"reservaId\":\"" + RESERVA_ID + "\"}";
        redisTemplate.opsForList().rightPush(OUTBOX_KEY, evento);
        redisTemplate.opsForList().rightPush(OUTBOX_PARTITION_KEYS_KEY, "Q-214");

        var reservado = outboxAdapter.reservarProximo("token-publicador-1").orElseThrow();

        assertEquals(evento, reservado.payload());
        assertEquals("Q-214", reservado.chave());
        assertTrue(outboxAdapter.reservarProximo("token-publicador-2").isEmpty());
        assertTrue(outboxAdapter.confirmarPublicacao("token-publicador-1", reservado));
        assertEquals(0L, redisTemplate.opsForList().size(OUTBOX_KEY));
        assertEquals(0L, redisTemplate.opsForList().size(OUTBOX_PARTITION_KEYS_KEY));
    }

    @Test
    void liberacaoMantemEventoPendenteParaNovaTentativa() {
        String evento = "{\"id\":\"evento-outbox\",\"reservaId\":\"" + RESERVA_ID + "\"}";
        redisTemplate.opsForList().rightPush(OUTBOX_KEY, evento);
        redisTemplate.opsForList().rightPush(OUTBOX_PARTITION_KEYS_KEY, "Q-214");

        EventoPendente primeiraReserva =
                outboxAdapter.reservarProximo("token-publicador-1").orElseThrow();
        outboxAdapter.liberar("token-publicador-1");
        EventoPendente segundaReserva =
                outboxAdapter.reservarProximo("token-publicador-2").orElseThrow();

        assertEquals(primeiraReserva, segundaReserva);
        assertTrue(outboxAdapter.confirmarPublicacao("token-publicador-2", segundaReserva));
    }

    @Test
    void tokenIncorretoNaoRemoveEventoDaOutbox() {
        String evento = "{\"id\":\"evento-outbox\",\"reservaId\":\"" + RESERVA_ID + "\"}";
        redisTemplate.opsForList().rightPush(OUTBOX_KEY, evento);
        redisTemplate.opsForList().rightPush(OUTBOX_PARTITION_KEYS_KEY, "Q-214");

        EventoPendente reservado =
                outboxAdapter.reservarProximo("token-publicador-1").orElseThrow();

        assertFalse(outboxAdapter.confirmarPublicacao("outro-token", reservado));
        assertEquals(evento, redisTemplate.opsForList().index(OUTBOX_KEY, 0));
        assertEquals("Q-214", redisTemplate.opsForList().index(OUTBOX_PARTITION_KEYS_KEY, 0));
    }

    @Test
    void repeticaoDaChaveDevolveRespostaOriginalSemEnfileirarOutroEvento() throws Exception {
        criarReservaNoRedis("CONFIRMADA");
        CheckinAceito respostaOriginal = criarResposta();

        CheckinAceito primeira = adapter.registrar(
                criarReserva(StatusReserva.CONFIRMADA),
                IDEMPOTENCY_KEY,
                respostaOriginal,
                CHECKIN_EM,
                1,
                LocalTime.of(15, 0)
        );
        CheckinAceito repetida = adapter.registrar(
                criarReserva(StatusReserva.CONFIRMADA),
                IDEMPOTENCY_KEY,
                new CheckinAceito(
                        RESERVA_ID,
                        "Q-214",
                        "CHECKIN_EM_PROCESSAMENTO",
                        UUID.randomUUID(),
                        "/ws/estadias/" + RESERVA_ID
                ),
                CHECKIN_EM.plusMinutes(1),
                2,
                LocalTime.of(16, 0)
        );

        assertEquals(primeira, repetida);
        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        assertNotNull(eventos);
        assertEquals(1, eventos.stream().filter(evento -> evento.contains(RESERVA_ID)).count());
    }

    @Test
    void recusaTransicaoQuandoStatusAtualNaoEhConfirmada() {
        criarReservaNoRedis("CHECKED_IN");

        assertThrows(
                StatusCheckinInvalidoException.class,
                () -> adapter.registrar(
                        criarReserva(StatusReserva.CONFIRMADA),
                        IDEMPOTENCY_KEY,
                        criarResposta(),
                        CHECKIN_EM,
                        1,
                        null
                )
        );

        assertEquals("CHECKED_IN", redisTemplate.opsForHash().get(RESERVA_KEY, "status"));
        assertTrue(adapter.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY).isEmpty());
        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        assertTrue(eventos == null
                || eventos.stream().noneMatch(evento -> evento.contains(RESERVA_ID)));
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

    private static CheckinAceito criarResposta() {
        return new CheckinAceito(
                RESERVA_ID,
                "Q-214",
                "CHECKIN_EM_PROCESSAMENTO",
                UUID.fromString("c7a1e2d4-91b3-4f6a-8d0e-5f2b7c9a1e33"),
                "/ws/estadias/" + RESERVA_ID
        );
    }
}
