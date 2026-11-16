import { TestBed } from '@angular/core/testing';
import { MensagemEstadia } from '../../shared/models/nordstay.models';
import { RESERVA_ID, TOKEN_MOCK } from './fixtures';
import { ConexaoEstadiaPerdida, MockEstadiaSocket } from './mock-estadia-socket';
import { MockStore } from './mock-store';

describe('MockEstadiaSocket', () => {
  let socket: MockEstadiaSocket;
  let store: MockStore;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [MockEstadiaSocket] });
    socket = TestBed.inject(MockEstadiaSocket);
    store = TestBed.inject(MockStore);
  });

  afterEach(() => {
    store.reiniciar();
    vi.useRealTimers();
  });

  it('repassa os eventos do store na ordem e manda PING a cada 25 s', () => {
    const recebidas: MensagemEstadia['tipo'][] = [];
    const sub = socket.conectar(RESERVA_ID, TOKEN_MOCK).subscribe((m) => recebidas.push(m.tipo));
    store.fazerCheckin('feliz');
    vi.advanceTimersByTime(2_000);
    store.solicitarCheckout('feliz');
    vi.advanceTimersByTime(3_000);
    vi.advanceTimersByTime(20_000);
    expect(recebidas).toEqual(['CHAVE_GERADA', 'FATURA_FECHADA', 'CHAVE_REVOGADA', 'PING']);
    sub.unsubscribe();
  });

  it('token de outra reserva falha no handshake (401)', () => {
    let erro: ConexaoEstadiaPerdida | undefined;
    socket.conectar(RESERVA_ID, 'outro').subscribe({ error: (e) => (erro = e) });
    expect(erro?.codigo).toBe(401);
  });

  it('derrubarConexao encerra o fluxo com erro 1006', () => {
    let erro: ConexaoEstadiaPerdida | undefined;
    socket.conectar(RESERVA_ID, TOKEN_MOCK).subscribe({ error: (e) => (erro = e) });
    socket.derrubarConexao();
    expect(erro?.codigo).toBe(1006);
  });
});
