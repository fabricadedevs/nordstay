import { TestBed } from '@angular/core/testing';
import { SessaoStore } from './sessao.store';

describe('SessaoStore', () => {
  beforeEach(() => sessionStorage.clear());

  it('guarda a sessão no sessionStorage e encerra a chave pelo jti', () => {
    const s = TestBed.inject(SessaoStore);
    s.iniciarEstadia('RSV-1', 'Q-214', 'Marina');
    s.definirChave({
      tipo: 'CHAVE_GERADA',
      jti: 'k-1',
      token: 'tok',
      portas: ['Q-214'],
      validoAte: '2026-11-23T12:00:00-03:00',
    });
    expect(JSON.parse(sessionStorage.getItem('ns.sessao')!).token).toBe('tok');
    expect(s.temChave()).toBe(true);

    s.encerrarChave('outro');
    expect(s.temChave()).toBe(true);
    s.encerrarChave('k-1');
    expect(s.temChave()).toBe(false);

    s.limpar();
    expect(sessionStorage.getItem('ns.sessao')).toBeNull();
  });
});
