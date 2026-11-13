#!/bin/sh
# Massa de dados do Redis para testar os endpoints dos 3 serviços.
#
# Datas relativas a "hoje" no fuso de Recife (UTC-3, sem horário de verão):
# o check-in só aceita reserva com data = hoje, então datas fixas envelheceriam.
# Os IDs seguem o padrão RSV-<data do check-in>-NNNN e mudam a cada dia.
#
# Idempotente: tudo o que o seed grava fica listado em seed:chaves e é apagado
# na próxima execução. Dados criados pelos serviços em runtime não são tocados.
set -eu

export REDISCLI_AUTH="$REDIS_PASSWORD"
R="redis-cli -h ${REDIS_HOST:-redis} -p ${REDIS_PORT:-6379}"
BUCKET="${MINIO_BUCKET:-nordstay-catalogo}"

r() { $R "$@" > /dev/null; }

# Registra a chave para limpeza na próxima execução
k() { r SADD seed:chaves "$1"; echo "$1"; }

# ---------- Tempo ----------
FUSO=10800                                   # 3 h em segundos
AGORA=$(date -u +%s)
DIA0=$(( (AGORA - FUSO) / 86400 * 86400 + FUSO ))   # 00:00 de hoje em Recife, em epoch

# epoch de "dia D (relativo a hoje) às HH:MM em Recife"
em() { echo $(( DIA0 + $1 * 86400 + $2 * 3600 + ${3:-0} * 60 )); }
iso() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }
data() { date -u -d "@$(( DIA0 + $1 * 86400 ))" +%Y-%m-%d; }
compacta() { date -u -d "@$(( DIA0 + $1 * 86400 ))" +%Y%m%d; }
saida_local() { echo "$(data "$1")T12:00:00-03:00"; }  # checkout previsto 12:00 em Recife

# ---------- Limpeza da execução anterior ----------
echo "Limpando seed anterior"
for chave in $($R SMEMBERS seed:chaves); do r DEL "$chave"; done
r DEL seed:chaves

# ---------- Quartos (fotos já enviadas pelo minio-init) ----------
echo "Quartos"
quarto() { # id tipo andar capacidade diariaCentavos objectKey
  r HSET "$(k "quarto:$1")" quartoId "$1" tipo "$2" andar "$3" capacidade "$4" \
    diariaCentavos "$5" objectKey "$6"
  r SADD "$(k quartos)" "$1"
}
quarto Q-108 STANDARD 1 2 32000 quartos/quarto-standard.jpg
quarto Q-112 DUPLO    1 2 38000 quartos/quarto-duplo.jpg
quarto Q-118 STANDARD 1 2 32000 quartos/quarto-standard.jpg
quarto Q-201 SUPERIOR 2 2 45000 quartos/quarto-superior.jpg
quarto Q-214 SUITE_MASTER 2 2 62000 quartos/suite-master.jpg
quarto Q-305 SUITE_FAMILIA 3 4 54000 quartos/suite-familia.jpg

# ---------- Catálogo (GET /api/catalogo) ----------
echo "Catálogo"
item_catalogo() { # sku nome categoria precoCentavos objectKey descricao
  r HSET "$(k "catalogo:$1")" sku "$1" nome "$2" categoria "$3" precoCentavos "$4" \
    objectKey "$5" descricao "$6" ativo true
  r SADD "$(k catalogo:skus)" "$1"
  r SADD "$(k "catalogo:categoria:$3")" "$1"
}
item_catalogo BEB-AGUA-COCO-500      "Água de coco gelada 500 ml"   FRIGOBAR 1200 itens/agua-coco-500.webp      "Coco verde do litoral sul, servido gelado"
item_catalogo BEB-AGUA-MINERAL-500   "Água mineral 500 ml"          FRIGOBAR  600 itens/agua-mineral-500.webp   "Sem gás"
item_catalogo BEB-REFRI-LATA-350     "Refrigerante lata 350 ml"     FRIGOBAR  800 itens/refri-lata-350.webp     "Cola, guaraná ou laranja"
item_catalogo BEB-CERVEJA-LATA-350   "Cerveja artesanal lata 350 ml" FRIGOBAR 1600 itens/cerveja-lata-350.webp  "Cervejaria pernambucana"
item_catalogo PET-QUEIJO-COALHO-ESPETO "Queijo coalho no espeto"    PETISCO  1900 itens/queijo-coalho.webp      "Com melaço de cana"
item_catalogo PET-TAPIOCA-CARNE-SOL  "Tapioca de carne de sol"      PETISCO  3200 itens/tapioca-carne-sol.webp  "Com queijo coalho e manteiga de garrafa"
item_catalogo PET-CASQUINHA-SIRI     "Casquinha de siri"            PETISCO  2800 itens/casquinha-siri.webp     "Porção com 2 unidades"
item_catalogo SRV-CAFE-NO-QUARTO     "Café da manhã no quarto"      SERVICO  6500 areas-comuns/cafe-da-manha.jpg "Entregue entre 7h e 10h30"
item_catalogo SRV-CABANA-PISCINA     "Cabana na piscina (dia)"      SERVICO 12000 areas-comuns/piscina.jpg      "Cabana com toalhas e balde de gelo"
item_catalogo EXP-JANGADA-PISCINAS   "Passeio de jangada às piscinas naturais" EXPERIENCIA 8000 experiencias/jangada.webp "Saída conforme a maré baixa"
item_catalogo EXP-BUGGY-MARACAIPE    "Passeio de buggy até Maracaípe" EXPERIENCIA 15000 experiencias/buggy.webp "Inclui o pontal de Maracaípe"
item_catalogo EXP-MERGULHO-CILINDRO  "Mergulho com cilindro"        EXPERIENCIA 25000 experiencias/mergulho.webp "Batismo com instrutor, 40 min"

