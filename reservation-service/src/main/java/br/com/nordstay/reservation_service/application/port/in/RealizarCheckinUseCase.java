package br.com.nordstay.reservation_service.application.port.in;

public interface RealizarCheckinUseCase {
    CheckinAceito realizar(RealizarCheckinCommand command);
}