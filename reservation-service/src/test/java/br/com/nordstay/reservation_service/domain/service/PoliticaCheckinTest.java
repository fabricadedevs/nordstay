package br.com.nordstay.reservation_service.domain.service;

import br.com.nordstay.reservation_service.domain.exception.CheckinForaDaDataException;
import br.com.nordstay.reservation_service.domain.exception.CheckinRequisicaoInvalidaException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckinInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoliticaCheckinTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final LocalDate DATA_ENTRADA = LocalDate.of(2026, 11, 20);
    private final PoliticaCheckin politica = new PoliticaCheckin();

    @Test
    void aceitaRequisicaoValidaEValoresLimiteDeAcompanhantes() {
        Reserva reserva = criarReserva(StatusReserva.CONFIRMADA, DATA_ENTRADA);

        assertDoesNotThrow(() ->
                politica.validarRequisicao(reserva, RESERVA_ID, "9132", 0, true));
        assertDoesNotThrow(() ->
                politica.validarRequisicao(reserva, RESERVA_ID, "9132", 6, true));
        assertDoesNotThrow(() -> politica.validarElegibilidade(reserva, DATA_ENTRADA));
    }

    @Test
    void rejeitaDocumentoComFormatoInvalidoComoRequisicaoInvalida() {
        assertThrows(
                CheckinRequisicaoInvalidaException.class,
                () -> politica.validarRequisicao(
                        criarReserva(StatusReserva.CONFIRMADA, DATA_ENTRADA),
                        RESERVA_ID,
                        "91a2",
                        1,
                        true
                )
        );
    }

    @Test
    void rejeitaDocumentoFinalDivergenteSemRevelarSeReservaExiste() {
        assertThrows(
                ReservaNaoEncontradaException.class,
                () -> politica.validarRequisicao(
                        criarReserva(StatusReserva.CONFIRMADA, DATA_ENTRADA),
                        RESERVA_ID,
                        "0000",
                        1,
                        true
                )
        );
    }

    @Test
    void rejeitaAceiteDeTermosFalsoOuAusente() {
        Reserva reserva = criarReserva(StatusReserva.CONFIRMADA, DATA_ENTRADA);

        assertThrows(
                CheckinRequisicaoInvalidaException.class,
                () -> politica.validarRequisicao(reserva, RESERVA_ID, "9132", 1, false)
        );
        assertThrows(
                CheckinRequisicaoInvalidaException.class,
                () -> politica.validarRequisicao(reserva, RESERVA_ID, "9132", 1, null)
        );
    }

    @Test
    void rejeitaAcompanhantesForaDoIntervaloPermitido() {
        Reserva reserva = criarReserva(StatusReserva.CONFIRMADA, DATA_ENTRADA);

        assertThrows(
                CheckinRequisicaoInvalidaException.class,
                () -> politica.validarRequisicao(reserva, RESERVA_ID, "9132", -1, true)
        );
        assertThrows(
                CheckinRequisicaoInvalidaException.class,
                () -> politica.validarRequisicao(reserva, RESERVA_ID, "9132", 7, true)
        );
        assertThrows(
                CheckinRequisicaoInvalidaException.class,
                () -> politica.validarRequisicao(reserva, RESERVA_ID, "9132", null, true)
        );
    }

    @Test
    void rejeitaReservaComStatusDiferenteDeConfirmada() {
        assertThrows(
                StatusCheckinInvalidoException.class,
                () -> politica.validarElegibilidade(
                        criarReserva(StatusReserva.CHECKED_IN, DATA_ENTRADA),
                        DATA_ENTRADA
                )
        );
    }

    @Test
    void rejeitaCheckinEmDataDiferenteDaEntrada() {
        assertThrows(
                CheckinForaDaDataException.class,
                () -> politica.validarElegibilidade(
                        criarReserva(StatusReserva.CONFIRMADA, DATA_ENTRADA),
                        DATA_ENTRADA.minusDays(1)
                )
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
