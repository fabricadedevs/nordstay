package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.out.MarcarCheckoutConcluidoPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ConcluirCheckoutServiceTest {

    private final MarcarCheckoutConcluidoPort marcarCheckoutConcluido =
            mock(MarcarCheckoutConcluidoPort.class);
    private final ConcluirCheckoutService service =
            new ConcluirCheckoutService(marcarCheckoutConcluido);

    @Test
    void encaminhaIdentificadorParaTransicaoDeEstado() {
        service.concluir("RSV-20261120-0042");

        verify(marcarCheckoutConcluido).marcarComoConcluido("RSV-20261120-0042");
    }

    @Test
    void rejeitaIdentificadorAusente() {
        assertThrows(IllegalArgumentException.class, () -> service.concluir(null));
        assertThrows(IllegalArgumentException.class, () -> service.concluir(" "));

        verifyNoInteractions(marcarCheckoutConcluido);
    }
}
