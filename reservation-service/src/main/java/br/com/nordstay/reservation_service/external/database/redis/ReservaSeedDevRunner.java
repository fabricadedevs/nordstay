package br.com.nordstay.reservation_service.external.database.redis;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
public class ReservaSeedDevRunner implements ApplicationRunner {

    private static final List<Map.Entry<String, Map<String, String>>> RESERVAS = List.of(
            reserva("RSV-20261120-0042", "Q-214", "CONFIRMADA", "Marina Albuquerque",
                    "12345679132", "2026-11-20", "2026-11-23", "186000"),
            reserva("RSV-20261205-0017", "Q-108", "CONFIRMADA", "Rafael Martins",
                    "23456789101", "2026-12-05", "2026-12-08", "225000"),
            reserva("RSV-20261212-0031", "Q-305", "CONFIRMADA", "Beatriz Costa",
                    "34567891234", "2026-12-12", "2026-12-15", "312000"),
            reserva("RSV-20270108-0009", "Q-412", "CHECKED_IN", "Lucas Ferreira",
                    "45678912345", "2027-01-08", "2027-01-12", "448000"),
            reserva("RSV-20270119-0026", "Q-219", "CONFIRMADA", "Camila Oliveira",
                    "56789123456", "2027-01-19", "2027-01-22", "270000")
    );

    private final StringRedisTemplate redisTemplate;

    public ReservaSeedDevRunner(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Map.Entry<String, Map<String, String>> reserva : RESERVAS) {
            String chave = chave(reserva.getKey());
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(chave))) {
                redisTemplate.opsForHash().putAll(chave, reserva.getValue());
            }
        }
    }

    private static Map.Entry<String, Map<String, String>> reserva(
            String reservaId,
            String quartoId,
            String status,
            String nome,
            String documento,
            String entrada,
            String saida,
            String diariasCentavos) {
        return Map.entry(reservaId, Map.of(
                "quartoId", quartoId,
                "status", status,
                "nome", nome,
                "documento", documento,
                "entrada", entrada,
                "saida", saida,
                "diariasCentavos", diariasCentavos
        ));
    }

    private static String chave(String reservaId) {
        return "reserva:{" + reservaId + "}";
    }
}
