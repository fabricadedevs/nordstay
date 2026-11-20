package br.com.nordstay.reservation_service.domain.exception;

import br.com.nordstay.reservation_service.domain.model.StatusReserva;

public class StatusCheckoutInvalidoException extends RuntimeException {
    public StatusCheckoutInvalidoException(String reservaId, StatusReserva statusAtual) {
        super("A reserva %s não pode solicitar checkout com status %s; status esperado: CHECKED_IN"
                .formatted(reservaId, statusAtual));
    }
}
