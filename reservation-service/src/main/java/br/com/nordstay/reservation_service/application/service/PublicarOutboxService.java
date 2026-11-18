package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.in.PublicarOutboxUseCase;
import br.com.nordstay.reservation_service.application.port.out.EventoPendente;
import br.com.nordstay.reservation_service.application.port.out.OutboxPort;
import br.com.nordstay.reservation_service.application.port.out.PublicarEventoPort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PublicarOutboxService implements PublicarOutboxUseCase {

    private final OutboxPort outboxPort;
    private final PublicarEventoPort publicarEventoPort;

    public PublicarOutboxService(OutboxPort outboxPort, PublicarEventoPort publicarEventoPort) {
        this.outboxPort = outboxPort;
        this.publicarEventoPort = publicarEventoPort;
    }

    @Override
    public boolean publicarProximo() {
        String token = UUID.randomUUID().toString();
        Optional<EventoPendente> pendente = outboxPort.reservarProximo(token);
        if (pendente.isEmpty()) {
            return false;
        }

        EventoPendente evento = pendente.orElseThrow();
        try {
            publicarEventoPort.publicar(evento.chave(), evento.payload());
            if (!outboxPort.confirmarPublicacao(token, evento)) {
                throw new IllegalStateException(
                        "A reserva do evento na outbox expirou antes da confirmação"
                );
            }
            return true;
        } catch (RuntimeException exception) {
            try {
                outboxPort.liberar(token);
            } catch (RuntimeException liberacaoException) {
                exception.addSuppressed(liberacaoException);
            }
            throw exception;
        }
    }
}
