package br.com.nordstay.reservation_service.external.messaging;

import br.com.nordstay.reservation_service.application.port.in.ConcluirCheckoutUseCase;
import br.com.nordstay.reservation_service.domain.exception.EventoFaturaFechadaInvalidoException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FaturaFechadaListenerTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";

    private final ConcluirCheckoutUseCase concluirCheckout = mock(ConcluirCheckoutUseCase.class);
    private final FaturaFechadaListener listener =
            new FaturaFechadaListener(JsonMapper.builder().build(), concluirCheckout);

    @Test
    void encaminhaReservaDeEventoValido() {
        listener.receber(eventoValido());

        verify(concluirCheckout).concluir(RESERVA_ID);
    }

    @Test
    void rejeitaJsonMalformadoSemInvocarCasoDeUso() {
        assertThrows(EventoFaturaFechadaInvalidoException.class, () -> listener.receber("{"));

        verifyNoInteractions(concluirCheckout);
    }

    @Test
    void rejeitaTipoVersaoOuReservaInvalida() {
        assertThrows(
                EventoFaturaFechadaInvalidoException.class,
                () -> listener.receber(eventoValido().replace(
                        "nordstay.fatura-fechada",
                        "nordstay.checkout-solicitado"
                ))
        );
        assertThrows(
                EventoFaturaFechadaInvalidoException.class,
                () -> listener.receber(eventoValido().replace("\"versao\": 1", "\"versao\": 2"))
        );
        assertThrows(
                EventoFaturaFechadaInvalidoException.class,
                () -> listener.receber(eventoValido().replace(
                        "\"reservaId\": \"" + RESERVA_ID + "\"",
                        "\"reservaId\": \" \""
                ))
        );

        verifyNoInteractions(concluirCheckout);
    }

    private static String eventoValido() {
        return """
                {
                  "id": "2e9d7c41-5b8a-4f06-a3d2-7c1e9b4f6a05",
                  "tipo": "nordstay.fatura-fechada",
                  "versao": 1,
                  "ocorridoEm": "2026-11-23T13:45:00Z",
                  "origem": "billing-service",
                  "correlacaoId": "%s",
                  "quartoId": "Q-214",
                  "dados": {
                    "faturaId": "fat-2026-000311",
                    "reservaId": "%s",
                    "total": { "centavos": 187600, "moeda": "BRL" },
                    "status": "PENDENTE",
                    "fechadaEm": "2026-11-23T13:45:00Z"
                  }
                }
                """.formatted(RESERVA_ID, RESERVA_ID);
    }
}
