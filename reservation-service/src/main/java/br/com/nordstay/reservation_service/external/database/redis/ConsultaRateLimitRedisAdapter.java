package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.application.port.out.ConsultaRateLimitPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Repository
public class ConsultaRateLimitRedisAdapter implements ConsultaRateLimitPort {

    private static final int LIMITE_CONSULTAS = 10;
    private static final String PREFIXO_CHAVE = "rate-limit:consultar-reserva:";
    private static final DefaultRedisScript<Long> INCREMENTAR_E_DEFINIR_EXPIRACAO =
            new DefaultRedisScript<>(
                    """
                    local total = redis.call('INCR', KEYS[1])
                    if total == 1 then
                        redis.call('EXPIRE', KEYS[1], 60)
                    end
                    return total
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public ConsultaRateLimitRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean registrarConsulta(String ip) {
        Long total = redisTemplate.execute(
                INCREMENTAR_E_DEFINIR_EXPIRACAO,
                List.of(chavePara(ip))
        );
        if (total == null) {
            throw new IllegalStateException("Redis não retornou o total de consultas");
        }
        return total <= LIMITE_CONSULTAS;
    }

    private String chavePara(String ip) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(ip.getBytes(StandardCharsets.UTF_8));
            return PREFIXO_CHAVE + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível", exception);
        }
    }
}
