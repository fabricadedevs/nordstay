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
        String evento = "{\"tipo\":\"nordstay.checkin-realizado\",\"id\":\"evento-1\"}";
        when(kafkaTemplate.send("nordstay.checkin-realizado", "Q-214", evento))
                .thenReturn(CompletableFuture.completedFuture(null));
        KafkaPublicarEventoAdapter adapter =
                new KafkaPublicarEventoAdapter(
                        kafkaTemplate,
                        "nordstay.checkin-realizado",
                        "nordstay.checkout-solicitado",
                        1000
                );

        adapter.publicar("Q-214", evento);

        verify(kafkaTemplate).send("nordstay.checkin-realizado", "Q-214", evento);
    }

    @Test
    void publicaCheckoutNoTopicoEspecifico() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        String evento = "{\"tipo\":\"CHECKOUT_SOLICITADO\",\"reservaId\":\"RSV-1\"}";
        when(kafkaTemplate.send("nordstay.checkout-solicitado", "Q-214", evento))
                .thenReturn(CompletableFuture.completedFuture(null));
        KafkaPublicarEventoAdapter adapter =
                new KafkaPublicarEventoAdapter(
                        kafkaTemplate,
                        "nordstay.checkin-realizado",
                        "nordstay.checkout-solicitado",
                        1000
                );

        adapter.publicar("Q-214", evento);

        verify(kafkaTemplate).send("nordstay.checkout-solicitado", "Q-214", evento);
    }

    @Test
    void rejeitaEventoSemTipoOuSemTopicoConfigurado() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaPublicarEventoAdapter adapter =
                new KafkaPublicarEventoAdapter(
                        kafkaTemplate,
                        "nordstay.checkin-realizado",
                        "nordstay.checkout-solicitado",
                        1000
                );

        assertThrows(IllegalArgumentException.class, () -> adapter.publicar("Q-214", "{}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.publicar("Q-214", "{\"tipo\":\"EVENTO_DESCONHECIDO\"}")
        );
    }

    @Test
    void propagaFalhaDeConfirmacaoDoBroker() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> envio = new CompletableFuture<>();
        envio.completeExceptionally(new IllegalStateException("Broker indisponível"));
        String evento = "{\"tipo\":\"nordstay.checkin-realizado\",\"id\":\"evento-1\"}";
        when(kafkaTemplate.send("nordstay.checkin-realizado", "Q-214", evento))
                .thenReturn(envio);
        KafkaPublicarEventoAdapter adapter =
                new KafkaPublicarEventoAdapter(
                        kafkaTemplate,
                        "nordstay.checkin-realizado",
                        "nordstay.checkout-solicitado",
                        1000
                );

        assertThrows(
                IllegalStateException.class,
                () -> adapter.publicar("Q-214", evento)
        );
    }
}
