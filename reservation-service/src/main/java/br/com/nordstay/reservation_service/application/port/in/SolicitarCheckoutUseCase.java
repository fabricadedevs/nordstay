package br.com.nordstay.reservation_service.application.port.in;

public interface SolicitarCheckoutUseCase {
    CheckoutAceito solicitar(SolicitarCheckoutCommand command);
}
