package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutCommand;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckoutPort;
import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckoutInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import br.com.nordstay.reservation_service.domain.service.PoliticaCheckout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
class SolicitarCheckoutServiceTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("0f8e3b6a-1d2c-4e5f-9a7b-6c4d2e1f0a93");
    private static final UUID CORRELATION_ID =
            UUID.fromString("e3b9a1c7-5d2f-4a86-b0c4-9f7e1d3a5b62");
    private static final Instant AGORA = Instant.parse("2026-11-23T13:41:37Z");

    @Mock
    private ReservaRepositoryPort reservaRepository;

    @Mock
    private RegistrarCheckoutPort registrarCheckout;

    private SolicitarCheckoutService service;

    @BeforeEach
    void configurarServico() {
        service = new SolicitarCheckoutService(
                reservaRepository,
                registrarCheckout,
                new PoliticaCheckout(),
                Clock.fixed(AGORA, ZoneId.of("America/Recife"))
        );
    }

    @Test
    void registraCheckoutEDevolveRespostaCriada() {
        Reserva reserva = criarReserva(StatusReserva.CHECKED_IN);
        SolicitarCheckoutCommand command = comando();
        CheckoutAceito resposta = new CheckoutAceito(
                RESERVA_ID,
                "CHECKOUT_SOLICITADO",
                CORRELATION_ID
        );
        when(reservaRepository.buscarPorId(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(registrarCheckout.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(registrarCheckout.registrar(
                eq(reserva),
                eq(IDEMPOTENCY_KEY),
                any(CheckoutAceito.class),
                any(OffsetDateTime.class)
        )).thenReturn(resposta);

        assertEquals(resposta, service.solicitar(command));

        ArgumentCaptor<OffsetDateTime> horarioCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(registrarCheckout).registrar(
                eq(reserva),
                eq(IDEMPOTENCY_KEY),
                any(CheckoutAceito.class),
                horarioCaptor.capture()
        );
        assertEquals(OffsetDateTime.parse("2026-11-23T10:41:37-03:00"), horarioCaptor.getValue());
    }

    @Test
    void retornaRespostaIdempotenteSemRevalidarStatusOuRegistrarNovoEvento() {
        Reserva reserva = criarReserva(StatusReserva.CHECKOUT_SOLICITADO);
        CheckoutAceito respostaExistente = new CheckoutAceito(
                RESERVA_ID,
                "CHECKOUT_SOLICITADO",
                CORRELATION_ID
        );
        when(reservaRepository.buscarPorId(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(registrarCheckout.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(respostaExistente));

        assertSame(respostaExistente, service.solicitar(comando()));
        verify(registrarCheckout, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void rejeitaStatusInvalidoAntesDeRegistrar() {
        when(reservaRepository.buscarPorId(RESERVA_ID))
                .thenReturn(Optional.of(criarReserva(StatusReserva.CHECKED_OUT)));
        when(registrarCheckout.buscarResposta(RESERVA_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        assertThrows(StatusCheckoutInvalidoException.class, () -> service.solicitar(comando()));
        verify(registrarCheckout, never()).registrar(any(), any(), any(), any());
    }

    private static SolicitarCheckoutCommand comando() {
        return new SolicitarCheckoutCommand(RESERVA_ID, IDEMPOTENCY_KEY);
    }

    private static Reserva criarReserva(StatusReserva status) {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                status,
                new Hospede("Hóspede de teste", new Documento("***.***.*91-32", "9132")),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );
    }
}
