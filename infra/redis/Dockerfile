FROM redis:8.2.2-alpine

COPY redis.conf /usr/local/etc/redis/redis.conf

CMD ["sh", "-c", "exec redis-server /usr/local/etc/redis/redis.conf --requirepass \"$REDIS_PASSWORD\""]

HEALTHCHECK --interval=5s --timeout=3s --retries=10 \
  CMD REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping | grep -q PONG