package br.com.nordstay.reservation_service.domain.service;

import br.com.nordstay.reservation_service.domain.exception.StatusCheckoutInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoliticaCheckoutTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final LocalDate DATA_ENTRADA = LocalDate.of(2026, 11, 20);
    private final PoliticaCheckout politica = new PoliticaCheckout();

    @Test
    void aceitaSolicitacaoQuandoReservaEstaCheckedIn() {
        assertDoesNotThrow(() ->
                politica.validarElegibilidade(criarReserva(StatusReserva.CHECKED_IN)));
    }

    @Test
    void rejeitaSolicitacaoQuandoReservaNaoEstaCheckedIn() {
        for (StatusReserva status : StatusReserva.values()) {
            if (status != StatusReserva.CHECKED_IN) {
                assertThrows(
                        StatusCheckoutInvalidoException.class,
                        () -> politica.validarElegibilidade(criarReserva(status)),
                        "Status rejeitado: " + status
                );
            }
        }
    }

    private static Reserva criarReserva(StatusReserva status) {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                status,
                new Hospede("Hóspede de teste", new Documento("***.***.*91-32", "9132")),
                DATA_ENTRADA,
                DATA_ENTRADA.plusDays(3),
                186000
        );
    }
}
