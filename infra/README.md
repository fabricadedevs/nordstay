# NordStay · Infraestrutura local (NS-01)

Redis, Kafka (KRaft) e MinIO para desenvolvimento local, com tópicos, bucket e imagens de seed criados automaticamente.
Os comandos são para **PowerShell** e rodam **dentro da pasta `infra/`**.

> Usa Git Bash? Veja a seção [Git Bash / MSYS2](#git-bash--msys2) no final.

---

## Instalação rápida

Pré-requisito: **Docker Desktop** ou **Rancher Desktop** rodando.

```powershell
cd infra
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps -a
```

Pronto quando aparecer:

| Serviço    | Status         |
|------------|----------------|
| redis      | `Up (healthy)` |
| kafka      | `Up (healthy)` |
| minio      | `Up (healthy)` |
| kafka-init | `Exited (0)`   |
| minio-init | `Exited (0)`   |
| redis-seed | `Exited (0)`   |

> O **primeiro build demora alguns minutos**: o MinIO e o cliente `mc` são compilados do código-fonte (a imagem oficial saiu do Docker Hub). Os builds seguintes usam cache.

| Recurso | Endereço | Credenciais (`.env`) |
|---|---|---|
| Redis | `localhost:6379` | senha `REDIS_PASSWORD` |
| Kafka (da sua máquina) | `localhost:9092` | — |
| Kafka (entre containers) | `kafka:29092` | — |
| MinIO API (S3) | http://localhost:9000 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| MinIO Console | http://localhost:9001 | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| Imagem de exemplo | http://localhost:9000/nordstay-catalogo/quartos/suite-master.jpg | pública (só leitura) |

Para parar: `docker compose down` (mantém dados) ou `docker compose down -v` (apaga tudo).

---

## Estrutura

```text
infra/
├── docker-compose.yml        # orquestra tudo
├── .env.example              # credenciais locais (copiar para .env)
├── redis/                    # Redis 8 + AOF + senha
├── kafka/                    # Kafka 4.3.1 em modo KRaft (1 nó)
├── kafka-init/               # cria os tópicos de topics.txt e termina
├── minio/                    # MinIO compilado do fonte
├── redis-seed/               # massa de dados do Redis para testar os endpoints
└── minio-init/               # cria o bucket, envia o seed e indexa no Redis
    ├── seed/                 # imagens do catálogo (quartos/, areas-comuns/)
    ├── baixar_seed.py        # baixa as fotos reais do Unsplash
    └── CREDITOS.md           # autor e licença de cada foto
```

---

## 0. Pré-requisitos

- Docker Desktop rodando (Engine com Compose v2)
- Python 3 (só para rebaixar as imagens de seed)
- Arquivos em LF: `*.sh`, `topics.txt`, `redis.conf` e Dockerfiles (garantido pelo `.gitattributes`)

```powershell
docker --version
docker compose version
```

---

## 1. Configuração (uma vez só)

### 1.1 Criar o `.env`

```powershell
cd infra
Copy-Item .env.example .env
```

```env
REDIS_PASSWORD=nordstay-local
KAFKA_CLUSTER_ID=<id do cluster>
MINIO_ROOT_USER=nordstay
MINIO_ROOT_PASSWORD=nordstay-local-123
```

> A senha do MinIO precisa ter **8 caracteres ou mais**.

### 1.2 ID do cluster Kafka (opcional)

O `.env.example` já traz um ID válido. Para gerar outro:

```powershell
docker run --rm apache/kafka:4.3.1 /opt/kafka/bin/kafka-storage.sh random-uuid
```

> Não troque o `KAFKA_CLUSTER_ID` depois de subir: ele fica gravado no volume `kafka-data`.
> Se precisar trocar, rode antes `docker compose down -v`.

### 1.3 Imagens de seed (opcional)

As fotos já estão em `minio-init/seed/`. Para baixá-las de novo do Unsplash:

```powershell
cd ..                                   # raiz do repositório
python infra\minio-init\baixar_seed.py
cd infra
docker compose build minio-init
docker compose up -d minio-init
```

**Esperado:** `ok  NNN KB` por foto e `Concluído: 8 de 8 fotos`.

---

## 2. Build e subida

```powershell
docker compose up -d --build
docker compose ps -a
```

**Esperado:** a tabela da [Instalação rápida](#-instalação-rápida). O Kafka leva de 20 a 40 s para ficar `healthy`; repita `docker compose ps -a` até lá.

```powershell
docker compose logs kafka-init
docker compose logs minio-init
```

**Esperado:**
- `kafka-init`: uma linha `Criando <tópico> (3 partições)` por tópico de `topics.txt` e a lista de tópicos.
- `minio-init`: bucket criado, as 8 imagens enviadas e `Imagens indexadas: 8`.

---

## 3. Testes do Redis

### 3.1 Escrita e leitura

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli set teste ok'
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli get teste'
```

**Esperado:** `OK` e depois `"ok"`.

> Use **aspas simples**: com aspas duplas, o PowerShell tenta expandir `$REDIS_PASSWORD` na sua máquina, e o comando chega ao container sem a senha.

### 3.2 Persistência após restart (AOF + volume)

```powershell
docker compose restart redis
docker compose ps redis                       # aguarde "healthy"
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli get teste'
```

**Esperado:** `"ok"`.

### 3.3 Persistência após recriar o container

```powershell
docker compose down
docker compose up -d
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli get teste'
```

**Esperado:** `"ok"`. Sem `-v`, o volume `redis-data` é mantido.

### 3.4 Sem senha não acessa

```powershell
docker compose exec redis redis-cli get teste
```

**Esperado:** `NOAUTH Authentication required.`

---

## 4. Testes do Kafka

### 4.1 Tópicos criados

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
```

**Esperado:** os tópicos de `topics.txt` + `__consumer_offsets`.

Para ver os detalhes de um tópico:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server kafka:29092 --describe --topic reserva.criada
```

**Esperado:** `PartitionCount: 3` e `ReplicationFactor: 1`.

### 4.2 Produzir e consumir

**Terminal 1** (consumidor):

```powershell
cd infra
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server kafka:29092 `
  --topic reserva.criada `
  --from-beginning
```

**Terminal 2** (produtor):

```powershell
cd infra
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh `
  --bootstrap-server kafka:29092 `
  --topic reserva.criada
```

Digite no produtor e tecle Enter:

```json
{"reservaId": 1}
```

**Esperado:** a mensagem aparece no Terminal 1. Saia com `Ctrl+C` nos dois.

### 4.3 Persistência após restart

```powershell
docker compose restart kafka
docker compose ps kafka                       # aguarde "healthy"
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server kafka:29092 --topic reserva.criada `
  --from-beginning --timeout-ms 10000
```

**Esperado:** `{"reservaId": 1}` reaparece. O consumidor encerra sozinho após 10 s.

### 4.4 kafka-init é idempotente

```powershell
docker compose up -d kafka-init
docker compose ps -a kafka-init
docker compose logs kafka-init
```

**Esperado:** `Exited (0)` de novo, sem erro de "tópico já existe" (graças ao `--if-not-exists`).

### 4.5 Tópico inexistente não é criado automaticamente

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh `
  --bootstrap-server kafka:29092 --topic reserva.criadaa
```

**Esperado:** avisos `UNKNOWN_TOPIC_OR_PARTITION`. Saia com `Ctrl+C`.
Esse é o comportamento desejado (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`): erro de digitação não vira tópico novo.

### 4.6 Validação do `topics.txt`

Adicione temporariamente ao `topics.txt`:

```text
teste.ruim:abc
```

```powershell
docker compose build kafka-init
docker compose up -d kafka-init
docker compose logs kafka-init
docker compose ps -a kafka-init
```

**Esperado:** `ERRO: partições inválidas para 'teste.ruim': 'abc'` e `Exited (1)`.
**Remova a linha** e rode o build e o up de novo.

---

## 5. Testes do MinIO

### 5.1 Saúde, versão e usuário

```powershell
curl.exe -i http://localhost:9000/minio/health/live      # HTTP/1.1 200 OK
docker compose exec minio minio --version                # mostra a RELEASE, não DEVELOPMENT
docker compose exec minio id                             # roda sem ser root
```

> Use `curl.exe`: no Windows PowerShell 5, `curl` é apelido de `Invoke-WebRequest`.

### 5.2 Leitura pública e escrita protegida

```powershell
curl.exe -I http://localhost:9000/nordstay-catalogo/quartos/suite-master.jpg          # 200 OK
curl.exe -i -X PUT --data "x" http://localhost:9000/nordstay-catalogo/invasor.txt     # 403 AccessDenied
```

### 5.3 Console e persistência

1. Abra http://localhost:9001 e entre com `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`.
2. Confira o bucket `nordstay-catalogo` com as pastas `quartos/` e `areas-comuns/`.
3. Rode `docker compose restart minio`, aguarde `healthy` e recarregue: tudo continua lá.

### 5.4 minio-init é idempotente

```powershell
docker compose up -d minio-init
docker compose ps -a minio-init
```

**Esperado:** `Exited (0)` de novo, sem duplicar arquivos (`mb --ignore-existing` + `mirror --overwrite`).

### 5.5 Índice do catálogo no Redis

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli SMEMBERS nordstay:catalogo:imagens'
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli HGETALL nordstay:catalogo:imagem:quartos/suite-master.jpg'
```

**Esperado:** as 8 chaves e, no hash, `chave`, `nome`, `categoria`, `tipo`, `tamanho`, `bucket` e `url`.

| Chave | Tipo | Conteúdo |
|---|---|---|
| `nordstay:catalogo:imagens` | Set | caminhos de todas as imagens |
| `nordstay:catalogo:imagem:<caminho>` | Hash | metadados e URL pública da imagem |

---

## 5.6 Massa de dados do Redis (`redis-seed`)

O `redis-seed` grava reservas, quartos, catálogo, faturas e chaves digitais para testar os endpoints.
Roda a cada `docker compose up` e recria só o que ele mesmo gravou (lista em `seed:chaves`); dados criados pelos serviços não são apagados.

As datas são relativas a **hoje em Recife** (o check-in exige `data = hoje`), então os IDs mudam a cada dia.
Para ver os IDs do dia:

```powershell
docker compose logs redis-seed --no-log-prefix
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli HGETALL seed:cenarios'
```

Comandos para consultar cada registro: [`redis/README.md`](redis/README.md).

Para reaplicar (volta tudo ao estado inicial):

```powershell
docker compose up -d redis-seed
```

| Cenário (`seed:cenarios`) | ID | Quarto | Status | Endpoint e resultado esperado |
|---|---|---|---|---|
| `checkin-ok` | `RSV-<hoje>-0042` | Q-214 | `CONFIRMADA` | `POST /api/reservas/{id}/checkin` → 202 |
| `checkin-ok-2` | `RSV-<hoje>-0043` | Q-108 | `CONFIRMADA` | `POST /api/reservas/{id}/checkin` → 202 |
| `checkin-data-futura` | `RSV-<amanhã>-0051` | Q-305 | `CONFIRMADA` | check-in recusado: data ≠ hoje |
| `checkin-no-show` | `RSV-<ontem>-0033` | Q-118 | `NO_SHOW` | check-in recusado |
| `hospedado` | `RSV-<ontem>-0037` | Q-112 | `CHECKED_IN` | `POST /api/consumos`, `GET /api/faturas/{id}` (versão 3), `/ws/estadias/{id}?token=<hospedado-token>`, `POST /api/reservas/{id}/checkout` |
| `encerrado` | `RSV-<hoje-3>-0029` | Q-201 | `CHECKED_OUT` | fatura `FECHADA`: consumo vai para a DLT; chave na denylist |

| Chave | Tipo | Conteúdo |
|---|---|---|
| `reserva:{RSV}` | Hash | reserva (status, hóspede, datas, `diariasCentavos`) |
| `reservas`, `reservas:status:<STATUS>` | Set | IDs das reservas |
| `quarto:<Q>` / `quartos` | Hash / Set | tipo, diária e foto (`objectKey`) do quarto |
| `catalogo:<sku>` / `catalogo:skus` / `catalogo:categoria:<CAT>` | Hash / Set | 12 itens: `FRIGOBAR`, `PETISCO`, `SERVICO`, `EXPERIENCIA` |
| `fatura:{RSV}` | Hash | `status`, `versao`, `pendentes`, `consumoCentavos`, `diariasCentavos` (campos do `aplicar-consumo.lua`) |
| `fatura:{RSV}:itens` | List | cada elemento é o JSON (array) dos itens de um consumo |
| `idem:{RSV}:<eventId>` | String (TTL 72 h) | marca de idempotência dos consumos já aplicados |
| `chave:<jti>` | Hash (expira em `validoAte`) | chave digital |
| `chave:reserva:{RSV}` | String | `jti` da chave ativa da reserva |
| `chave:denylist:<jti>` | String (TTL até `validoAte`) | chave revogada |
| `estadia:{RSV}:token` | String | token aceito no handshake do WebSocket |

> O token da chave do seed é opaco (`seed-token-<jti>`): libera o WebSocket, mas não passa na validação RS256 do door-lock.
> Para testar `POST /api/acessos`, faça o check-in de `checkin-ok` e use a chave emitida pelo serviço.
>
> Só `areas-comuns/cafe-da-manha.jpg`, `areas-comuns/piscina.jpg` e as fotos dos quartos existem no bucket.
> Os `objectKey` em `itens/` e `experiencias/` assinam URL normalmente, mas o GET da imagem dá 404 até as fotos entrarem no `minio-init/seed/`.

---

## 6. Subir do zero

Apaga containers **e volumes** (Redis, Kafka e MinIO zerados) e sobe tudo de novo:

```powershell
docker compose down -v
docker compose up -d --build
docker compose ps -a
```

**Esperado:** o mesmo da seção 2. Tópicos, bucket, imagens e índice do Redis voltam sozinhos; a chave `teste` e as mensagens do Kafka não existem mais:

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli get teste'   # (nil)
```

---

## 7. Teste negativo do `.env`

```powershell
Rename-Item .env .env.bak
docker compose up -d
```

**Esperado:** erro `defina REDIS_PASSWORD no .env` (ou outra variável) e nada sobe.

```powershell
Rename-Item .env.bak .env
```

---

## 8. Comandos úteis

```powershell
docker compose logs -f kafka                 # acompanhar logs
docker compose logs --tail 50 redis          # últimas 50 linhas
docker compose exec kafka bash               # shell dentro do Kafka
docker volume ls                             # nordstay_redis-data, nordstay_kafka-data, nordstay_minio-data
docker images "nordstay/*"                   # imagens construídas
docker compose down                          # para tudo, mantém dados
docker compose down -v                       # para tudo e apaga dados
```

### Rodar a partir da raiz do repositório

```powershell
cd E:\DEV\Projetos\nordstay
$env:COMPOSE_FILE = "infra/docker-compose.yml"   # vale para esta janela do PowerShell
docker compose ps -a
```

---

## Resolução de problemas

| Sintoma | Causa | Solução |
|---|---|---|
| `no configuration file provided: not found` | Comando fora de `infra/` | `cd infra` ou `$env:COMPOSE_FILE = "infra/docker-compose.yml"` |
| `kafka-init` `Exited (1)` com `NumberFormatException: For input string: "3` | `topics.txt` em CRLF | Salvar em LF; conferir o `.gitattributes` e o `sed` no Dockerfile |
| Log "atropelando" o prefixo (`"afka-init-1 \|`) | `\r` (CRLF) no texto | Mesma solução acima |
| `$'\r': command not found` | `.sh` em CRLF | Salvar em LF e rebuild |
| `UNKNOWN_TOPIC_OR_PARTITION` num tópico que deveria existir | `kafka-init` falhou | `docker compose logs kafka-init` |
| Kafka não sobe após trocar `KAFKA_CLUSTER_ID` | ID diferente do gravado no volume | `docker compose down -v` |
| `NOAUTH Authentication required` | Senha não enviada | Usar `REDISCLI_AUTH` com aspas simples |
| MinIO não sobe, log fala de senha | `MINIO_ROOT_PASSWORD` com menos de 8 caracteres | Aumentar a senha no `.env` |
| Primeiro build do `minio` ou `minio-init` muito lento | Compilação do fonte em Go | Normal na primeira vez; os seguintes usam cache |
| `baixar_seed.py` retorna `401` | Link `/photos/<id>/download` exige login | O script usa o CDN `images.unsplash.com`; atualize o script |
| `minio-init` `Exited (1)` logo após `Indexando imagens no Redis` | Redis fora do ar ou senha errada | `docker compose ps redis` e conferir `REDIS_PASSWORD` |

---

## Git Bash / MSYS2

O Git Bash converte caminhos como `/opt/...` para caminhos do Windows. Antes de usar os comandos acima:

```bash
export MSYS_NO_PATHCONV=1
```

Para deixar fixo, adicione a linha ao `~/.bashrc`. No Bash, troque a crase (`` ` ``) do fim das linhas por `\` e `Copy-Item` por `cp`.
