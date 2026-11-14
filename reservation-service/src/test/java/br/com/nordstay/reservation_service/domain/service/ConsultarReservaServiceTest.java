package br.com.nordstay.reservation_service.domain.service;

import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.service.ConsultarReservaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsultarReservaServiceTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";

    private final RepositorioFake repositorio = new RepositorioFake();
    private final ConsultarReservaService service = new ConsultarReservaService(repositorio);
    private final Reserva reserva = criarReserva();

    @Test
    void retornaReservaQuandoDocumentoFinalConfere() {
        repositorio.reserva = Optional.of(reserva);

        Reserva resultado = service.consultar(RESERVA_ID, "9132");

        assertSame(reserva, resultado);
        assertEquals(RESERVA_ID, repositorio.ultimoIdConsultado);
    }

    @Test
    void lancaExcecaoQuandoDocumentoFinalNaoConfere() {
        repositorio.reserva = Optional.of(reserva);

        assertThrows(
                ReservaNaoEncontradaException.class,
                () -> service.consultar(RESERVA_ID, "0000")
        );
    }

    @Test
    void reservaInexistenteEDocumentoIncorretoUsamAMesmaMensagem() {
        repositorio.reserva = Optional.empty();
        ReservaNaoEncontradaException erroReservaInexistente = assertThrows(
                ReservaNaoEncontradaException.class,
                () -> service.consultar(RESERVA_ID, "9132")
        );

        repositorio.reserva = Optional.of(reserva);
        ReservaNaoEncontradaException erroDocumentoIncorreto = assertThrows(
                ReservaNaoEncontradaException.class,
                () -> service.consultar(RESERVA_ID, "0000")
        );

        assertEquals(erroReservaInexistente.getMessage(), erroDocumentoIncorreto.getMessage());
    }

    private static Reserva criarReserva() {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                StatusReserva.CONFIRMADA,
                new Hospede("Hóspede de teste", new Documento("12345679132")),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );
    }

    private static class RepositorioFake implements ReservaRepositoryPort {
        private Optional<Reserva> reserva = Optional.empty();
        private String ultimoIdConsultado;

        @Override
        public Optional<Reserva> buscarPorId(String reservaId) {
            ultimoIdConsultado = reservaId;
            return reserva;
        }
    }
}
