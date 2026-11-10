#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:29092}"
ARQUIVO=/opt/nordstay/topics.txt
BIN=/opt/kafka/bin

PARTICOES_PADRAO="${PARTICOES_PADRAO:-6}"
RETENCAO_DIAS="${RETENCAO_DIAS:-7}"
RETENCAO_DLT_DIAS="${RETENCAO_DLT_DIAS:-14}"
dias_em_ms() { echo $(( $1 * 24 * 60 * 60 * 1000 )); }

# ---------- 1. Estado atual: UMA chamada para todos os tópicos ----------
declare -A PARTICOES_ATUAIS=()
while read -r nome qtd; do
  PARTICOES_ATUAIS["$nome"]="$qtd"
done < <(
  "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --describe --exclude-internal 2>/dev/null \
    | awk '/PartitionCount/ { for (i=1;i<=NF;i++) { if ($i=="Topic:") t=$(i+1); if ($i=="PartitionCount:") p=$(i+1) } print t, p }'
)
echo "Tópicos existentes: ${#PARTICOES_ATUAIS[@]}"

# ---------- 2. Garante um tópico no estado desejado ----------
reconciliar() {
  local topico="$1" particoes="$2" retencao_ms="$3"
  local atual="${PARTICOES_ATUAIS[$topico]:-}"

  if [[ -z "$atual" ]]; then
    echo "+ criando  $topico ($particoes partições, retenção $((retencao_ms / 86400000)) dias)"
    "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
      --topic "$topico" --partitions "$particoes" --replication-factor 1 \
      --config retention.ms="$retencao_ms" --config cleanup.policy=delete
    return
  fi

  if (( atual < particoes )); then
    echo "~ partições $topico: $atual -> $particoes (atenção: muda o roteamento das chaves)"
    "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --alter --topic "$topico" --partitions "$particoes"
  elif (( atual > particoes )); then
    echo "! $topico tem $atual partições (> $particoes). O Kafka não reduz; use 'docker compose down -v' no ambiente local." >&2
  fi

  echo "= retenção $topico: $((retencao_ms / 86400000)) dias"
  "$BIN/kafka-configs.sh" --bootstrap-server "$BOOTSTRAP" --alter \
    --entity-type topics --entity-name "$topico" --add-config retention.ms="$retencao_ms" > /dev/null
}

# ---------- 3. Lê o catálogo e reconcilia tópico + DLT ----------
total=0
while IFS= read -r linha || [[ -n "$linha" ]]; do
  linha="${linha//$'\r'/}"
  linha="$(echo "$linha" | xargs)"
  [[ -z "$linha" || "$linha" == \#* ]] && continue

  topico="${linha%%:*}"
  particoes="${linha#*:}"
  [[ "$particoes" == "$linha" ]] && particoes="$PARTICOES_PADRAO"
  if ! [[ "$particoes" =~ ^[0-9]+$ ]]; then
    echo "ERRO: partições inválidas para '$topico': '$particoes'" >&2
    exit 1
  fi

  reconciliar "$topico"     "$particoes" "$(dias_em_ms "$RETENCAO_DIAS")"
  reconciliar "$topico.dlt" "$particoes" "$(dias_em_ms "$RETENCAO_DLT_DIAS")"
  total=$((total + 1))
done < "$ARQUIVO"

echo
echo "Catálogo: $total tópicos + $total DLTs"
"$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --list --exclude-internal