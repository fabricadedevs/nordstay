package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;

import java.util.UUID;

public record CheckinAceitoResponse(
        String reservaId,
        String quartoId,
        String status,
        UUID correlationId,
        String websocket) {

    static CheckinAceitoResponse de(CheckinAceito resultado) {
        return new CheckinAceitoResponse(
                resultado.reservaId(),
                resultado.quartoId(),
                resultado.status(),
                resultado.correlationId(),
                resultado.websocket()
        );
    }
}
