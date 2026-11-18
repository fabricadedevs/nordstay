package br.com.nordstay.reservation_service.external.database.redis;

import br.com.nordstay.reservation_service.application.port.out.EventoPendente;
import br.com.nordstay.reservation_service.application.port.out.OutboxPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OutboxRedisAdapter implements OutboxPort {

    private static final String OUTBOX_KEY = "outbox:reservation";
    private static final String OUTBOX_PARTITION_KEYS_KEY = "outbox:reservation:partition-keys";
    private static final String LOCK_KEY = "outbox:reservation:publisher-lock";

    private static final DefaultRedisScript<String> RESERVAR_SCRIPT = new DefaultRedisScript<>("""
            local tipoEventos = redis.call('TYPE', KEYS[1]).ok
            local tipoChaves = redis.call('TYPE', KEYS[2]).ok
            if (tipoEventos ~= 'none' and tipoEventos ~= 'list')
                or (tipoChaves ~= 'none' and tipoChaves ~= 'list') then
                return 'INVALID_TYPE'
            end

            if not redis.call('SET', KEYS[3], ARGV[1], 'NX', 'PX', ARGV[2]) then
                return 'BUSY'
            end

            local evento = redis.call('LINDEX', KEYS[1], 0)
            if not evento then
                redis.call('DEL', KEYS[3])
                return 'EMPTY'
            end

            local chave = redis.call('LINDEX', KEYS[2], 0)
            if not chave then
                redis.call('DEL', KEYS[3])
                return 'INCONSISTENT'
            end

            return evento .. string.char(10) .. chave
            """, String.class);

    private static final DefaultRedisScript<String> CONFIRMAR_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[3]) ~= ARGV[1] then
                return 'LOCK_LOST'
            end
            if redis.call('LINDEX', KEYS[1], 0) ~= ARGV[2]
                or redis.call('LINDEX', KEYS[2], 0) ~= ARGV[3] then
                return 'HEAD_CHANGED'
            end

            redis.call('LPOP', KEYS[1])
            redis.call('LPOP', KEYS[2])
            redis.call('DEL', KEYS[3])
            return 'ACKNOWLEDGED'
            """, String.class);

    private static final DefaultRedisScript<Long> LIBERAR_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long lockTtlMillis;

    public OutboxRedisAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${reservation.outbox.lock-ttl-ms:40000}") long lockTtlMillis) {
        this.redisTemplate = redisTemplate;
        this.lockTtlMillis = lockTtlMillis;
    }

    @Override
    public Optional<EventoPendente> reservarProximo(String token) {
        String resultado = redisTemplate.execute(
                RESERVAR_SCRIPT,
                List.of(OUTBOX_KEY, OUTBOX_PARTITION_KEYS_KEY, LOCK_KEY),
                token,
                Long.toString(lockTtlMillis)
        );
        if (resultado == null) {
            throw new IllegalStateException("Redis não retornou o resultado da reserva da outbox");
        }
        if (resultado.equals("EMPTY") || resultado.equals("BUSY")) {
            return Optional.empty();
        }
        if (resultado.equals("INVALID_TYPE")) {
            throw new IllegalStateException("As chaves da outbox não são listas Redis");
        }
        if (resultado.equals("INCONSISTENT")) {
            throw new IllegalStateException("Evento sem chave de partição na outbox");
        }

        int separador = resultado.lastIndexOf('\n');
        if (separador < 0) {
            throw new IllegalStateException("Registro inválido na outbox");
        }
        return Optional.of(new EventoPendente(
                resultado.substring(separador + 1),
                resultado.substring(0, separador)
        ));
    }

    @Override
    public boolean confirmarPublicacao(String token, EventoPendente evento) {
        String resultado = redisTemplate.execute(
                CONFIRMAR_SCRIPT,
                List.of(OUTBOX_KEY, OUTBOX_PARTITION_KEYS_KEY, LOCK_KEY),
                token,
                evento.payload(),
                evento.chave()
        );
        if ("ACKNOWLEDGED".equals(resultado)) {
            return true;
        }
        if ("LOCK_LOST".equals(resultado) || "HEAD_CHANGED".equals(resultado)) {
            return false;
        }
        throw new IllegalStateException("Resultado inesperado ao confirmar publicação da outbox");
    }

    @Override
    public void liberar(String token) {
        Long removida = redisTemplate.execute(LIBERAR_SCRIPT, List.of(LOCK_KEY), token);
        if (removida == null) {
            throw new IllegalStateException("Redis não retornou o resultado da liberação da outbox");
        }
    }
}
