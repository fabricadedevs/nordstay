import { Injectable, inject } from '@angular/core';
import { Observable, Subject, interval, map, merge, switchMap, throwError } from 'rxjs';
import { MensagemEstadia } from '../../shared/models/nordstay.models';
import { EstadiaSocket } from '../realtime/estadia-socket';
import { RESERVA_ID, TOKEN_MOCK } from './fixtures';
import { MockStore } from './mock-store';

export const INTERVALO_PING_MS = 25_000;

/** Erro de conexão do socket simulado (mesmo papel do close do WebSocket real). */
export class ConexaoEstadiaPerdida extends Error {
  constructor(
    readonly codigo: 401 | 1006,
    mensagem: string,
  ) {
    super(mensagem);
    this.name = 'ConexaoEstadiaPerdida';
  }
}

/**
 * WebSocket simulado: repassa os eventos do MockStore (CHAVE_GERADA, FATURA_ATUALIZADA,
 * FATURA_FECHADA, CHAVE_REVOGADA) e manda PING a cada 25 s.
 * Quem consome é que decide reconectar (retry), como fará com o socket real.
 */
@Injectable()
export class MockEstadiaSocket extends EstadiaSocket {
  private readonly store = inject(MockStore);
  private readonly quedas = new Subject<void>();

  conectar(reservaId: string, token: string): Observable<MensagemEstadia> {
    if (reservaId !== RESERVA_ID || token !== TOKEN_MOCK) {
      return throwError(
        () => new ConexaoEstadiaPerdida(401, 'Handshake recusado: o token não é desta reserva'),
      );
    }
    return merge(
      this.store.eventos$,
      interval(INTERVALO_PING_MS).pipe(map((): MensagemEstadia => ({ tipo: 'PING' }))),
      this.quedas.pipe(
        switchMap(() => throwError(() => new ConexaoEstadiaPerdida(1006, 'Conexão perdida'))),
      ),
    );
  }

  /** Botão "Derrubar conexão" do painel de cenários. */
  derrubarConexao(): void {
    this.quedas.next();
  }
}
