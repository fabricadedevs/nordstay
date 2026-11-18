package br.com.nordstay.reservation_service.application.port.out;

public interface PublicarEventoPort {

    void publicar(String chave, String payload);
}
