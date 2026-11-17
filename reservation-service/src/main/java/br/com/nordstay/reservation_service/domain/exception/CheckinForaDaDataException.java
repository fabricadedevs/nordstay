package br.com.nordstay.reservation_service.domain.exception;

import java.time.LocalDate;

public class CheckinForaDaDataException extends RuntimeException {
    public CheckinForaDaDataException(String reservaId, LocalDate dataEntrada) {
        super("O check-in de %s só pode ser feito em %s".formatted(reservaId, dataEntrada));
    }
}
