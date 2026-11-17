package br.com.nordstay.reservation_service.application.port.out;

import br.com.nordstay.reservation_service.application.port.in.CheckinAceito;
import br.com.nordstay.reservation_service.domain.model.Reserva;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RegistrarCheckinPort {

    Optional<CheckinAceito> buscarResposta(String reservaId, UUID idempotencyKey);

    /**
     * Registra o check-in de forma idempotente. A implementação deve, numa única
     * operação atômica, atualizar a reserva, guardar a resposta por 24 horas e
     * adicionar o evento à outbox. Para uma chave já registrada, deve devolver
     * a resposta previamente armazenada sem criar outro evento.
     */
    CheckinAceito registrar(
            Reserva reserva,
            UUID idempotencyKey,
            CheckinAceito resposta,
            OffsetDateTime checkinEm,
            Integer acompanhantes,
            LocalTime previsaoChegada);
}
