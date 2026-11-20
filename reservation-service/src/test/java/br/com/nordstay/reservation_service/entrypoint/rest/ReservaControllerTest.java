package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinCommand;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinUseCase;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutUseCase;
import br.com.nordstay.reservation_service.application.port.out.ConsultaRateLimitPort;
import br.com.nordstay.reservation_service.domain.exception.CheckinForaDaDataException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckinInvalidoException;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import br.com.nordstay.reservation_service.security.SecurityConfiguracao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
@Import(SecurityConfiguracao.class)
class ReservaControllerTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String DOCUMENTO_COMPLETO = "12345679132";
    private static final String IP_CLIENTE = "203.0.113.7";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("6c0d6a5e-3f2b-4b1a-9e8c-2d7f4a1b9c30");
    private static final UUID CORRELATION_ID =
            UUID.fromString("c7a1e2d4-91b3-4f6a-8d0e-5f2b7c9a1e33");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarReservaUseCase consultarReserva;

    @MockitoBean
    private ConsultaRateLimitPort consultaRateLimit;

    @MockitoBean
    private RealizarCheckinUseCase realizarCheckin;

    @MockitoBean
    private SolicitarCheckoutUseCase solicitarCheckout;

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

    @Test
    void retorna202ComLocationEContratoDeCheckinAceito() throws Exception {
        when(realizarCheckin.realizar(comandoEsperado()))
                .thenReturn(respostaAceita());

        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content("""
                                {"acompanhantes":1,"aceiteTermos":true,"previsaoChegada":"15:00"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/ws/estadias/" + RESERVA_ID))
                .andExpect(jsonPath("$.reservaId").value(RESERVA_ID))
                .andExpect(jsonPath("$.quartoId").value("Q-214"))
                .andExpect(jsonPath("$.status").value("CHECKIN_EM_PROCESSAMENTO"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.websocket").value("/ws/estadias/" + RESERVA_ID));

        verify(realizarCheckin).realizar(comandoEsperado());
    }

    @Test
    void retorna400QuandoCorpoDoCheckinEstaInvalido() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content("""
                                {"acompanhantes":7,"aceiteTermos":true}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(realizarCheckin);
    }

    @Test
    void retorna400QuandoTermosNaoSaoAceitos() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content("""
                                {"acompanhantes":1,"aceiteTermos":false}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(realizarCheckin);
    }

    @Test
    void retorna400QuandoTermosNaoSaoInformados() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content("""
                                {"acompanhantes":1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(realizarCheckin);
    }

    @Test
    void retorna400QuandoDocumentoFinalNaoTemQuatroDigitos() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "91a2")
                        .contentType("application/json")
                        .content(corpoValido()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(realizarCheckin);
    }

    @Test
    void retorna400QuandoIdempotencyKeyNaoEhUuid() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", "chave-invalida")
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content("""
                                {"acompanhantes":1,"aceiteTermos":true}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(realizarCheckin);
    }

    @Test
    void retorna404QuandoReservaNaoExiste() throws Exception {
        when(realizarCheckin.realizar(comandoEsperado()))
                .thenThrow(new ReservaNaoEncontradaException());

        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content(corpoValido()))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna409ComCorrelationIdQuandoStatusNaoPermiteCheckin() throws Exception {
        when(realizarCheckin.realizar(comandoEsperado()))
                .thenThrow(new StatusCheckinInvalidoException(RESERVA_ID, StatusReserva.CHECKED_IN));

        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content(corpoValido()))
                .andExpect(status().isConflict())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.title").value("Check-in não permitido"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void retorna422ComCorrelationIdQuandoCheckinEstaForaDaData() throws Exception {
        when(realizarCheckin.realizar(comandoEsperado()))
                .thenThrow(new CheckinForaDaDataException(RESERVA_ID, LocalDate.of(2026, 11, 20)));

        mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Documento-Final", "9132")
                        .contentType("application/json")
                        .content(corpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.title").value("Check-in fora da data"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void repeticaoIdempotenteRetornaARespostaAceitaOriginal() throws Exception {
        when(realizarCheckin.realizar(comandoEsperado()))
                .thenReturn(respostaAceita());

        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mockMvc.perform(post("/api/reservas/{reservaId}/checkin", RESERVA_ID)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .header("X-Documento-Final", "9132")
                            .contentType("application/json")
                            .content(corpoValido()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()))
                    .andExpect(jsonPath("$.status").value("CHECKIN_EM_PROCESSAMENTO"));
        }

        verify(realizarCheckin, org.mockito.Mockito.times(2)).realizar(comandoEsperado());
    }

    private static RealizarCheckinCommand comandoEsperado() {
        return new RealizarCheckinCommand(
                RESERVA_ID,
                IDEMPOTENCY_KEY,
                "9132",
                1,
                true,
                LocalTime.of(15, 0)
        );
    }

    private static CheckinAceito respostaAceita() {
        return new CheckinAceito(
                RESERVA_ID,
                "Q-214",
                "CHECKIN_EM_PROCESSAMENTO",
                CORRELATION_ID,
                "/ws/estadias/" + RESERVA_ID
        );
    }

    private static String corpoValido() {
        return """
                {"acompanhantes":1,"aceiteTermos":true,"previsaoChegada":"15:00"}
                """;
    }

    private static Reserva criarReserva() {
        return new Reserva(
                RESERVA_ID,
                "Q-214",
                StatusReserva.CONFIRMADA,
                new Hospede("Hóspede de teste", new Documento("***.***.*91-32", "9132")),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );
    }
}