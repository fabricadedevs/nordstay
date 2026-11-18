package br.com.nordstay.reservation_service.entrypoint.rest;

import br.com.nordstay.reservation_service.domain.exception.CheckinForaDaDataException;
import br.com.nordstay.reservation_service.domain.exception.CheckinRequisicaoInvalidaException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.exception.StatusCheckinInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.util.UUID;

@RestControllerAdvice(assignableTypes = ReservaController.class)
public class CheckinExceptionHandler {

    @ExceptionHandler(CheckinRequisicaoInvalidaException.class)
    public ResponseEntity<ProblemDetail> requisicaoInvalida(
            CheckinRequisicaoInvalidaException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", exception.getMessage(), request);
    }

    @ExceptionHandler(ReservaNaoEncontradaException.class)
    public ResponseEntity<ProblemDetail> reservaNaoEncontrada(
            ReservaNaoEncontradaException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Reserva não encontrada", exception.getMessage(), request);
    }

    @ExceptionHandler(StatusCheckinInvalidoException.class)
    public ResponseEntity<ProblemDetail> statusInvalido(
            StatusCheckinInvalidoException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Check-in não permitido", exception.getMessage(), request);
    }

    @ExceptionHandler(CheckinForaDaDataException.class)
    public ResponseEntity<ProblemDetail> foraDaData(
            CheckinForaDaDataException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Check-in fora da data", exception.getMessage(), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> validacaoDeParametros(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Requisição inválida", "Parâmetro da requisição inválido", request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", correlationId);
        return ResponseEntity.status(status)
                .header("X-Correlation-Id", correlationId)
                .body(problem);
    }
}
