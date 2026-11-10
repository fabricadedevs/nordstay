#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:29092}"
TOPICS=/opt/kafka/bin/kafka-topics.sh
ARQUIVO=/opt/nordstay/topics.txt

# "|| [[ -n $linha ]]" garante que a última linha seja lida mesmo sem \n no final
while IFS= read -r linha || [[ -n "$linha" ]]; do
  # Remove \r (CRLF do Windows) e espaços nas pontas
  linha="${linha//$'\r'/}"
  linha="$(echo "$linha" | xargs)"

  # Ignora linhas vazias e comentários
  [[ -z "$linha" || "$linha" == \#* ]] && continue

  topic="${linha%%:*}"
  partitions="${linha#*:}"
  [[ "$partitions" == "$linha" ]] && partitions=3   # linha sem ":" usa 3 partições

  # Falha com mensagem clara se a partição não for número
  if ! [[ "$partitions" =~ ^[0-9]+$ ]]; then
    echo "ERRO: partições inválidas para '$topic': '$partitions'" >&2
    exit 1
  fi

  echo "Criando $topic ($partitions partições)"
  "$TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor 1
done < "$ARQUIVO"

echo "Tópicos existentes:"
"$TOPICS" --bootstrap-server "$BOOTSTRAP" --list