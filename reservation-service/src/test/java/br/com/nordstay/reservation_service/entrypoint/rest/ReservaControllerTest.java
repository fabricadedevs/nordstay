package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(ReservaController.class)
class ReservaControllerTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String DOCUMENTO_COMPLETO = "12345679132";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarReservaUseCase consultarReserva;

    @Test
    void retornaReservaComDocumentoMascarado() throws Exception {
        when(consultarReserva.consultar(RESERVA_ID, "9132"))
                .thenReturn(criarReserva());

        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID)
                        .header("X-Documento-Final", "9132"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(RESERVA_ID))
                .andExpect(jsonPath("$.quartoId").value("Q-214"))
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.hospede.nome").value("Hóspede de teste"))
                .andExpect(jsonPath("$.hospede.documentoMascarado")
                        .value("***.***.*91-32"))
                .andExpect(jsonPath("$.entrada").value("2026-11-20"))
                .andExpect(jsonPath("$.saida").value("2026-11-23"))
                .andExpect(jsonPath("$.diariasCentavos").value(186000))
                .andExpect(content().string(not(containsString(DOCUMENTO_COMPLETO))));

        verify(consultarReserva).consultar(RESERVA_ID, "9132");
    }

    @Test
    void retorna400QuandoCodigoDaReservaEstaMalformado() throws Exception {
        mockMvc.perform(get("/api/reservas/{reservaId}", "codigo-invalido")
                        .header("X-Documento-Final", "9132"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultarReserva);
    }

    @Test
    void retorna400QuandoHeaderNaoTemQuatroDigitos() throws Exception {
        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID)
                        .header("X-Documento-Final", "91a2"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultarReserva);
    }

    @Test
    void retorna400QuandoHeaderEstaAusente() throws Exception {
        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultarReserva);
    }

    @Test
    void retorna404QuandoCasoDeUsoNaoEncontraReserva() throws Exception {
        when(consultarReserva.consultar(RESERVA_ID, "9132"))
                .thenThrow(new ReservaNaoEncontradaException());

        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID)
                        .header("X-Documento-Final", "9132"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Reserva não encontrada"));
    }

    private static Reserva criarReserva() {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                StatusReserva.CONFIRMADA,
                new Hospede("Hóspede de teste", new Documento(DOCUMENTO_COMPLETO)),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );
    }
}