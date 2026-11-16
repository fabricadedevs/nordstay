import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessaoStore } from '../auth/sessao.store';
import { ErroApi } from './erro-api';

/**
 * Converte qualquer erro HTTP em ErroApi (mensagem em português a partir do Problem Details).
 * 401 numa rota protegida: limpa a sessão e volta para /checkin.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const sessao = inject(SessaoStore);
  const router = inject(Router);
  return next(req).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse)) return throwError(() => erro);
      if (erro.status === 401) {
        sessao.limpar();
        void router.navigate(['/checkin']);
      }
      return throwError(() => ErroApi.de(erro));
    }),
  );
};
