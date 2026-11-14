package br.com.nordstay.reservation_service.application.port.out;

public interface ConsultaRateLimitPort {
    boolean registrarConsulta(String ip);
}
