package br.com.nordstay.reservation_service.external.messaging;

import br.com.nordstay.reservation_service.application.port.out.PublicarEventoPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaPublicarEventoAdapter implements PublicarEventoPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long publishTimeoutMillis;

    public KafkaPublicarEventoAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${reservation.outbox.topic:nordstay.checkin-realizado}") String topic,
            @Value("${reservation.outbox.publish-timeout-ms:25000}") long publishTimeoutMillis) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeoutMillis = publishTimeoutMillis;
    }

    @Override
    public void publicar(String chave, String payload) {
        try {
            kafkaTemplate.send(topic, chave, payload).get(publishTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação do evento Kafka foi interrompida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Falha ao confirmar publicação do evento no Kafka", exception);
        }
    }
}
