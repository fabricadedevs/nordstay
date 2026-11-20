package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutCommand;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutUseCase;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinUseCase;
import br.com.nordstay.reservation_service.application.port.out.ConsultaRateLimitPort;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckoutInvalidoException;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import br.com.nordstay.reservation_service.security.SecurityConfiguracao;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
@Import({SecurityConfiguracao.class, CheckoutSecurityControllerTest.JwtTestConfiguration.class})
class CheckoutSecurityControllerTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("0f8e3b6a-1d2c-4e5f-9a7b-6c4d2e1f0a93");
    private static final UUID CORRELATION_ID =
            UUID.fromString("e3b9a1c7-5d2f-4a86-b0c4-9f7e1d3a5b62");
    private static final RSAKey RSA_KEY = criarChaveTeste();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private ConsultarReservaUseCase consultarReserva;

    @MockitoBean
    private ConsultaRateLimitPort consultaRateLimit;

    @MockitoBean
    private RealizarCheckinUseCase realizarCheckin;

    @MockitoBean
    private SolicitarCheckoutUseCase solicitarCheckout;

    @Test
    void retorna401QuandoTokenEstaAusente() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(solicitarCheckout);
    }

    @Test
    void retorna401QuandoTokenNaoPodeSerValidado() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(solicitarCheckout);
    }

    @Test
    void retorna403QuandoSubjectNaoCorrespondeAReservaDaRota() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Authorization", bearerToken("RSV-20261120-9999")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(solicitarCheckout);
    }

    @Test
    void retorna202ParaJwtAssinadoComSubjectDaReserva() throws Exception {
        CheckoutAceito resposta = new CheckoutAceito(
                RESERVA_ID,
                "CHECKOUT_SOLICITADO",
                CORRELATION_ID
        );
        when(solicitarCheckout.solicitar(new SolicitarCheckoutCommand(RESERVA_ID, IDEMPOTENCY_KEY)))
                .thenReturn(resposta);

        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Authorization", bearerToken(RESERVA_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reservaId").value(RESERVA_ID))
                .andExpect(jsonPath("$.status").value("CHECKOUT_SOLICITADO"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()));

        verify(solicitarCheckout).solicitar(
                eq(new SolicitarCheckoutCommand(RESERVA_ID, IDEMPOTENCY_KEY))
        );
    }

    @Test
    void retorna400QuandoIdempotencyKeyNaoEhUuid() throws Exception {
        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", "invalida")
                        .header("Authorization", bearerToken(RESERVA_ID)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(solicitarCheckout);
    }

    @Test
    void retorna404QuandoReservaNaoExiste() throws Exception {
        when(solicitarCheckout.solicitar(new SolicitarCheckoutCommand(RESERVA_ID, IDEMPOTENCY_KEY)))
                .thenThrow(new ReservaNaoEncontradaException());

        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Authorization", bearerToken(RESERVA_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void retorna409QuandoStatusNaoPermiteCheckout() throws Exception {
        when(solicitarCheckout.solicitar(new SolicitarCheckoutCommand(RESERVA_ID, IDEMPOTENCY_KEY)))
                .thenThrow(new StatusCheckoutInvalidoException(RESERVA_ID, StatusReserva.CHECKED_OUT));

        mockMvc.perform(post("/api/reservas/{reservaId}/checkout", RESERVA_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("Authorization", bearerToken(RESERVA_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Checkout não permitido"));
    }

    private String bearerToken(String subject) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(agora.minusSeconds(1))
                .expiresAt(agora.plusSeconds(60))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(RSA_KEY.getKeyID())
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey criarChaveTeste() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("checkout-test-key")
                    .build();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        JwtDecoder jwtDecoder() throws Exception {
            return NimbusJwtDecoder.withPublicKey(RSA_KEY.toRSAPublicKey()).build();
        }

        @Bean
        JwtEncoder jwtEncoder() {
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(RSA_KEY)));
        }
    }
}
