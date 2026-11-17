package br.com.nordstay.reservation_service.domain.service;

import br.com.nordstay.reservation_service.domain.exception.CheckinForaDaDataException;
import br.com.nordstay.reservation_service.domain.exception.CheckinRequisicaoInvalidaException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckinInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;

public class PoliticaCheckin {

    public void validarRequisicao(
            Reserva reserva,
            String reservaId,
            String documentoFinal,
            Integer acompanhantes,
            Boolean aceiteTermos) {
        if (reserva == null) {
            throw new CheckinRequisicaoInvalidaException("Reserva é obrigatória");
        }
        if (reservaId == null || reservaId.isBlank() || !reserva.reservaId().equals(reservaId)) {
            throw new CheckinRequisicaoInvalidaException(
                    "O código informado não corresponde à reserva"
            );
        }
        if (documentoFinal == null || !documentoFinal.matches("\\d{4}")) {
            throw new CheckinRequisicaoInvalidaException(
                    "Documento final deve conter exatamente 4 dígitos"
            );
        }
        if (acompanhantes == null || acompanhantes < 0 || acompanhantes > 6) {
            throw new CheckinRequisicaoInvalidaException(
                    "A quantidade de acompanhantes deve estar entre 0 e 6"
            );
        }
        if (!Boolean.TRUE.equals(aceiteTermos)) {
            throw new CheckinRequisicaoInvalidaException(
                    "É necessário aceitar os termos para realizar o check-in"
            );
        }

        validarDocumento(reserva, documentoFinal);
    }

    public void validarElegibilidade(Reserva reserva, LocalDate hoje) {
        if (reserva == null || hoje == null) {
            throw new CheckinRequisicaoInvalidaException(
                    "Reserva e data atual são obrigatórias"
            );
        }
        if (reserva.status() != StatusReserva.CONFIRMADA) {
            throw new StatusCheckinInvalidoException(reserva.reservaId(), reserva.status());
        }

        if (!reserva.entrada().equals(hoje)) {
            throw new CheckinForaDaDataException(reserva.reservaId(), reserva.entrada());
        }
    }

    private void validarDocumento(Reserva reserva, String documentoFinal) {
        byte[] esperado = reserva.hospede()
                .documento()
                .ultimosQuatro()
                .getBytes(StandardCharsets.UTF_8);
        byte[] informado = documentoFinal.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(esperado, informado)) {
            throw new ReservaNaoEncontradaException();
        }
    }
}
