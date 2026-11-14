package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.TestcontainersConfiguration;
import br.com.nordstay.reservation_service.domain.model.Reserva;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class ReservaSeedDevIntegrationTest {

    private static final Map<String, String> RESERVAS_E_DOCUMENTOS = Map.of(
            "RSV-20261120-0042", "9132",
            "RSV-20261205-0017", "9101",
            "RSV-20261212-0031", "1234",
            "RSV-20270108-0009", "2345",
            "RSV-20270119-0026", "3456"
    );

    @Autowired
    private ReservaRedisAdapter reservaRedisAdapter;

    @Test
    void carregaCincoReservasNoRedisAoIniciarEmDev() {
        assertEquals(5, RESERVAS_E_DOCUMENTOS.size());
        for (Map.Entry<String, String> esperado : RESERVAS_E_DOCUMENTOS.entrySet()) {
            String reservaId = esperado.getKey();
            Reserva reserva = reservaRedisAdapter.buscarPorId(reservaId).orElse(null);
            assertNotNull(reserva, "Reserva seed não carregada: " + reservaId);
            assertEquals(reservaId, reserva.reservaId());
            assertEquals(esperado.getValue(), reserva.hospede().documento().ultimosQuatro());
        }

        Reserva exemplo = reservaRedisAdapter.buscarPorId("RSV-20261120-0042").orElseThrow();
        assertEquals("Marina Albuquerque", exemplo.hospede().nome());
        assertEquals("Q-214", exemplo.quartoId());
        assertEquals(LocalDate.of(2026, 11, 20), exemplo.entrada());
        assertEquals(LocalDate.of(2026, 11, 23), exemplo.saida());
        assertEquals(186000, exemplo.diariasCentavos());
    }
}
