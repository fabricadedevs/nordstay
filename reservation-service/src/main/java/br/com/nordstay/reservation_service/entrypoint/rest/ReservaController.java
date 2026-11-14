package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.application.port.in.ConsultarReservaUseCase;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ConsultarReservaUseCase consultarReserva;

    public ReservaController(ConsultarReservaUseCase consultarReserva) {
        this.consultarReserva = consultarReserva;
    }

    @GetMapping("/{reservaId}")
    public ResponseEntity<ReservaResponse> buscar(
            @PathVariable
            @Pattern(regexp = "RSV-\\d{8}-\\d{4}")
            String reservaId,
            @RequestHeader("X-Documento-Final")
            @Pattern(regexp = "\\d{4}")
            String documentoFinal) {

        Reserva reserva = consultarReserva.consultar(reservaId, documentoFinal);
        return ResponseEntity.ok(ReservaResponse.de(reserva));
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