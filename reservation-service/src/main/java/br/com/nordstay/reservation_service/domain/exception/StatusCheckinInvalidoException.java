package br.com.nordstay.reservation_service.domain.exception;

import br.com.nordstay.reservation_service.domain.model.StatusReserva;

public class StatusCheckinInvalidoException extends RuntimeException {
    public StatusCheckinInvalidoException(String reservaId, StatusReserva statusAtual) {
        super("A reserva %s não pode fazer check-in com status %s; status esperado: CONFIRMADA"
                .formatted(reservaId, statusAtual));
    }
}
