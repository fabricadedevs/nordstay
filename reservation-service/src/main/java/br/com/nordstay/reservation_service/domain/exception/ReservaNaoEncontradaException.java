package br.com.nordstay.reservation_service.domain.exception;

public class ReservaNaoEncontradaException extends RuntimeException {
    public ReservaNaoEncontradaException() {
        super("Reserva não encontrada");
    }
}