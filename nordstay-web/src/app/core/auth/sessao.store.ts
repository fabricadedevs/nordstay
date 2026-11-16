import { Injectable, computed, signal } from '@angular/core';
import { MensagemEstadia } from '../../shared/models/nordstay.models';

type ChaveGerada = Extract<MensagemEstadia, { tipo: 'CHAVE_GERADA' }>;

interface DadosSessao {
  reservaId: string | null;
  quartoId: string | null;
  nomeHospede: string | null;
  token: string | null;
  jti: string | null;
  portas: string[];
  validoAte: string | null;
  chaveEncerrada: boolean;
}

const VAZIA: DadosSessao = {
  reservaId: null,
  quartoId: null,
  nomeHospede: null,
  token: null,
  jti: null,
  portas: [],
  validoAte: null,
  chaveEncerrada: false,
};

const CHAVE_STORAGE = 'ns.sessao';

/**
 * Sessão do hóspede neste navegador. Fica no sessionStorage (some ao fechar a aba).
 * O token é a própria chave digital (JWT) e vai no Authorization das rotas protegidas.
 */
@Injectable({ providedIn: 'root' })
export class SessaoStore {
  private readonly dados = signal<DadosSessao>(lerStorage());

  readonly reservaId = computed(() => this.dados().reservaId);
  readonly quartoId = computed(() => this.dados().quartoId);
  readonly nomeHospede = computed(() => this.dados().nomeHospede);
  readonly token = computed(() => this.dados().token);
  readonly jti = computed(() => this.dados().jti);
  readonly portas = computed(() => this.dados().portas);
  readonly validoAte = computed(() => this.dados().validoAte);
  readonly chaveEncerrada = computed(() => this.dados().chaveEncerrada);

  /** Check-in aceito (202): a estadia começa, a chave chega depois pelo socket. */
  readonly checkinFeito = computed(() => this.dados().reservaId !== null);
  readonly temChave = computed(() => this.dados().token !== null && !this.dados().chaveEncerrada);

  iniciarEstadia(reservaId: string, quartoId: string, nomeHospede: string): void {
    this.gravar({ ...VAZIA, reservaId, quartoId, nomeHospede });
  }

  definirChave(msg: ChaveGerada): void {
    this.gravar({
      ...this.dados(),
      token: msg.token,
      jti: msg.jti,
      portas: msg.portas,
      validoAte: msg.validoAte,
      chaveEncerrada: false,
    });
  }

  encerrarChave(jti: string): void {
    if (this.dados().jti !== jti) return;
    this.gravar({ ...this.dados(), chaveEncerrada: true });
  }

  limpar(): void {
    this.gravar(VAZIA);
  }

  private gravar(dados: DadosSessao): void {
    this.dados.set(dados);
    try {
      if (dados.reservaId === null) sessionStorage.removeItem(CHAVE_STORAGE);
      else sessionStorage.setItem(CHAVE_STORAGE, JSON.stringify(dados));
    } catch {
      // sem sessionStorage: a sessão vale só enquanto a página estiver aberta
    }
  }
}

function lerStorage(): DadosSessao {
  try {
    const bruto = sessionStorage.getItem(CHAVE_STORAGE);
    return bruto ? { ...VAZIA, ...(JSON.parse(bruto) as Partial<DadosSessao>) } : VAZIA;
  } catch {
    return VAZIA;
  }
}
