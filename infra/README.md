```
cd infra
cp .env.example .env
docker compose up -d --build
docker compose ps                                   # aguarde "healthy"
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli set teste ok'
docker compose restart redis
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli get teste'
```