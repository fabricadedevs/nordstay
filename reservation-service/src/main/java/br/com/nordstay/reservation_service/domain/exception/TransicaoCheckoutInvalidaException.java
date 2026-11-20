package br.com.nordstay.reservation_service.domain.exception;

public class TransicaoCheckoutInvalidaException extends RuntimeException {

    public TransicaoCheckoutInvalidaException(String reservaId, String statusAtual) {
        super("A reserva %s não pode ser concluída com status %s; status esperado: CHECKOUT_SOLICITADO"
                .formatted(reservaId, statusAtual));
    }
}
