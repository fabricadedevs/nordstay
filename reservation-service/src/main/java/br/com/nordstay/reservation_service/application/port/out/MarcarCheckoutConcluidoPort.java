package br.com.nordstay.reservation_service.application.port.out;

public interface MarcarCheckoutConcluidoPort {
    void marcarComoConcluido(String reservaId);
}
