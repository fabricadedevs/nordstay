package br.com.nordstay.reservation_service.application.service;

import br.com.nordstay.reservation_service.application.port.in.CheckoutAceito;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutCommand;
import br.com.nordstay.reservation_service.application.port.in.SolicitarCheckoutUseCase;
import br.com.nordstay.reservation_service.application.port.out.RegistrarCheckoutPort;
import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.exception.CheckinRequisicaoInvalidaException;
import br.com.nordstay.reservation_service.domain.exception.ReservaNaoEncontradaException;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.service.PoliticaCheckout;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class SolicitarCheckoutService implements SolicitarCheckoutUseCase {

    private static final String STATUS_RESPOSTA = "CHECKOUT_SOLICITADO";

    private final ReservaRepositoryPort reservaRepository;
    private final RegistrarCheckoutPort registrarCheckout;
    private final PoliticaCheckout politicaCheckout;
    private final Clock clock;

    public SolicitarCheckoutService(
            ReservaRepositoryPort reservaRepository,
            RegistrarCheckoutPort registrarCheckout,
            PoliticaCheckout politicaCheckout,
            Clock clock) {
        this.reservaRepository = reservaRepository;
        this.registrarCheckout = registrarCheckout;
        this.politicaCheckout = politicaCheckout;
        this.clock = clock;
    }

    @Override
    public CheckoutAceito solicitar(SolicitarCheckoutCommand command) {
        validarRequisicao(command);

        Reserva reserva = reservaRepository.buscarPorId(command.reservaId())
                .orElseThrow(ReservaNaoEncontradaException::new);

        var respostaExistente = registrarCheckout.buscarResposta(
                command.reservaId(),
                command.idempotencyKey()
        );
        if (respostaExistente.isPresent()) {
            return respostaExistente.get();
        }

        politicaCheckout.validarElegibilidade(reserva);
        CheckoutAceito resposta = new CheckoutAceito(
                reserva.reservaId(),
                STATUS_RESPOSTA,
                UUID.randomUUID()
        );
        return registrarCheckout.registrar(
                reserva,
                command.idempotencyKey(),
                resposta,
                OffsetDateTime.now(clock)
        );
    }

    private void validarRequisicao(SolicitarCheckoutCommand command) {
        if (command == null) {
            throw new CheckinRequisicaoInvalidaException("Comando de checkout é obrigatório");
        }
        if (command.reservaId() == null || command.reservaId().isBlank()) {
            throw new CheckinRequisicaoInvalidaException("Código da reserva é obrigatório");
        }
        if (command.idempotencyKey() == null) {
            throw new CheckinRequisicaoInvalidaException("Idempotency-Key é obrigatório");
        }
    }
}