# JSON de um ItemConsumido (só objectKey: a URL assinada nunca é persistida)
item_json() { # sku nome categoria quantidade precoUnitario objectKey
  echo "{\"sku\":\"$1\",\"nome\":\"$2\",\"categoria\":\"$3\",\"quantidade\":$4,\"precoUnitarioCentavos\":$5,\"subtotalCentavos\":$(( $4 * $5 )),\"imagem\":{\"objectKey\":\"$6\"}}"
}

# ---------- Reservas (reservation-service) ----------
reserva() { # id quarto hospedeId nome documento acompanhantes status diaCheckin noites diaria
  noites=$9; diaria=${10}
  r HSET "$(k "reserva:{$1}")" reservaId "$1" quartoId "$2" \
    hospedeId "$3" hospedeNome "$4" hospedeDocumentoMascarado "$5" \
    acompanhantes "$6" status "$7" dataCheckin "$(data "$8")" \
    checkoutPrevisto "$(saida_local $(( $8 + noites )))" \
    noites "$noites" diariasCentavos $(( noites * diaria )) canal APP \
    criadaEm "$(iso $(em -20 10))"
  r SADD "$(k reservas)" "$1"
  r SADD "$(k "reservas:status:$7")" "$1"
}

HOJE=$(compacta 0); AMANHA=$(compacta 1); ONTEM=$(compacta -1); ANTEONTEM3=$(compacta -3)

# 1) CONFIRMADA hoje: caminho feliz do POST /checkin
RSV1="RSV-$HOJE-0042"
reserva "$RSV1" Q-214 HSP-88213 "Marina Albuquerque" "***.482.913-**" 1 CONFIRMADA 0 3 62000

# 2) CONFIRMADA hoje: segundo check-in (testes em paralelo)
RSV2="RSV-$HOJE-0043"
reserva "$RSV2" Q-108 HSP-88214 "Rafael Cavalcanti" "***.731.204-**" 0 CONFIRMADA 0 2 32000

# 3) CONFIRMADA amanhã: POST /checkin deve recusar (data ≠ hoje)
RSV3="RSV-$AMANHA-0051"
reserva "$RSV3" Q-305 HSP-88220 "Juliana Lins" "***.105.668-**" 3 CONFIRMADA 1 4 54000

# 4) NO_SHOW ontem: POST /checkin deve recusar
RSV4="RSV-$ONTEM-0033"
reserva "$RSV4" Q-118 HSP-88209 "Carlos Maranhão" "***.390.517-**" 0 NO_SHOW -1 2 32000

# 5) CHECKED_IN desde ontem: consumo, fatura, WebSocket, acesso e checkout
RSV5="RSV-$ONTEM-0037"
reserva "$RSV5" Q-112 HSP-88211 "Thiago Barreto" "***.624.031-**" 1 CHECKED_IN -1 4 38000
r HSET "reserva:{$RSV5}" checkinEm "$(iso $(em -1 15 4))"

# 6) CHECKED_OUT hoje (saída antecipada): consumo e acesso devem ser recusados
RSV6="RSV-$ANTEONTEM3-0029"
reserva "$RSV6" Q-201 HSP-88205 "Beatriz Wanderley" "***.857.442-**" 0 CHECKED_OUT -3 3 45000
r HSET "reserva:{$RSV6}" checkinEm "$(iso $(em -3 14 48))" checkoutEm "$(iso $(( AGORA - 7200 )))"

# ---------- Chaves digitais (door-lock-service) ----------
# O token é opaco: o JWT RS256 real só o door-lock assina. Ele autoriza o
# WebSocket (comparação de string), mas não passa na validação de assinatura.
chave() { # jti reserva quarto diaEmissao hEmissao validoAteEpoch
  tag="{$2}"
  token="seed-token-$1"
  r HSET "$(k "chave:$1")" jti "$1" reservaId "$2" quartoId "$3" kid nordstay-lock-seed \
    algoritmo RS256 validoDe "$(iso $(em "$4" "$5"))" validoAte "$(iso "$6")" \
    portasPermitidas "[\"$3\",\"PISCINA\",\"ACADEMIA\"]" revogada false token "$token"
  r EXPIREAT "chave:$1" "$6"
  r SET "$(k "chave:reserva:$tag")" "$1" EXAT "$6"
  r SET "$(k "estadia:$tag:token")" "$token" EXAT "$6"
}

