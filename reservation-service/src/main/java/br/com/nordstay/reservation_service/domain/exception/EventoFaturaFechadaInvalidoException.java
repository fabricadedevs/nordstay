package br.com.nordstay.reservation_service.domain.exception;

public class EventoFaturaFechadaInvalidoException extends RuntimeException {

    public EventoFaturaFechadaInvalidoException(String detalhe) {
        super("Evento nordstay.fatura-fechada inválido: " + detalhe);
    }

    public EventoFaturaFechadaInvalidoException(String detalhe, Throwable causa) {
        super("Evento nordstay.fatura-fechada inválido: " + detalhe, causa);
    }
}
