package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.out.EventoPendente;
import br.com.nordstay.reservation_service.application.port.out.OutboxPort;
import br.com.nordstay.reservation_service.application.port.out.PublicarEventoPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicarOutboxServiceTest {

    @Test
    void publicaEConfirmaEvento() {
        EventoPendente evento = new EventoPendente("Q-214", "{\"id\":\"evento-1\"}");
        OutboxFake outbox = new OutboxFake(Optional.of(evento));
        PublicarEventoPort publicador = (chave, payload) -> {
            assertEquals("Q-214", chave);
            assertEquals(evento.payload(), payload);
        };

        assertTrue(new PublicarOutboxService(outbox, publicador).publicarProximo());
        assertEquals(1, outbox.confirmacoes);
        assertEquals(0, outbox.liberacoes);
    }

    @Test
    void naoPublicaQuandoOutboxEstaVazia() {
        OutboxFake outbox = new OutboxFake(Optional.empty());
        PublicarEventoPort publicador = (chave, payload) -> {
            throw new AssertionError("Não deveria publicar sem evento");
        };

        assertFalse(new PublicarOutboxService(outbox, publicador).publicarProximo());
        assertEquals(0, outbox.confirmacoes);
        assertEquals(0, outbox.liberacoes);
    }

    @Test
    void liberaEventoQuandoPublicacaoFalha() {
        OutboxFake outbox = new OutboxFake(
                Optional.of(new EventoPendente("Q-214", "{\"id\":\"evento-1\"}"))
        );
        PublicarEventoPort publicador = (chave, payload) -> {
            throw new IllegalStateException("Kafka indisponível");
        };

        assertThrows(
                IllegalStateException.class,
                () -> new PublicarOutboxService(outbox, publicador).publicarProximo()
        );
        assertEquals(0, outbox.confirmacoes);
        assertEquals(1, outbox.liberacoes);
    }

    @Test
    void liberaEventoQuandoConfirmacaoDaOutboxFalha() {
        OutboxFake outbox = new OutboxFake(
                Optional.of(new EventoPendente("Q-214", "{\"id\":\"evento-1\"}"))
        );
        outbox.confirmar = false;
        PublicarEventoPort publicador = (chave, payload) -> {
        };

        assertThrows(
                IllegalStateException.class,
                () -> new PublicarOutboxService(outbox, publicador).publicarProximo()
        );
        assertEquals(1, outbox.liberacoes);
    }

    private static final class OutboxFake implements OutboxPort {

        private final Optional<EventoPendente> proximo;
        private boolean confirmar = true;
        private int confirmacoes;
        private int liberacoes;

        private OutboxFake(Optional<EventoPendente> proximo) {
            this.proximo = proximo;
        }

        @Override
        public Optional<EventoPendente> reservarProximo(String token) {
            return proximo;
        }

        @Override
        public boolean confirmarPublicacao(String token, EventoPendente evento) {
            confirmacoes++;
            return confirmar;
        }

        @Override
        public void liberar(String token) {
            liberacoes++;
        }
    }
}
