package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutCommand;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutUseCase;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinCommand;
import br.com.nordstay.reservation_service.application.port.in.RealizarCheckinUseCase;
import br.com.nordstay.reservation_service.application.port.out.ConsultaRateLimitPort;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ConsultarReservaUseCase consultarReserva;
    private final ConsultaRateLimitPort consultaRateLimit;
    private final RealizarCheckinUseCase realizarCheckin;
    private final SolicitarCheckoutUseCase solicitarCheckout;

    public ReservaController(
            ConsultarReservaUseCase consultarReserva,
            ConsultaRateLimitPort consultaRateLimit,
            RealizarCheckinUseCase realizarCheckin,
            SolicitarCheckoutUseCase solicitarCheckout) {
        this.consultarReserva = consultarReserva;
        this.consultaRateLimit = consultaRateLimit;
        this.realizarCheckin = realizarCheckin;
        this.solicitarCheckout = solicitarCheckout;
    }

    @GetMapping("/{reservaId}")
    public ResponseEntity<?> buscar(
            @PathVariable
            @Pattern(regexp = "RSV-\\d{8}-\\d{4}")
            String reservaId,
            @RequestHeader("X-Documento-Final")
            @Pattern(regexp = "\\d{4}")
            String documentoFinal,
            HttpServletRequest request) {

        if (!consultaRateLimit.registrarConsulta(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErroResponse("Limite de consultas excedido"));
        }
        Reserva reserva = consultarReserva.consultar(reservaId, documentoFinal);
        return ResponseEntity.ok(ReservaResponse.de(reserva));
    }

    @PostMapping("/{reservaId}/checkin")
    public ResponseEntity<CheckinAceitoResponse> realizarCheckin(
            @PathVariable
            @Pattern(regexp = "RSV-\\d{8}-\\d{4}")
            String reservaId,
            @RequestHeader("Idempotency-Key")
            UUID idempotencyKey,
            @RequestHeader("X-Documento-Final")
            @Pattern(regexp = "\\d{4}")
            String documentoFinal,
            @Valid @RequestBody CheckinRequest request) {
        var resultado = realizarCheckin.realizar(new RealizarCheckinCommand(
                reservaId,
                idempotencyKey,
                documentoFinal,
                request.acompanhantes(),
                request.aceiteTermos(),
                request.previsaoChegada()
        ));

        return ResponseEntity.accepted()
                .location(URI.create(resultado.websocket()))
                .body(CheckinAceitoResponse.de(resultado));
    }

    @PostMapping("/{reservaId}/checkout")
    public ResponseEntity<CheckoutAceitoResponse> solicitarCheckout(
            @PathVariable
            @Pattern(regexp = "RSV-\\d{8}-\\d{4}")
            String reservaId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @AuthenticationPrincipal Jwt chave) {
        if (!reservaId.equals(chave.getSubject())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var resultado = solicitarCheckout.solicitar(
                new SolicitarCheckoutCommand(reservaId, idempotencyKey)
        );
        return ResponseEntity.accepted().body(CheckoutAceitoResponse.de(resultado));
    }

    @ExceptionHandler(ReservaNaoEncontradaException.class)
    public ResponseEntity<ErroResponse> reservaNaoEncontrada(
            ReservaNaoEncontradaException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroResponse(exception.getMessage()));
    }

    public record ReservaResponse(
            String reservaId,
            String quartoId,
            String status,
            HospedeResumo hospede,
            LocalDate entrada,
            LocalDate saida,
            long diariasCentavos) {

        private static ReservaResponse de(Reserva reserva) {
            return new ReservaResponse(
                    reserva.reservaId(),
                    reserva.quartoId(),
                    reserva.status().name(),
                    new HospedeResumo(
                            reserva.hospede().nome(),
                            reserva.hospede().documento().mascarado()
                    ),
                    reserva.entrada(),
                    reserva.saida(),
                    reserva.diariasCentavos()
            );
        }
    }

    public record HospedeResumo(String nome, String documentoMascarado) {
    }

    public record ErroResponse(String mensagem) {
    }
}