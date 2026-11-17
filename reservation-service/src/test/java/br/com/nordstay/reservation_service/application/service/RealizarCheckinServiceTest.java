package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinCommand;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckinPort;
import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.exception.CheckinForaDaDataException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import br.com.nordstay.reservation_service.domain.service.PoliticaCheckin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealizarCheckinServiceTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("6c0d6a5e-3f2b-4b1a-9e8c-2d7f4a1b9c30");
    private static final Instant AGORA_UTC = Instant.parse("2026-11-20T02:30:00Z");
    private static final ZoneId FUSO_RECIFE = ZoneId.of("America/Recife");

    @Mock
    private ReservaRepositoryPort reservaRepository;

    @Mock
    private RegistrarCheckinPort registrarCheckin;

    private RealizarCheckinService service;

    @BeforeEach
    void configurarServico() {
        service = new RealizarCheckinService(
                reservaRepository,
                registrarCheckin,
                new PoliticaCheckin(),
                Clock.fixed(AGORA_UTC, ZoneId.of("UTC"))
        );
    }

    @Test
    void registraCheckinUsandoDataEHorarioDeRecife() {
        Reserva reserva = criarReserva(StatusReserva.CONFIRMADA, LocalDate.of(2026, 11, 19));
        RealizarCheckinCommand command = comando("9132");
        when(reservaRepository.buscarPorId(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(registrarCheckin.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(registrarCheckin.registrar(
                eq(reserva),
                eq(IDEMPOTENCY_KEY),
                any(CheckinAceito.class),
                any(OffsetDateTime.class),
                eq(1),
                eq(LocalTime.of(15, 0))
        )).thenAnswer(invocacao -> invocacao.getArgument(2));

        CheckinAceito resposta = service.realizar(command);

        assertEquals(RESERVA_ID, resposta.reservaId());
        assertEquals("Q-214", resposta.quartoId());
        assertEquals("CHECKIN_EM_PROCESSAMENTO", resposta.status());
        assertEquals("/ws/estadias/" + RESERVA_ID, resposta.websocket());

        ArgumentCaptor<OffsetDateTime> horarioCaptor =
                ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(registrarCheckin).registrar(
                eq(reserva),
                eq(IDEMPOTENCY_KEY),
                any(CheckinAceito.class),
                horarioCaptor.capture(),
                eq(1),
                eq(LocalTime.of(15, 0))
        );
        assertEquals(
                OffsetDateTime.of(2026, 11, 19, 23, 30, 0, 0, FUSO_RECIFE.getRules()
                        .getOffset(Instant.parse("2026-11-20T02:30:00Z"))),
                horarioCaptor.getValue()
        );
    }

    @Test
    void devolveRespostaIdempotenteDepoisDeValidarDocumentoMesmoComReservaJaCheckin() {
        Reserva reserva = criarReserva(StatusReserva.CHECKED_IN, LocalDate.of(2026, 11, 19));
        RealizarCheckinCommand command = comando("9132");
        CheckinAceito respostaAnterior = new CheckinAceito(
                RESERVA_ID,
                "Q-214",
                "CHECKIN_EM_PROCESSAMENTO",
                UUID.randomUUID(),
                "/ws/estadias/" + RESERVA_ID
        );
        when(reservaRepository.buscarPorId(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(registrarCheckin.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(respostaAnterior));

        CheckinAceito resposta = service.realizar(command);

        assertSame(respostaAnterior, resposta);
        verify(registrarCheckin, never()).registrar(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void naoDevolveRespostaIdempotenteQuandoDocumentoNaoConfere() {
        Reserva reserva = criarReserva(StatusReserva.CHECKED_IN, LocalDate.of(2026, 11, 19));
        when(reservaRepository.buscarPorId(RESERVA_ID)).thenReturn(Optional.of(reserva));

        assertThrows(
                ReservaNaoEncontradaException.class,
                () -> service.realizar(comando("0000"))
        );

        verify(registrarCheckin, never()).buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY);
        verify(registrarCheckin, never()).registrar(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void rejeitaCheckinQuandoDataDeRecifeAindaEhDiaAnterior() {
        Reserva reserva = criarReserva(StatusReserva.CONFIRMADA, LocalDate.of(2026, 11, 20));
        when(reservaRepository.buscarPorId(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(registrarCheckin.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        assertThrows(
                CheckinForaDaDataException.class,
                () -> service.realizar(comando("9132"))
        );

        verify(registrarCheckin, never()).registrar(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private static RealizarCheckinCommand comando(String documentoFinal) {
        return new RealizarCheckinCommand(
                RESERVA_ID,
                IDEMPOTENCY_KEY,
                documentoFinal,
                1,
                true,
                LocalTime.of(15, 0)
        );
    }

    private static Reserva criarReserva(StatusReserva status, LocalDate entrada) {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                status,
                new Hospede("Hóspede de teste", new Documento("***.***.*91-32", "9132")),
                entrada,
                entrada.plusDays(3),
                186000
        );
    }
}
