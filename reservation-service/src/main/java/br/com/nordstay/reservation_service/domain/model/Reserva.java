package br.com.nordstay.reservation_service.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record Reserva(
        String reservaId,
        String quartoId,
        StatusReserva status,
        Hospede hospede,
        LocalDate entrada,
        LocalDate saida,
        long diariasCentavos) {

    public Reserva {
        if (reservaId == null || reservaId.isBlank()) {
            throw new IllegalArgumentException("Código da reserva é obrigatório");
        }
        if (quartoId == null || quartoId.isBlank()) {
            throw new IllegalArgumentException("Código do quarto é obrigatório");
        }

        Objects.requireNonNull(status, "Status é obrigatório");
        Objects.requireNonNull(hospede, "Hóspede é obrigatório");
        Objects.requireNonNull(entrada, "Data de entrada é obrigatória");
        Objects.requireNonNull(saida, "Data de saída é obrigatória");

        if (!saida.isAfter(entrada)) {
            throw new IllegalArgumentException("Saída deve ser posterior à entrada");
        }
        if (diariasCentavos < 0) {
            throw new IllegalArgumentException("Valor das diárias não pode ser negativo");
        }
    }
}