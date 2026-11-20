package br.com.nordstay.reservation_service.external;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.application.port.in.PublicarOutboxUseCase;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckoutPort;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(properties = "reservation.outbox.enabled=false")
@Import(TestcontainersConfiguration.class)
class CheckoutOutboxFluxoIntegracaoTest {

    private static final String CHECKOUT_TOPIC = "nordstay.checkout-solicitado";
    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String QUARTO_ID = "Q-214";
    private static final String RESERVA_KEY = "reserva:{" + RESERVA_ID + "}";
    private static final String OUTBOX_KEY = "outbox:reservation";
    private static final String OUTBOX_PARTITION_KEYS_KEY = "outbox:reservation:partition-keys";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("0f8e3b6a-1d2c-4e5f-9a7b-6c4d2e1f0a93");
    private static final String IDEMPOTENCY_REDIS_KEY =
            "idem-http:{" + RESERVA_ID + "}:" + IDEMPOTENCY_KEY;
    private static final OffsetDateTime CHECKOUT_EM =
            OffsetDateTime.parse("2026-11-23T10:41:37-03:00");
    private static final UUID CORRELATION_ID =
            UUID.fromString("e3b9a1c7-5d2f-4a86-b0c4-9f7e1d3a5b62");

    @Autowired
    private RegistrarCheckoutPort registrarCheckout;

    @Autowired
    private PublicarOutboxUseCase publicarOutbox;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    @BeforeEach
    void garantirTopicoKafka() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            try {
                admin.createTopics(List.of(new NewTopic(CHECKOUT_TOPIC, 6, (short) 1)))
                        .all()
                        .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
        }

        redisTemplate.opsForHash().putAll(RESERVA_KEY, Map.of(
                "reservaId", RESERVA_ID,
                "quartoId", QUARTO_ID,
                "status", "CHECKED_IN",
                "hospedeNome", "Hóspede de integração",
                "hospedeDocumentoMascarado", "***.***.*91-32",
                "hospedeDocumentoFinal", "9132",
                "dataCheckin", "2026-11-20",
                "checkoutPrevisto", "2026-11-23T12:00:00-03:00",
                "diariasCentavos", "186000"
        ));
    }

    @AfterEach
    void limparDadosDeTeste() {
        redisTemplate.delete(RESERVA_KEY);
        redisTemplate.delete(IDEMPOTENCY_REDIS_KEY);
        removerDaOutbox();
    }

    @Test
    void publicaCheckoutNoTopicoProprioEConfirmaOutbox() {
        try (KafkaConsumer<String, String> consumer = novoConsumer()) {
            aguardarAtribuicao(consumer);

            registrarCheckout.registrar(
                    criarReserva(),
                    IDEMPOTENCY_KEY,
                    new CheckoutAceito(RESERVA_ID, "CHECKOUT_SOLICITADO", CORRELATION_ID),
                    CHECKOUT_EM
            );
            assertEquals(1L, redisTemplate.opsForList().size(OUTBOX_KEY));
            assertTrue(publicarOutbox.publicarProximo());
            assertFalse(publicarOutbox.publicarProximo());

            List<ConsumerRecord<String, String>> eventos = aguardarEvento(consumer);
            assertEquals(1, eventos.size());
            ConsumerRecord<String, String> evento = eventos.getFirst();
            assertEquals(QUARTO_ID, evento.key());
            assertTrue(evento.value().contains("\"tipo\":\"CHECKOUT_SOLICITADO\""));
            assertTrue(evento.value().contains("\"reservaId\":\"" + RESERVA_ID + "\""));
            assertTrue(evento.value().contains("\"quartoId\":\"" + QUARTO_ID + "\""));
            assertTrue(evento.value().contains("\"correlationId\":\"" + CORRELATION_ID + "\""));
            assertEquals(0L, redisTemplate.opsForList().size(OUTBOX_KEY));
        }
    }

    private KafkaConsumer<String, String> novoConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "checkout-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(CHECKOUT_TOPIC));
        return consumer;
    }

    private void aguardarAtribuicao(KafkaConsumer<String, String> consumer) {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (consumer.assignment().isEmpty() && System.nanoTime() < limite) {
            consumer.poll(Duration.ofMillis(100));
        }
        assertFalse(consumer.assignment().isEmpty(), "Kafka consumer não recebeu partição");
    }

    private List<ConsumerRecord<String, String>> aguardarEvento(KafkaConsumer<String, String> consumer) {
        List<ConsumerRecord<String, String>> eventos = new ArrayList<>();
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (eventos.isEmpty() && System.nanoTime() < limite) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(100))) {
                if (record.value().contains("\"reservaId\":\"" + RESERVA_ID + "\"")) {
                    eventos.add(record);
                }
            }
        }
        if (eventos.isEmpty()) {
            return fail("Evento de checkout não foi recebido no tópico " + CHECKOUT_TOPIC);
        }

        long limiteDuplicatas = System.nanoTime() + Duration.ofMillis(500).toNanos();
        while (System.nanoTime() < limiteDuplicatas) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(100))) {
                if (record.value().contains("\"reservaId\":\"" + RESERVA_ID + "\"")) {
                    eventos.add(record);
                }
            }
        }
        return eventos;
    }

    private Reserva criarReserva() {
        return new Reserva(
                RESERVA_ID,
                QUARTO_ID,
                StatusReserva.CHECKED_IN,
                new Hospede("Hóspede de integração", new Documento("***.***.*91-32", "9132")),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );
    }

    private void removerDaOutbox() {
        List<String> eventos = redisTemplate.opsForList().range(OUTBOX_KEY, 0, -1);
        if (eventos == null) {
            return;
        }
        for (String evento : eventos) {
            if (evento.contains(RESERVA_ID)) {
                redisTemplate.opsForList().remove(OUTBOX_KEY, 1, evento);
                redisTemplate.opsForList().remove(OUTBOX_PARTITION_KEYS_KEY, 1, QUARTO_ID);
            }
        }
    }
}
