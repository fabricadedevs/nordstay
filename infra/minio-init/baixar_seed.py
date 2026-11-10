"""
Baixa as fotos reais do Unsplash usadas como seed do bucket nordstay-catalogo.

Uso (de qualquer pasta):
    python infra/minio-init/baixar_seed.py

Sem dependências externas: só a biblioteca padrão do Python 3.
Créditos e licença: veja CREDITOS.md nesta mesma pasta.
"""
from __future__ import annotations

import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

LARGURA = 1600  # px; mantém o seed leve (~200-450 KB por foto)

# destino no seed  ->  (id da página no Unsplash, arquivo no CDN images.unsplash.com)
# O id da página é só referência (CREDITOS.md); o download usa o CDN, que é público.
FOTOS: dict[str, tuple[str, str]] = {
    "quartos/quarto-standard.jpg":    ("9QhyuOeu3Ik", "photo-1778205015308-1ed324aea202"),
    "quartos/quarto-duplo.jpg":       ("cDRKleMCFrs", "photo-1790774877815-5358d88f4c52"),
    "quartos/quarto-superior.jpg":    ("LV0OCPkmEWo", "photo-1689729738817-fb1f4256769d"),
    "quartos/suite-master.jpg":       ("iXtriAA5HWk", "photo-1733253956603-a417ec11cc77"),
    "quartos/suite-familia.jpg":      ("4MQ0T4zBIys", "photo-1741506131058-533fcf894483"),
    "areas-comuns/recepcao.jpg":      ("-l1mdvHjaR4", "photo-1782890790046-2aefb0b503cb"),
    "areas-comuns/cafe-da-manha.jpg": ("hrlvr2ZlUNk", "photo-1504754524776-8f4f37790ca0"),
    "areas-comuns/piscina.jpg":       ("D1yT791Nf9A", "photo-1623718649591-311775a30c43"),
}

SEED = Path(__file__).resolve().parent / "seed"
HEADERS = {"User-Agent": "nordstay-seed/1.0 (+https://github.com/fabricadedevs/nordstay)"}


def baixar(arquivo_cdn: str, tentativas: int = 3) -> bytes:
    # O endpoint /photos/<id>/download exige login (401); o CDN não.
    url = f"https://images.unsplash.com/{arquivo_cdn}?w={LARGURA}&q=80&fm=jpg&fit=max"
    for n in range(1, tentativas + 1):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=60) as resp:  # segue redirects
                dados = resp.read()
            if not dados.startswith(b"\xff\xd8"):  # assinatura de JPEG
                raise ValueError("resposta não é um JPEG")
            return dados
        except (urllib.error.URLError, ValueError, TimeoutError) as e:
            if n == tentativas:
                raise
            print(f"    tentativa {n} falhou ({e}); tentando de novo...")
            time.sleep(2 * n)
    raise RuntimeError("inalcançável")


def main() -> int:
    falhas = 0
    for destino, (foto_id, arquivo_cdn) in FOTOS.items():
        caminho = SEED / destino
        caminho.parent.mkdir(parents=True, exist_ok=True)
        print(f"-> {destino}  (unsplash.com/photos/{foto_id})")
        try:
            dados = baixar(arquivo_cdn)
        except Exception as e:  # noqa: BLE001
            print(f"   ERRO: {e}  (arquivo atual mantido)")
            falhas += 1
            continue
        # grava num temporário e só então substitui: nunca deixa arquivo pela metade
        tmp = caminho.with_suffix(".tmp")
        tmp.write_bytes(dados)
        tmp.replace(caminho)
        print(f"   ok  {len(dados) // 1024} KB")

    print()
    print(f"Concluído: {len(FOTOS) - falhas} de {len(FOTOS)} fotos em {SEED}")
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(main())
