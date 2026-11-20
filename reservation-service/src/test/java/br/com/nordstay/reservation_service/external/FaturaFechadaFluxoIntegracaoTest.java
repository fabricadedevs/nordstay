package br.com.nordstay.reservation_service.external;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
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
class FaturaFechadaFluxoIntegracaoTest {

    private static final String TOPIC = "nordstay.fatura-fechada";
    private static final String DLT = TOPIC + ".dlt";
    private static final String QUARTO_ID = "Q-214";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    @BeforeEach
    void garantirTopicosKafka() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaContainer.getBootstrapServers()
        ))) {
            List<NewTopic> topicos = List.of(
                    new NewTopic(TOPIC, 6, (short) 1),
                    new NewTopic(DLT, 6, (short) 1)
            );
            try {
                admin.createTopics(topicos).all().get(10, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
        }
    }

    @AfterEach
    void limparRedis() {
        redisTemplate.delete(chaveReserva("RSV-20261120-0042"));
        redisTemplate.delete(chaveReserva("RSV-20261120-0043"));
    }

    @Test
    void consomeFaturaFechadaEConcluiReservaIdempotentemente() throws Exception {
        String reservaId = "RSV-20261120-0042";
        criarReserva(reservaId, "CHECKOUT_SOLICITADO");
        String evento = eventoFaturaFechada(reservaId);

        publicar(evento);
        aguardarStatus(reservaId, "CHECKED_OUT");

        publicar(evento);
        aguardarStatus(reservaId, "CHECKED_OUT");

        assertEquals("CHECKED_OUT", redisTemplate.opsForHash()
                .get(chaveReserva(reservaId), "status"));
    }

    @Test
    void enviaEventoPermanenteParaDltSemMudarStatus() throws Exception {
        String reservaId = "RSV-20261120-0043";
        criarReserva(reservaId, "CHECKED_IN");
        String evento = eventoFaturaFechada(reservaId);

        try (KafkaConsumer<String, String> consumer = novoConsumerDlt()) {
            aguardarAtribuicao(consumer);
            publicar(evento);

            List<ConsumerRecord<String, String>> eventosDlt = aguardarEventoDlt(consumer, reservaId);
            assertEquals(1, eventosDlt.size());
            assertEquals(evento, eventosDlt.getFirst().value());
        }

        assertEquals("CHECKED_IN", redisTemplate.opsForHash()
                .get(chaveReserva(reservaId), "status"));
    }

    private void publicar(String payload) throws Exception {
        kafkaTemplate.send(TOPIC, QUARTO_ID, payload).get(10, TimeUnit.SECONDS);
    }

    private void criarReserva(String reservaId, String status) {
        redisTemplate.opsForHash().putAll(chaveReserva(reservaId), Map.of(
                "reservaId", reservaId,
                "quartoId", QUARTO_ID,
                "status", status
        ));
    }

    private void aguardarStatus(String reservaId, String statusEsperado) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        Object statusAtual = null;
        while (System.nanoTime() < limite) {
            statusAtual = redisTemplate.opsForHash().get(chaveReserva(reservaId), "status");
            if (statusEsperado.equals(statusAtual)) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(statusEsperado, statusAtual, "Reserva não alcançou o status esperado");
    }

    private KafkaConsumer<String, String> novoConsumerDlt() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "fatura-dlt-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(DLT));
        return consumer;
    }

    private void aguardarAtribuicao(KafkaConsumer<String, String> consumer) {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (consumer.assignment().isEmpty() && System.nanoTime() < limite) {
            consumer.poll(Duration.ofMillis(100));
        }
        assertFalse(consumer.assignment().isEmpty(), "Kafka consumer não recebeu partição DLT");
    }

    private List<ConsumerRecord<String, String>> aguardarEventoDlt(
            KafkaConsumer<String, String> consumer,
            String reservaId) {
        List<ConsumerRecord<String, String>> eventos = new ArrayList<>();
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (eventos.isEmpty() && System.nanoTime() < limite) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(100))) {
                if (record.value().contains("\"reservaId\": \"" + reservaId + "\"")) {
                    eventos.add(record);
                }
            }
        }
        if (eventos.isEmpty()) {
            return fail("Evento permanente não foi encaminhado para " + DLT);
        }
        return eventos;
    }

    private String eventoFaturaFechada(String reservaId) {
        return """
                {
                  "id": "%s",
                  "tipo": "nordstay.fatura-fechada",
                  "versao": 1,
                  "ocorridoEm": "2026-11-23T13:45:00Z",
                  "origem": "billing-service",
                  "correlacaoId": "%s",
                  "quartoId": "%s",
                  "dados": {
                    "faturaId": "fat-2026-000311",
                    "reservaId": "%s",
                    "total": { "centavos": 187600, "moeda": "BRL" },
                    "status": "PENDENTE",
                    "fechadaEm": "2026-11-23T13:45:00Z"
                  }
                }
                """.formatted(UUID.randomUUID(), reservaId, QUARTO_ID, reservaId);
    }

    private String chaveReserva(String reservaId) {
        return "reserva:{" + reservaId + "}";
    }
}
