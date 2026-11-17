package br.com.nordstay.reservation_service.application.port.in;

import java.util.UUID;

public record CheckinAceito(
        String reservaId,
        String quartoId,
        String status,
        UUID correlationId,
        String websocket) {
}