import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { cenarioAtual } from './cenarios';
import { MockStore } from './mock-store';
import { ROTAS_MOCK } from './rotas';

/**
 * Faz o papel do backend quando environment.useMocks = true.
 * Tem que ser o ÚLTIMO interceptor da lista: os outros (auth, idempotência, erro)
 * precisam agir antes, como agiriam numa chamada real.
 */
export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  if (!environment.useMocks) return next(req);
  const rota = ROTAS_MOCK.find((r) => r.metodo === req.method && r.padrao.test(req.url));
  return rota ? rota.responder(req, inject(MockStore), cenarioAtual()) : next(req);
};
