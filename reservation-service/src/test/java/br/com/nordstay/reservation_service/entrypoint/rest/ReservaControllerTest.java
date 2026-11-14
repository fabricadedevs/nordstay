package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.application.port.out.ConsultaRateLimitPort;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
class ReservaControllerTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String DOCUMENTO_COMPLETO = "12345679132";
    private static final String IP_CLIENTE = "203.0.113.7";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarReservaUseCase consultarReserva;

    @MockitoBean
    private ConsultaRateLimitPort consultaRateLimit;

    @BeforeEach
    void permitirConsultasPorPadrao() {
        when(consultaRateLimit.registrarConsulta(anyString())).thenReturn(true);
    }

    @Test
    void retornaReservaComDocumentoMascarado() throws Exception {
        when(consultarReserva.consultar(RESERVA_ID, "9132"))
                .thenReturn(criarReserva());

        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID)
                        .header("X-Documento-Final", "9132")
                        .with(request -> {
                            request.setRemoteAddr(IP_CLIENTE);
                            return request;
                        }))
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
        verify(consultaRateLimit).registrarConsulta(IP_CLIENTE);
    }

    @Test
    void retorna400QuandoCodigoDaReservaEstaMalformado() throws Exception {
        mockMvc.perform(get("/api/reservas/{reservaId}", "codigo-invalido")
                        .header("X-Documento-Final", "9132"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultarReserva);
        verifyNoInteractions(consultaRateLimit);
    }

    @Test
    void retorna400QuandoHeaderNaoTemQuatroDigitos() throws Exception {
        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID)
                        .header("X-Documento-Final", "91a2"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultarReserva);
        verifyNoInteractions(consultaRateLimit);
    }

    @Test
    void retorna400QuandoHeaderEstaAusente() throws Exception {
        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(consultarReserva);
        verifyNoInteractions(consultaRateLimit);
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

    @Test
    void retorna429QuandoLimiteDeConsultasFoiExcedido() throws Exception {
        when(consultaRateLimit.registrarConsulta(IP_CLIENTE)).thenReturn(false);

        mockMvc.perform(get("/api/reservas/{reservaId}", RESERVA_ID)
                        .header("X-Documento-Final", "9132")
                        .with(request -> {
                            request.setRemoteAddr(IP_CLIENTE);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem").value("Limite de consultas excedido"));

        verify(consultaRateLimit).registrarConsulta(IP_CLIENTE);
        verifyNoInteractions(consultarReserva);
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