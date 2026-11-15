package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public record ReservaRedisDTO(
        String reservaId,
        String quartoId,
        String hospedeNome,
        String hospedeDocumentoMascarado,
        String hospedeDocumentoFinal,
        String status,
        String dataCheckin,
        String checkoutPrevisto,
        String diariasCentavos
) {

    public static ReservaRedisDTO fromHash(Map<Object, Object> hash) {
        return new ReservaRedisDTO(
                obrigatorio(hash, "reservaId"),
                obrigatorio(hash, "quartoId"),
                obrigatorio(hash, "hospedeNome"),
                obrigatorio(hash, "hospedeDocumentoMascarado"),
                obrigatorio(hash, "hospedeDocumentoFinal"),
                obrigatorio(hash, "status"),
                obrigatorio(hash, "dataCheckin"),
                obrigatorio(hash, "checkoutPrevisto"),
                obrigatorio(hash, "diariasCentavos")
        );
    }

    public Reserva toDomain() {
        return new Reserva(
                reservaId,
                quartoId,
                StatusReserva.valueOf(status),
                new Hospede(hospedeNome, new Documento(hospedeDocumentoMascarado, hospedeDocumentoFinal)),
                LocalDate.parse(dataCheckin),
                OffsetDateTime.parse(checkoutPrevisto).toLocalDate(),
                Long.parseLong(diariasCentavos)
        );
    }

    private static String obrigatorio(Map<Object, Object> hash, String campo) {
        Object valor = hash.get(campo);
        if (!(valor instanceof String texto) || texto.isBlank()) {
            throw new IllegalStateException(
                    "Hash de reserva inválido: campo obrigatório ausente (" + campo + ")");
        }
        return texto;
    }
}
