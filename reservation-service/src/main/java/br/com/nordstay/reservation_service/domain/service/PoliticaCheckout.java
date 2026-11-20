package br.com.nordstay.reservation_service.domain.service;

import br.com.nordstay.reservation_service.domain.exception.StatusCheckoutInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;

public class PoliticaCheckout {

    public void validarElegibilidade(Reserva reserva) {
        if (reserva == null) {
            throw new IllegalArgumentException("Reserva é obrigatória");
        }
        if (reserva.status() != StatusReserva.CHECKED_IN) {
            throw new StatusCheckoutInvalidoException(reserva.reservaId(), reserva.status());
        }
    }
}
