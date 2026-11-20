package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.in.ConcluirCheckoutUseCase;
import br.com.nordstay.reservation_service.application.port.out.MarcarCheckoutConcluidoPort;
import org.springframework.stereotype.Service;

@Service
public class ConcluirCheckoutService implements ConcluirCheckoutUseCase {

    private final MarcarCheckoutConcluidoPort marcarCheckoutConcluido;

    public ConcluirCheckoutService(MarcarCheckoutConcluidoPort marcarCheckoutConcluido) {
        this.marcarCheckoutConcluido = marcarCheckoutConcluido;
    }

    @Override
    public void concluir(String reservaId) {
        if (reservaId == null || reservaId.isBlank()) {
            throw new IllegalArgumentException("Código da reserva é obrigatório no evento");
        }
        marcarCheckoutConcluido.marcarComoConcluido(reservaId);
    }
}