JTI5=key-3a9e1f42-7b6c-4d10-9e85-c21f0a7d4b63
chave "$JTI5" "$RSV5" Q-112 -1 15 $(em 3 12)

JTI6=key-8d27c5b1-0f4e-4a93-b6d2-5e1a9c3f7084
chave "$JTI6" "$RSV6" Q-201 -3 14 $(em 1 12)
r HSET "chave:$JTI6" revogada true revogadaEm "$(iso $(( AGORA - 7100 )))"
r SET "$(k "chave:denylist:$JTI6")" "$RSV6" EXAT $(em 1 12)

# ---------- Faturas (billing-service) ----------
# Campos numéricos são os que o aplicar-consumo.lua usa com HINCRBY.
idem() { r SET "$(k "idem:{$1}:$2")" 1 EX 259200; }   # 72 h

# Fatura 5: ABERTA, 2 consumos já aplicados (versao 1 na abertura + 2)
I5A="[$(item_json BEB-AGUA-COCO-500 'Água de coco gelada 500 ml' FRIGOBAR 2 1200 itens/agua-coco-500.webp),$(item_json PET-QUEIJO-COALHO-ESPETO 'Queijo coalho no espeto' PETISCO 1 1900 itens/queijo-coalho.webp)]"
I5B="[$(item_json EXP-JANGADA-PISCINAS 'Passeio de jangada às piscinas naturais' EXPERIENCIA 2 8000 experiencias/jangada.webp)]"
r HSET "$(k "fatura:{$RSV5}")" reservaId "$RSV5" quartoId Q-112 faturaId "FAT-$ONTEM-0037" \
  status ABERTA versao 3 pendentes 0 diariasCentavos 152000 consumoCentavos $(( 2400 + 1900 + 16000 )) \
  abertaEm "$(iso $(em -1 15 5))"
r RPUSH "$(k "fatura:{$RSV5}:itens")" "$I5A" "$I5B"
idem "$RSV5" 4c1d8e7a-2b5f-4e93-a106-7d3c9b2e5f18
idem "$RSV5" 9a6f2c3e-1d8b-4f70-b5e4-0c7a3d9e1b26

# Fatura 6: FECHADA (consumo novo vai para a DLT; taxa de serviço 10% do consumo)
I6A="[$(item_json BEB-AGUA-COCO-500 'Água de coco gelada 500 ml' FRIGOBAR 6 1200 itens/agua-coco-500.webp)]"
I6B="[$(item_json PET-QUEIJO-COALHO-ESPETO 'Queijo coalho no espeto' PETISCO 4 1900 itens/queijo-coalho.webp)]"
CONSUMO6=$(( 7200 + 7600 )); TAXA6=$(( CONSUMO6 / 10 ))
r HSET "$(k "fatura:{$RSV6}")" reservaId "$RSV6" quartoId Q-201 faturaId "FAT-$HOJE-0029" \
  status FECHADA versao 4 pendentes 0 diariasCentavos 135000 consumoCentavos "$CONSUMO6" \
  taxaServicoCentavos "$TAXA6" totalCentavos $(( 135000 + CONSUMO6 + TAXA6 )) \
  abertaEm "$(iso $(em -3 14 49))" fechadaEm "$(iso $(( AGORA - 7150 )))"
r RPUSH "$(k "fatura:{$RSV6}:itens")" "$I6A" "$I6B"
idem "$RSV6" 2e7b9d14-6c3a-4f58-8a0d-b1f6e4c2973a
idem "$RSV6" 7f0c4a68-3e2d-4b19-9c57-a8d1e6b4f302

# ---------- Mapa dos cenários (consulta rápida dos IDs do dia) ----------
r HSET "$(k seed:cenarios)" \
  checkin-ok "$RSV1" checkin-ok-2 "$RSV2" checkin-data-futura "$RSV3" \
  checkin-no-show "$RSV4" hospedado "$RSV5" encerrado "$RSV6" \
  hospedado-token "seed-token-$JTI5" geradoEm "$(iso "$AGORA")"

echo
echo "Seed do Redis concluído ($(date -u -d "@$(( AGORA - FUSO ))" '+%d/%m/%Y %H:%M') em Recife)"
echo "  checkin OK ............ $RSV1 (Q-214)"
echo "  checkin OK ............ $RSV2 (Q-108)"
echo "  checkin data futura ... $RSV3 (Q-305)"
echo "  checkin NO_SHOW ....... $RSV4 (Q-118)"
echo "  hospedado ............. $RSV5 (Q-112) token seed-token-$JTI5"
echo "  encerrado ............. $RSV6 (Q-201) chave $JTI6 na denylist"
echo "  catálogo .............. $($R SCARD catalogo:skus) itens"
echo "  chaves gravadas ....... $($R SCARD seed:chaves)"
