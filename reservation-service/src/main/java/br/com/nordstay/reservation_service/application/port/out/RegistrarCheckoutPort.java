package br.com.nordstay.reservation_service.application.port.out;

import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.domain.model.Reserva;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RegistrarCheckoutPort {

    Optional<CheckoutAceito> buscarResposta(String reservaId, UUID idempotencyKey);

    /**
     * Atualiza a reserva, guarda a resposta idempotente por 24 horas e adiciona
     * o evento à outbox numa única operação atômica.
     */
    CheckoutAceito registrar(
            Reserva reserva,
            UUID idempotencyKey,
            CheckoutAceito resposta,
            OffsetDateTime checkoutEm);
}
