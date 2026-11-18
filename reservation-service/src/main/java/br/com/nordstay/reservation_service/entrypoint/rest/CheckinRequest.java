package br.com.nordstay.reservation_service.entrypoint.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CheckinRequest(
        @NotNull
        @Min(0)
        @Max(6)
        Integer acompanhantes,
        @NotNull
        @AssertTrue(message = "Os termos devem ser aceitos")
        Boolean aceiteTermos,
        @JsonFormat(pattern = "HH:mm")
        LocalTime previsaoChegada) {
}
