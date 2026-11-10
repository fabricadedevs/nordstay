#!/bin/sh
set -eu

BUCKET="${MINIO_BUCKET:-nordstay-catalogo}"

echo "Conectando ao MinIO..."
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

echo "Criando bucket $BUCKET (se não existir)"
mc mb --ignore-existing "local/$BUCKET"

echo "Liberando leitura anônima das imagens do catálogo"
mc anonymous set download "local/$BUCKET"

echo "Enviando seed"
mc mirror --overwrite /seed "local/$BUCKET"

echo "Conteúdo do bucket:"
mc ls --recursive "local/$BUCKET"

# ---------- Índice do catálogo no Redis ----------
# Cada imagem vira um hash nordstay:catalogo:imagem:<chave>
# e o conjunto nordstay:catalogo:imagens lista todas as chaves.
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-http://localhost:9000}"
export REDISCLI_AUTH="$REDIS_PASSWORD"
R="redis-cli -h ${REDIS_HOST:-redis} -p ${REDIS_PORT:-6379}"
TMP_SET="nordstay:catalogo:imagens:tmp"

echo "Indexando imagens no Redis"
$R DEL "$TMP_SET" > /dev/null

cd /seed
find . -type f | sed 's|^\./||' | sort | while read -r chave; do
  case "$chave" in
    *.jpg|*.jpeg) tipo=image/jpeg ;;
    *.png)        tipo=image/png ;;
    *.webp)       tipo=image/webp ;;
    *) continue ;;
  esac
  categoria="${chave%%/*}"
  nome="$(basename "$chave")"; nome="${nome%.*}"
  tamanho="$(stat -c %s "$chave")"

  $R HSET "nordstay:catalogo:imagem:$chave" \
    chave "$chave" \
    nome "$nome" \
    categoria "$categoria" \
    tipo "$tipo" \
    tamanho "$tamanho" \
    bucket "$BUCKET" \
    url "$PUBLIC_BASE_URL/$BUCKET/$chave" > /dev/null
  $R SADD "$TMP_SET" "$chave" > /dev/null
  echo "  + $chave"
done

# Troca atômica: quem lê o índice nunca vê uma lista pela metade
if [ "$($R EXISTS "$TMP_SET")" = "1" ]; then
  $R RENAME "$TMP_SET" "nordstay:catalogo:imagens" > /dev/null
fi

echo "Imagens indexadas: $($R SCARD nordstay:catalogo:imagens)"
