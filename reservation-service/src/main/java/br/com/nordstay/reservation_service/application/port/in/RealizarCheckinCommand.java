package br.com.nordstay.reservation_service.application.port.in;

import java.time.LocalTime;
import java.util.UUID;

public record RealizarCheckinCommand(
        String reservaId,
        UUID idempotencyKey,
        String documentoFinal,
        Integer acompanhantes,
        Boolean aceiteTermos,
        LocalTime previsaoChegada) {
}