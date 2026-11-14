package br.com.nordstay.reservation_service.domain.service;

import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class ConsultarReservaService implements ConsultarReservaUseCase {

    private final ReservaRepositoryPort reservaRepository;

    public ConsultarReservaService(ReservaRepositoryPort reservaRepository) {
        this.reservaRepository = reservaRepository;
    }

    @Override
    public Reserva consultar(String reservaId, String documentoFinal) {
        Reserva reserva = reservaRepository.buscarPorId(reservaId)
                .orElseThrow(ReservaNaoEncontradaException::new);

        byte[] documentoArmazenado = reserva.hospede()
                .documento()
                .ultimosQuatro()
                .getBytes(StandardCharsets.UTF_8);
        byte[] documentoInformado = documentoFinal.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(documentoArmazenado, documentoInformado)) {
            throw new ReservaNaoEncontradaException();
        }

        return reserva;
    }
}