package br.com.nordstay.reservation_service.application.port.in;

import java.util.UUID;

public record CheckoutAceito(String reservaId, String status, UUID correlationId) {
}
