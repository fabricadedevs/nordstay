package br.com.nordstay.reservation_service.application.port.in;

import br.com.nordstay.reservation_service.domain.model.Reserva;

public interface ConsultarReservaUseCase {
    Reserva consultar(String reservaId, String documentoFinal);
}