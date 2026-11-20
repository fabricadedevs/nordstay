package br.com.nordstay.reservation_service.application.port.in;

import java.util.UUID;

public record SolicitarCheckoutCommand(String reservaId, UUID idempotencyKey) {
}
