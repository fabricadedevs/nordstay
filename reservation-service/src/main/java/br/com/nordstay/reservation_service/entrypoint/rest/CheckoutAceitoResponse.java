package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;

import java.util.UUID;

public record CheckoutAceitoResponse(String reservaId, String status, UUID correlationId) {

    static CheckoutAceitoResponse de(CheckoutAceito resultado) {
        return new CheckoutAceitoResponse(
                resultado.reservaId(),
                resultado.status(),
                resultado.correlationId()
        );
    }
}
