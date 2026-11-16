import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessaoStore } from '../auth/sessao.store';

/** Rotas que exigem a chave digital (resource server). As demais são públicas. */
const PROTEGIDAS: readonly RegExp[] = [
  /^\/api\/reservas\/[^/]+\/checkout$/,
  /^\/api\/consumos$/,
  /^\/api\/faturas\/[^/]+$/,
];

/**
 * Coloca Authorization: Bearer <chave> só nas rotas protegidas.
 * Mandar o header numa rota pública com token inválido faria o Spring devolver 401.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(SessaoStore).token();
  if (!token || req.headers.has('Authorization') || !PROTEGIDAS.some((p) => p.test(req.url))) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
