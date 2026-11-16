import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Todo POST sai com Idempotency-Key. Se a chamada já trouxer uma (o serviço de API recebe
 * a chave da tela), ela é mantida: assim "Tentar de novo" reenvia a mesma chave e o backend
 * não processa o pedido duas vezes.
 */
export const idempotencyInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method !== 'POST' || req.headers.has('Idempotency-Key')) return next(req);
  return next(req.clone({ setHeaders: { 'Idempotency-Key': crypto.randomUUID() } }));
};
