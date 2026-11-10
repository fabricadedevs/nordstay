# NS-01 · Build e testes da infraestrutura (Redis + Kafka)

Roteiro para subir e validar a infraestrutura local do NordStay.
Os comandos são para **PowerShell** e rodam **dentro da pasta `infra/`**.

> Usa Git Bash? Veja a seção [Git Bash / MSYS2](#git-bash--msys2) no final.

---

## 0. Pré-requisitos

- Docker Desktop rodando (Engine com Compose v2)
- Arquivos em LF: `create-topics.sh`, `topics.txt`, `redis.conf` e Dockerfiles (garantido pelo `.gitattributes`)

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

### 1.2 Gerar o ID do cluster Kafka

```powershell
docker run --rm apache/kafka:4.3.1 /opt/kafka/bin/kafka-storage.sh random-uuid
```

Cole o valor gerado no `.env` e no `.env.example`:

```env
REDIS_PASSWORD=nordstay-local
KAFKA_CLUSTER_ID=<valor gerado>
```

> Não troque o `KAFKA_CLUSTER_ID` depois de subir: ele fica gravado no volume `kafka-data`.
> Se precisar trocar, rode antes `docker compose down -v`.

---

## 2. Build e subida

```powershell
docker compose up -d --build
docker compose ps -a
```

**Esperado:**

| Serviço    | Status            |
|------------|-------------------|
| redis      | `Up (healthy)`    |
| kafka      | `Up (healthy)`    |
| kafka-init | `Exited (0)`      |

O Kafka leva de 20 a 40 s para ficar `healthy`. Repita `docker compose ps -a` até lá.

```powershell
docker compose logs kafka-init
```

**Esperado:** uma linha `Criando <tópico> (3 partições)` por tópico de `topics.txt` e, ao final, a lista de tópicos.

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

## 5. Subir do zero

Apaga containers **e volumes** (Redis e Kafka zerados) e sobe tudo de novo:

```powershell
docker compose down -v
docker compose up -d --build
docker compose ps -a
```

**Esperado:** o mesmo da seção 2. A chave `teste` do Redis e as mensagens do Kafka não existem mais:

```powershell
docker compose exec redis sh -c 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli get teste'   # (nil)
```

---

## 6. Teste negativo do `.env`

```powershell
Rename-Item .env .env.bak
docker compose up -d
```

**Esperado:** erro `defina REDIS_PASSWORD no .env` (ou `KAFKA_CLUSTER_ID`) e nada sobe.

```powershell
Rename-Item .env.bak .env
```

---

## 7. Comandos úteis

```powershell
docker compose logs -f kafka                 # acompanhar logs
docker compose logs --tail 50 redis          # últimas 50 linhas
docker compose exec kafka bash               # shell dentro do Kafka
docker volume ls                             # ver volumes (nordstay_redis-data, nordstay_kafka-data)
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
| `$'\r': command not found` | `create-topics.sh` em CRLF | Salvar o `.sh` em LF e rebuild |
| `UNKNOWN_TOPIC_OR_PARTITION` num tópico que deveria existir | `kafka-init` falhou | `docker compose logs kafka-init` |
| Kafka não sobe após trocar `KAFKA_CLUSTER_ID` | ID diferente do gravado no volume | `docker compose down -v` |
| `NOAUTH Authentication required` | Senha não enviada | Usar `REDISCLI_AUTH` com aspas simples |

---

## Git Bash / MSYS2

O Git Bash converte caminhos como `/opt/...` para caminhos do Windows. Antes de usar os comandos acima:

```bash
export MSYS_NO_PATHCONV=1
```

Para deixar fixo, adicione a linha ao `~/.bashrc`. No Bash, troque a crase (`` ` ``) do fim das linhas por `\` e `Copy-Item` por `cp`.