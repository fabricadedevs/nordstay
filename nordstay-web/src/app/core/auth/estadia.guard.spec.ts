import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { estadiaGuard } from './estadia.guard';
import { SessaoStore } from './sessao.store';

describe('estadiaGuard', () => {
  const rota = (reservaId: string) =>
    ({ paramMap: convertToParamMap({ reservaId }) }) as ActivatedRouteSnapshot;
  const rodar = (reservaId: string) =>
    TestBed.runInInjectionContext(() => estadiaGuard(rota(reservaId), {} as RouterStateSnapshot));

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('sem check-in manda para /checkin', () => {
    const r = rodar('RSV-20261120-0042');
    expect(r).toBeInstanceOf(UrlTree);
    expect(String(r)).toBe('/checkin');
  });

  it('com check-in da mesma reserva libera, mesmo antes da chave chegar', () => {
    TestBed.inject(SessaoStore).iniciarEstadia('RSV-20261120-0042', 'Q-214', 'Marina');
    expect(rodar('RSV-20261120-0042')).toBe(true);
  });

  it('reserva diferente da sessão manda para /checkin', () => {
    TestBed.inject(SessaoStore).iniciarEstadia('RSV-20261120-0042', 'Q-214', 'Marina');
    expect(String(rodar('RSV-20261120-0099'))).toBe('/checkin');
  });
});
