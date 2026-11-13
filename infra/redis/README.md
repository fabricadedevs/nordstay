# Redis · consultas da massa de dados

Comandos para inspecionar os registros gravados pelo `redis-seed` (e pelos serviços em runtime).
Rode tudo **dentro da pasta `infra/`**, com o compose de pé.

> Os IDs das reservas mudam todo dia (datas relativas a hoje). Comece sempre pela [seção 2](#2-ids-do-dia).

---

## 1. Conectar

### Opção A · prompt interativo

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli'
```

Dentro do prompt, digite os comandos Redis puros (ex.: `HGETALL seed:cenarios`). Sai com `exit`.

### Opção B · helper `rcli` (um comando por vez)

**PowerShell** (vale para a janela atual; cole no `$PROFILE` para deixar fixo):

```powershell
function rcli { docker compose exec -T redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli "$@"' rcli @args }
```

**Git Bash:**

```bash
export MSYS_NO_PATHCONV=1
rcli() { docker compose exec -T redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli "$@"' rcli "$@"; }
```

> Chaves com `{...}` (hash tag) vão entre aspas: `rcli HGETALL "fatura:{$id}"`.
> No PowerShell, `"{$id}"` mantém as chaves e troca só o `$id`.

Os exemplos abaixo usam o `rcli` no PowerShell.

---

## 2. IDs do dia

```powershell
rcli HGETALL seed:cenarios
```

| Campo | Cenário |
|---|---|
| `checkin-ok`, `checkin-ok-2` | `CONFIRMADA` hoje: check-in aceito |
| `checkin-data-futura` | `CONFIRMADA` amanhã: check-in recusado |
| `checkin-no-show` | `NO_SHOW`: check-in recusado |
| `hospedado` | `CHECKED_IN`, fatura `ABERTA` |
| `hospedado-token` | token do WebSocket do `hospedado` |
| `encerrado` | `CHECKED_OUT`, fatura `FECHADA`, chave revogada |

Guardar em variáveis para os próximos comandos:

```powershell
$ok  = rcli HGET seed:cenarios checkin-ok
$id  = rcli HGET seed:cenarios hospedado
$fim = rcli HGET seed:cenarios encerrado
```

---

## 3. Reservas (reservation-service)

```powershell
rcli HGETALL "reserva:{$ok}"                          # reserva completa
rcli HMGET "reserva:{$id}" status quartoId dataCheckin checkoutPrevisto
rcli SMEMBERS reservas                                # todos os IDs
rcli SMEMBERS reservas:status:CONFIRMADA              # por status: CONFIRMADA, CHECKED_IN, CHECKED_OUT, NO_SHOW
```

## 4. Quartos

```powershell
rcli SMEMBERS quartos
rcli HGETALL quarto:Q-214                             # tipo, andar, capacidade, diariaCentavos, objectKey
```

## 5. Catálogo (billing-service · `GET /api/catalogo`)

```powershell
rcli SMEMBERS catalogo:skus                           # 12 SKUs
rcli SMEMBERS catalogo:categoria:EXPERIENCIA          # FRIGOBAR, PETISCO, SERVICO, EXPERIENCIA
rcli HGETALL catalogo:EXP-JANGADA-PISCINAS
rcli HMGET catalogo:BEB-AGUA-COCO-500 nome precoCentavos objectKey
```

URLs pré-assinadas em cache (criadas pelo billing em runtime, TTL de 4 min):

```powershell
rcli --scan --pattern 'presigned:*'
```

## 6. Faturas (billing-service · `GET /api/faturas/{id}`)

```powershell
rcli HGETALL "fatura:{$id}"                           # status, versao, pendentes, consumoCentavos, diariasCentavos
rcli HMGET "fatura:{$id}" status versao pendentes     # o que o checkout olha
rcli LLEN "fatura:{$id}:itens"                        # nº de consumos aplicados
rcli LRANGE "fatura:{$id}:itens" 0 -1                 # cada linha = JSON (array) de um consumo
rcli HGETALL "fatura:{$fim}"                          # FECHADA, com taxaServicoCentavos e totalCentavos
```

## 7. Idempotência dos consumos

```powershell
rcli --scan --pattern "idem:{$id}:*"                  # eventIds já aplicados na reserva
rcli TTL "idem:{$id}:4c1d8e7a-2b5f-4e93-a106-7d3c9b2e5f18"   # segundos restantes (72 h no total)
```

`EXISTS` igual a `1` quer dizer que o evento já foi aplicado: o Lua devolve `0` (duplicado).

## 8. Chaves digitais (door-lock-service)

```powershell
$jti = rcli GET "chave:reserva:{$id}"                 # jti da chave ativa
rcli HGETALL "chave:$jti"                             # validoDe, validoAte, portasPermitidas, revogada
rcli TTL "chave:$jti"                                 # expira em validoAte
rcli --scan --pattern 'chave:denylist:*'              # chaves revogadas
rcli EXISTS "chave:denylist:$jti"                     # 1 = revogada
```

## 9. Tempo real (WebSocket)

```powershell
rcli GET "estadia:{$id}:token"                        # token aceito em /ws/estadias/{id}?token=...
```

Acompanhar as mensagens publicadas para o WebSocket (`Ctrl+C` para sair):

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli PSUBSCRIBE estadia:*'
```

## 10. Imagens do MinIO (índice do `minio-init`)

```powershell
rcli SMEMBERS nordstay:catalogo:imagens
rcli HGETALL nordstay:catalogo:imagem:quartos/suite-master.jpg
```

---

## 11. Visão geral e diagnóstico

```powershell
rcli DBSIZE                                           # total de chaves
rcli SCARD seed:chaves                                # chaves gravadas pelo seed
rcli SMEMBERS seed:chaves                             # quais são
rcli --scan --pattern 'reserva:*'                     # listar por prefixo (não bloqueia, ao contrário de KEYS)
rcli TYPE "fatura:{$id}"                              # hash, list, set, string...
rcli MEMORY USAGE "fatura:{$id}:itens"                # bytes
rcli INFO keyspace
```

Acompanhar em tempo real tudo o que os serviços executam (`Ctrl+C` para sair; só em dev, pesa no Redis):

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli MONITOR'
```

## 12. Voltar ao estado inicial

```powershell
docker compose up -d redis-seed
docker compose logs redis-seed --no-log-prefix
```

Recria só as chaves do seed. Chaves criadas pelos serviços (ex.: novas marcas `idem:`) continuam lá.

> Não use `FLUSHALL`/`FLUSHDB`: apaga também o índice de imagens do `minio-init`.
> Para zerar tudo, use `docker compose down -v` e suba de novo (README principal, seção 6).
