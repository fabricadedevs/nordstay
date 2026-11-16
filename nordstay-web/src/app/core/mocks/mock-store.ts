import { HttpResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';
import {
  FaturaResponse,
  ItemFaturaResponse,
  MensagemEstadia,
  ReservaResponse,
} from '../../shared/models/nordstay.models';
import { Cenario } from './cenarios';
import { JTI_MOCK, PORTAS_MOCK, TOKEN_MOCK, faturaInicial, reservaInicial } from './fixtures';

/**
 * Estado do "backend falso". As rotas mock leem e alteram este estado,
 * e o MockEstadiaSocket repassa `eventos$` para o app como se fosse o WebSocket.
 */
@Injectable({ providedIn: 'root' })
export class MockStore {
  readonly reserva = signal<ReservaResponse>(reservaInicial());
  readonly fatura = signal<FaturaResponse>(faturaInicial());
  readonly chaveRevogada = signal(false);

  private readonly eventos = new Subject<MensagemEstadia>();
  readonly eventos$ = this.eventos.asObservable();

  private readonly respostas = new Map<string, HttpResponse<unknown>>();
  private readonly timers = new Set<ReturnType<typeof setTimeout>>();

  // ---------- idempotência (mesma Idempotency-Key = mesma resposta)

  respostaSalva(chave: string): HttpResponse<unknown> | undefined {
    return this.respostas.get(chave);
  }

  salvarResposta(chave: string, resposta: HttpResponse<unknown>): void {
    this.respostas.set(chave, resposta);
  }

  // ---------- regras simuladas

  fazerCheckin(cenario: Cenario): void {
    this.reserva.update((r) => ({ ...r, status: 'CHECKED_IN' }));
    this.depois(cenario === 'lento' ? 10_000 : 2_000, () => {
      this.eventos.next({
        tipo: 'CHAVE_GERADA',
        jti: JTI_MOCK,
        token: TOKEN_MOCK,
        portas: [...PORTAS_MOCK],
        validoAte: this.validoAte(),
      });
      if (cenario === 'chave-revogada') this.depois(5_000, () => this.revogarChave());
    });
  }

  /** Igual ao backend: pendentes +1 na hora; o valor entra depois, com versão +1 e FATURA_ATUALIZADA. */
  registrarConsumo(linhas: ItemFaturaResponse[], cenario: Cenario): void {
    this.fatura.update((f) => ({ ...f, pendentes: f.pendentes + 1 }));
    this.depois(cenario === 'lento' ? 6_000 : 1_500, () => {
      const soma = linhas.reduce((t, l) => t + l.subtotalCentavos, 0);
      this.fatura.update((f) => {
        const consumoCentavos = f.consumoCentavos + soma;
        return {
          ...f,
          itens: mesclarItens(f.itens, linhas),
          consumoCentavos,
          totalCentavos: f.diariasCentavos + consumoCentavos,
          pendentes: Math.max(0, f.pendentes - 1),
          versao: f.versao + 1,
        };
      });
      this.eventos.next({ tipo: 'FATURA_ATUALIZADA', fatura: this.fatura() });
    });
  }

  /** FECHANDO → espera pendentes = 0 (até 30 s) → FECHADA → CHAVE_REVOGADA 1 s depois. */
  solicitarCheckout(cenario: Cenario): void {
    this.reserva.update((r) => ({ ...r, status: 'CHECKOUT_SOLICITADO' }));
    this.fatura.update((f) => ({ ...f, status: 'FECHANDO' }));

    const fechar = () => {
      this.fatura.update((f) => ({ ...f, status: 'FECHADA', versao: f.versao + 1 }));
      this.eventos.next({ tipo: 'FATURA_FECHADA', fatura: this.fatura() });
      this.reserva.update((r) => ({ ...r, status: 'CHECKED_OUT' }));
      this.depois(1_000, () => this.revogarChave());
    };
    const esperar = (restanteMs: number) => {
      if (this.fatura().pendentes === 0 || restanteMs <= 0) {
        this.depois(cenario === 'fatura-fechando' ? 8_000 : 2_000, fechar);
      } else {
        this.depois(500, () => esperar(restanteMs - 500));
      }
    };
    esperar(30_000);
  }

  revogarChave(): void {
    if (this.chaveRevogada()) return;
    this.chaveRevogada.set(true);
    this.eventos.next({ tipo: 'CHAVE_REVOGADA', jti: JTI_MOCK });
  }

  // ---------- botões do painel de cenários

  /** Reenvia a chave com validade no passado, para testar o estado "expirada". */
  expirarChave(): void {
    this.eventos.next({
      tipo: 'CHAVE_GERADA',
      jti: JTI_MOCK,
      token: TOKEN_MOCK,
      portas: [...PORTAS_MOCK],
      validoAte: new Date(Date.now() - 60_000).toISOString(),
    });
  }

  /** Manda uma FATURA_ATUALIZADA com versão antiga: o app tem que ignorar. */
  enviarMensagemForaDeOrdem(): void {
    const f = this.fatura();
    this.eventos.next({
      tipo: 'FATURA_ATUALIZADA',
      fatura: { ...f, versao: Math.max(0, f.versao - 1) },
    });
  }

  /** Volta tudo ao início (usar em testes e no painel). */
  reiniciar(): void {
    this.timers.forEach((t) => clearTimeout(t));
    this.timers.clear();
    this.respostas.clear();
    this.reserva.set(reservaInicial());
    this.fatura.set(faturaInicial());
    this.chaveRevogada.set(false);
  }

  private validoAte(): string {
    return `${this.reserva().saida}T12:00:00-03:00`;
  }

  private depois(ms: number, fn: () => void): void {
    const t = setTimeout(() => {
      this.timers.delete(t);
      fn();
    }, ms);
    this.timers.add(t);
  }
}

function mesclarItens(
  atuais: ItemFaturaResponse[],
  novos: ItemFaturaResponse[],
): ItemFaturaResponse[] {
  const porSku = new Map(atuais.map((i) => [i.sku, { ...i }]));
  for (const n of novos) {
    const existente = porSku.get(n.sku);
    if (existente) {
      existente.quantidade += n.quantidade;
      existente.subtotalCentavos += n.subtotalCentavos;
    } else {
      porSku.set(n.sku, { ...n });
    }
  }
  // o mais recente primeiro, como na tela da conta
  const novosSkus = new Set(novos.map((n) => n.sku));
  const lista = [...porSku.values()];
  return [
    ...lista.filter((i) => novosSkus.has(i.sku)),
    ...lista.filter((i) => !novosSkus.has(i.sku)),
  ];
}
