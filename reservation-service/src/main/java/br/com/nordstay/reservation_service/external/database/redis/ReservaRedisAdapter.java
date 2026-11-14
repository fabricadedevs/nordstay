package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.application.port.out.ReservaRepositoryPort;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReservaRedisAdapter implements ReservaRepositoryPort {

    private final StringRedisTemplate redisTemplate;

    public ReservaRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Reserva> buscarPorId(String reservaId) {
        String chave = "reserva:{" + reservaId + "}";
        Map<Object, Object> dados = redisTemplate.opsForHash().entries(chave);

        if (dados.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new Reserva(
                reservaId,
                campoObrigatorio(dados, "quartoId"),
                StatusReserva.valueOf(campoObrigatorio(dados, "status")),
                new Hospede(
                        campoObrigatorio(dados, "nome"),
                        new Documento(campoObrigatorio(dados, "documento"))
                ),
                LocalDate.parse(campoObrigatorio(dados, "entrada")),
                LocalDate.parse(campoObrigatorio(dados, "saida")),
                Long.parseLong(campoObrigatorio(dados, "diariasCentavos"))
        ));
    }

    private String campoObrigatorio(Map<Object, Object> dados, String campo) {
        Object valor = dados.get(campo);
        if (!(valor instanceof String texto) || texto.isBlank()) {
            throw new IllegalStateException(
                    "Hash de reserva inválido: campo obrigatório ausente (" + campo + ")"
            );
        }
        return texto;
    }
}