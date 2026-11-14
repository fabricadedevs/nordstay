package br.com.nordstay.reservation_service.application.port.out;

import br.com.nordstay.reservation_service.domain.model.Reserva;

import java.util.Optional;

public interface ReservaRepositoryPort {
    Optional<Reserva> buscarPorId(String reservaId);
}