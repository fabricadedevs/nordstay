package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinCommand;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinUseCase;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckinPort;
import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.exception.CheckinRequisicaoInvalidaException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.service.PoliticaCheckin;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class RealizarCheckinService implements RealizarCheckinUseCase {

    private static final ZoneId FUSO_CHECKIN = ZoneId.of("America/Recife");
    private static final String STATUS_RESPOSTA = "CHECKIN_EM_PROCESSAMENTO";

    private final ReservaRepositoryPort reservaRepository;
    private final RegistrarCheckinPort registrarCheckin;
    private final PoliticaCheckin politicaCheckin;
    private final Clock clock;

    public RealizarCheckinService(
            ReservaRepositoryPort reservaRepository,
            RegistrarCheckinPort registrarCheckin,
            PoliticaCheckin politicaCheckin,
            Clock clock) {
        this.reservaRepository = reservaRepository;
        this.registrarCheckin = registrarCheckin;
        this.politicaCheckin = politicaCheckin;
        this.clock = clock;
    }

    @Override
    public CheckinAceito realizar(RealizarCheckinCommand command) {
        validarChaveDeIdempotencia(command);

        Reserva reserva = reservaRepository.buscarPorId(command.reservaId())
                .orElseThrow(ReservaNaoEncontradaException::new);

        politicaCheckin.validarRequisicao(
                reserva,
                command.reservaId(),
                command.documentoFinal(),
                command.acompanhantes(),
                command.aceiteTermos()
        );

        var respostaExistente = registrarCheckin.buscarResposta(
                command.reservaId(),
                command.idempotencyKey()
        );
        if (respostaExistente.isPresent()) {
            return respostaExistente.get();
        }

        OffsetDateTime agora = OffsetDateTime.now(clock.withZone(FUSO_CHECKIN));
        LocalDate hoje = agora.toLocalDate();
        politicaCheckin.validarElegibilidade(reserva, hoje);

        UUID correlationId = UUID.randomUUID();
        CheckinAceito resposta = new CheckinAceito(
                reserva.reservaId(),
                reserva.quartoId(),
                STATUS_RESPOSTA,
                correlationId,
                "/ws/estadias/" + reserva.reservaId()
        );

        return registrarCheckin.registrar(
                reserva,
                command.idempotencyKey(),
                resposta,
                agora,
                command.acompanhantes(),
                command.previsaoChegada()
        );
    }

    private void validarChaveDeIdempotencia(RealizarCheckinCommand command) {
        if (command == null) {
            throw new CheckinRequisicaoInvalidaException("Comando de check-in é obrigatório");
        }
        if (command.reservaId() == null || command.reservaId().isBlank()) {
            throw new CheckinRequisicaoInvalidaException("Código da reserva é obrigatório");
        }
        if (command.idempotencyKey() == null) {
            throw new CheckinRequisicaoInvalidaException("Idempotency-Key é obrigatório");
        }
    }
}
