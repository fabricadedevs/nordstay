package br.com.nordstay.reservation_service.external;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
import br.com.nordstay.reservation_service.application.port.in.PublicarOutboxUseCase;
import br.com.nordstay.reservation_service.external.messaging.KafkaPublicarEventoAdapter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "reservation.outbox.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CheckinFluxoIntegracaoTest {

    private static final String TOPIC = "nordstay.checkin-realizado";
    private static final String DOCUMENTO_FINAL = "9132";
    private static final String QUARTO_BASE = "Q-IT-";
    private static final ZoneId FUSO_CHECKIN = ZoneId.of("America/Recife");
    private static final DateTimeFormatter DATA_ID = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicarOutboxUseCase publicarOutbox;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    @MockitoSpyBean
    private KafkaPublicarEventoAdapter kafkaPublicador;

    private final List<ReservaTeste> reservasCriadas = new ArrayList<>();

    @BeforeEach
    void garantirTopicoKafka() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            try {
                admin.createTopics(List.of(new NewTopic(TOPIC, 6, (short) 1)))
                        .all()
                        .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
        }
    }

    @AfterEach
    void limparDadosDeTeste() {
        for (ReservaTeste reserva : reservasCriadas) {
            redisTemplate.delete(chaveReserva(reserva.reservaId()));
            redisTemplate.delete(chaveIdempotencia(reserva.reservaId(), reserva.idempotencyKey()));
            removerDaOutbox(reserva.reservaId(), reserva.quartoId());
        }
    }

    @Test
    @Order(1)
    void atualizaReservaDevolveRespostaIdempotenteEPublicaUmEventoComChaveDoQuarto()
            throws Exception {
        ReservaTeste reserva = criarReserva(9001);
        try (KafkaConsumer<String, String> consumer = novoConsumer()) {
            aguardarAtribuicao(consumer);

            MvcResult primeira = enviarCheckin(reserva);
            MvcResult repetida = enviarCheckin(reserva);

            assertEquals(202, primeira.getResponse().getStatus());
            assertEquals(primeira.getResponse().getContentAsString(),
                    repetida.getResponse().getContentAsString());
            assertEquals("CHECKED_IN",
                    redisTemplate.opsForHash().get(chaveReserva(reserva.reservaId()), "status"));
            assertEquals("1",
                    redisTemplate.opsForHash().get(chaveReserva(reserva.reservaId()), "acompanhantes"));
            assertEquals("15:00",
                    redisTemplate.opsForHash().get(chaveReserva(reserva.reservaId()), "previsaoChegada"));
            assertNotNull(redisTemplate.opsForHash().get(chaveReserva(reserva.reservaId()), "checkinEm"));
            assertEquals(1L, redisTemplate.opsForList().size("outbox:reservation"));
            assertTrue(publicarOutbox.publicarProximo());
            assertFalse(publicarOutbox.publicarProximo());

            List<ConsumerRecord<String, String>> eventos =
                    aguardarEventos(consumer, reserva.reservaId());
            assertEquals(1, eventos.size());
            ConsumerRecord<String, String> evento = eventos.getFirst();
            assertEquals(reserva.quartoId(), evento.key());
            assertTrue(evento.value().contains("\"tipo\":\"nordstay.checkin-realizado\""));
            assertTrue(evento.value().contains("\"reservaId\":\"" + reserva.reservaId() + "\""));
            assertTrue(evento.value().contains("\"correlacaoId\":\"" + reserva.reservaId() + "\""));
            assertEquals(0L, redisTemplate.opsForList().size("outbox:reservation"));
        }
    }

    @Test
    @Order(2)
    void mantemEventoNaOutboxDepoisDeFalhaEPublicaNaTentativaSeguinte() throws Exception {
        ReservaTeste reserva = criarReserva(9002);
        try (KafkaConsumer<String, String> consumer = novoConsumer()) {
            aguardarAtribuicao(consumer);
            assertEquals(202, enviarCheckin(reserva).getResponse().getStatus());

            doThrow(new IllegalStateException("Falha Kafka simulada"))
                    .doCallRealMethod()
                    .when(kafkaPublicador)
                    .publicar(anyString(), anyString());

            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class,
                    publicarOutbox::publicarProximo
            );
            assertEquals(1L, redisTemplate.opsForList().size("outbox:reservation"));
            assertTrue(publicarOutbox.publicarProximo());

            List<ConsumerRecord<String, String>> eventos =
                    aguardarEventos(consumer, reserva.reservaId());
            assertEquals(1, eventos.size());
            ConsumerRecord<String, String> evento = eventos.getFirst();
            assertEquals(reserva.quartoId(), evento.key());
            assertTrue(evento.value().contains("\"reservaId\":\"" + reserva.reservaId() + "\""));
            assertEquals(0L, redisTemplate.opsForList().size("outbox:reservation"));
            verify(kafkaPublicador, times(2)).publicar(anyString(), anyString());
        }
    }

    @Test
    @Order(3)
    void medeP95DoEndpointComRedisAbaixoDeTrezentosMilissegundos() throws Exception {
        int aquecimento = 10;
        int amostras = 100;
        List<Long> latenciasNanos = new ArrayList<>(amostras);

        for (int indice = 0; indice < aquecimento + amostras; indice++) {
            ReservaTeste reserva = criarReserva(9100 + indice);
            long inicio = System.nanoTime();
            MvcResult resultado = enviarCheckin(reserva);
            long decorrido = System.nanoTime() - inicio;

            assertEquals(202, resultado.getResponse().getStatus());
            if (indice >= aquecimento) {
                latenciasNanos.add(decorrido);
            }
        }

        latenciasNanos.sort(Long::compareTo);
        long p95Nanos = latenciasNanos.get((int) Math.ceil(amostras * 0.95) - 1);
        double p95Millis = p95Nanos / 1_000_000.0;
        System.out.printf("Check-in REST + Redis: p95 observado = %.2f ms (%d amostras)%n",
                p95Millis, amostras);

        assertTrue(p95Millis < 300.0,
                "p95 observado foi %.2f ms; requisito: abaixo de 300 ms".formatted(p95Millis));
    }

    private MvcResult enviarCheckin(ReservaTeste reserva) throws Exception {
        return mockMvc.perform(post("/api/reservas/{reservaId}/checkin", reserva.reservaId())
                        .header("Idempotency-Key", reserva.idempotencyKey())
                        .header("X-Documento-Final", DOCUMENTO_FINAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acompanhantes":1,"aceiteTermos":true,"previsaoChegada":"15:00"}
                                """))
                .andReturn();
    }

    private ReservaTeste criarReserva(int sequencia) {
        LocalDate entrada = LocalDate.now(FUSO_CHECKIN);
        String reservaId = "RSV-%s-%04d".formatted(entrada.format(DATA_ID), sequencia);
        String quartoId = QUARTO_BASE + "%04d".formatted(sequencia);
        UUID idempotencyKey = UUID.randomUUID();
        OffsetDateTime checkout = entrada.plusDays(3).atTime(12, 0)
                .atZone(FUSO_CHECKIN)
                .toOffsetDateTime();

        redisTemplate.opsForHash().putAll(chaveReserva(reservaId), Map.of(
                "reservaId", reservaId,
                "quartoId", quartoId,
                "hospedeNome", "Hóspede de integração",
                "hospedeDocumentoMascarado", "***.***.*91-32",
                "hospedeDocumentoFinal", DOCUMENTO_FINAL,
                "status", "CONFIRMADA",
                "dataCheckin", entrada.toString(),
                "checkoutPrevisto", checkout.toString(),
                "diariasCentavos", "186000"
        ));

        ReservaTeste reserva = new ReservaTeste(reservaId, quartoId, idempotencyKey);
        reservasCriadas.add(reserva);
        return reserva;
    }

    private KafkaConsumer<String, String> novoConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "checkin-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(TOPIC));
        return consumer;
    }

    private void aguardarAtribuicao(KafkaConsumer<String, String> consumer) {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (consumer.assignment().isEmpty() && System.nanoTime() < limite) {
            consumer.poll(Duration.ofMillis(100));
        }
        assertFalse(consumer.assignment().isEmpty(), "Kafka consumer não recebeu partição");
    }

    private List<ConsumerRecord<String, String>> aguardarEventos(
            KafkaConsumer<String, String> consumer,
            String reservaId) {
        List<ConsumerRecord<String, String>> eventos = new ArrayList<>();
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (eventos.isEmpty() && System.nanoTime() < limite) {
            adicionarEventosCorrespondentes(consumer, reservaId, eventos);
        }
        if (eventos.isEmpty()) {
            return fail("Evento de check-in não foi recebido pelo Kafka");
        }

        long limiteDuplicatas = System.nanoTime() + Duration.ofMillis(500).toNanos();
        while (System.nanoTime() < limiteDuplicatas) {
            adicionarEventosCorrespondentes(consumer, reservaId, eventos);
        }
        return eventos;
    }

    private void adicionarEventosCorrespondentes(
            KafkaConsumer<String, String> consumer,
            String reservaId,
            List<ConsumerRecord<String, String>> eventos) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(100))) {
            if (record.value().contains("\"reservaId\":\"" + reservaId + "\"")) {
                eventos.add(record);
            }
        }
    }

    private void removerDaOutbox(String reservaId, String quartoId) {
        List<String> eventos = redisTemplate.opsForList().range("outbox:reservation", 0, -1);
        if (eventos == null) {
            return;
        }
        for (String evento : eventos) {
            if (evento.contains("\"reservaId\":\"" + reservaId + "\"")) {
                redisTemplate.opsForList().remove("outbox:reservation", 1, evento);
                redisTemplate.opsForList().remove("outbox:reservation:partition-keys", 1, quartoId);
            }
        }
    }

    private String chaveReserva(String reservaId) {
        return "reserva:{" + reservaId + "}";
    }

    private String chaveIdempotencia(String reservaId, UUID idempotencyKey) {
        return "idem-http:{" + reservaId + "}:" + idempotencyKey;
    }

    private record ReservaTeste(String reservaId, String quartoId, UUID idempotencyKey) {
    }
}
