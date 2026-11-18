package br.com.nordstay.reservation_service.application.port.out;

import java.util.Optional;

public interface OutboxPort {

    Optional<EventoPendente> reservarProximo(String token);

    boolean confirmarPublicacao(String token, EventoPendente evento);

    void liberar(String token);
}
