package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
import br.com.nordstay.reservation_service.domain.model.Documento;
import br.com.nordstay.reservation_service.domain.model.Hospede;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import br.com.nordstay.reservation_service.domain.model.StatusReserva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservaRedisAdapterIntegrationTest {

    private static final String RESERVA_ID = "RSV-20261120-0042";
    private static final String CHAVE = "reserva:{" + RESERVA_ID + "}";

    @Autowired
    private ReservaRedisAdapter adapter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void limparRedis() {
        redisTemplate.delete(CHAVE);
    }

    @Test
    void retornaReservaMapeadaQuandoHashExiste() {
        redisTemplate.opsForHash().putAll(CHAVE, Map.of(
                "quartoId", "Q-214",
                "status", "CONFIRMADA",
                "nome", "Hóspede de teste",
                "documento", "12345679132",
                "entrada", "2026-11-20",
                "saida", "2026-11-23",
                "diariasCentavos", "186000"
        ));

        Reserva esperado = new Reserva(
                RESERVA_ID,
                "Q-214",
                StatusReserva.CONFIRMADA,
                new Hospede("Hóspede de teste", new Documento("12345679132")),
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 23),
                186000
        );

        assertEquals(esperado, adapter.buscarPorId(RESERVA_ID).orElseThrow());
    }

    @Test
    void retornaVazioQuandoHashNaoExiste() {
        assertTrue(adapter.buscarPorId(RESERVA_ID).isEmpty());
    }
}