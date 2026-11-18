package br.com.nordstay.reservation_service.domain.exception;

public class CheckinRequisicaoInvalidaException extends RuntimeException {
    public CheckinRequisicaoInvalidaException(String mensagem) {
        super("A requisição de check-in é inválida para a reserva atual.");
    }
}