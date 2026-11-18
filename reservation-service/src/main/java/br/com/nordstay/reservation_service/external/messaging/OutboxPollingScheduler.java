package br.com.nordstay.reservation_service.external.messaging;

import br.com.nordstay.reservation_service.application.port.in.PublicarOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reservation.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollingScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPollingScheduler.class);

    private final PublicarOutboxUseCase publicarOutboxUseCase;

    public OutboxPollingScheduler(PublicarOutboxUseCase publicarOutboxUseCase) {
        this.publicarOutboxUseCase = publicarOutboxUseCase;
    }

    @Scheduled(fixedDelayString = "${reservation.outbox.poll-interval-ms:1000}")
    public void publicarProximo() {
        try {
            publicarOutboxUseCase.publicarProximo();
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao publicar evento pendente da outbox; será tentado novamente", exception);
        }
    }
}
