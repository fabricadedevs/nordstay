package br.com.nordstay.reservation_service.external.messaging;

import br.com.nordstay.reservation_service.application.port.out.PublicarEventoPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaPublicarEventoAdapter implements PublicarEventoPort {

    private static final String TIPO_CHECKOUT_SOLICITADO = "CHECKOUT_SOLICITADO";
    private static final Pattern TIPO_EVENTO_PATTERN =
            Pattern.compile("\"tipo\"\\s*:\\s*\"([^\"\\\\]*)\"");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String checkinTopic;
    private final String checkoutTopic;
    private final long publishTimeoutMillis;

    public KafkaPublicarEventoAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${reservation.outbox.topic:nordstay.checkin-realizado}") String checkinTopic,
            @Value("${reservation.checkout.topic:nordstay.checkout-solicitado}") String checkoutTopic,
            @Value("${reservation.outbox.publish-timeout-ms:25000}") long publishTimeoutMillis) {
        this.kafkaTemplate = kafkaTemplate;
        this.checkinTopic = checkinTopic;
        this.checkoutTopic = checkoutTopic;
        this.publishTimeoutMillis = publishTimeoutMillis;
    }

    @Override
    public void publicar(String chave, String payload) {
        String topic = selecionarTopico(payload);
        try {
            kafkaTemplate.send(topic, chave, payload).get(publishTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação do evento Kafka foi interrompida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Falha ao confirmar publicação do evento no Kafka", exception);
        }
    }

    private String selecionarTopico(String payload) {
        Matcher matcher = TIPO_EVENTO_PATTERN.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Evento da outbox sem campo tipo válido");
        }

        String tipo = matcher.group(1);
        if (TIPO_CHECKOUT_SOLICITADO.equals(tipo)) {
            return checkoutTopic;
        }
        if ("nordstay.checkin-realizado".equals(tipo)) {
            return checkinTopic;
        }
        throw new IllegalArgumentException("Tipo de evento sem tópico configurado: " + tipo);
    }
}
