package br.com.nordstay.reservation_service.external.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class KafkaPublicarEventoAdapterTest {

    @Test
    void publicaComChaveDoQuartoEEsperaConfirmacaoDoBroker() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send("nordstay.checkin-realizado", "Q-214", "{\"id\":\"evento-1\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));
        KafkaPublicarEventoAdapter adapter =
                new KafkaPublicarEventoAdapter(kafkaTemplate, "nordstay.checkin-realizado", 1000);

        adapter.publicar("Q-214", "{\"id\":\"evento-1\"}");

        verify(kafkaTemplate).send("nordstay.checkin-realizado", "Q-214", "{\"id\":\"evento-1\"}");
    }

    @Test
    void propagaFalhaDeConfirmacaoDoBroker() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> envio = new CompletableFuture<>();
        envio.completeExceptionally(new IllegalStateException("Broker indisponível"));
        when(kafkaTemplate.send("nordstay.checkin-realizado", "Q-214", "{\"id\":\"evento-1\"}"))
                .thenReturn(envio);
        KafkaPublicarEventoAdapter adapter =
                new KafkaPublicarEventoAdapter(kafkaTemplate, "nordstay.checkin-realizado", 1000);

        assertThrows(
                IllegalStateException.class,
                () -> adapter.publicar("Q-214", "{\"id\":\"evento-1\"}")
        );
    }
}
