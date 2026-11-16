import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessaoStore } from './sessao.store';

/**
 * Protege /estadia/:reservaId/**. Exige check-in feito neste navegador e da mesma reserva da URL.
 * Não exige o token ainda: logo após o check-in a tela da chave mostra "Gerando sua chave…"
 * enquanto a CHAVE_GERADA não chega.
 */
export const estadiaGuard: CanActivateFn = (route) => {
  const sessao = inject(SessaoStore);
  const router = inject(Router);
  const reservaDaUrl = route.paramMap.get('reservaId');
  if (sessao.checkinFeito() && sessao.reservaId() === reservaDaUrl) return true;
  return router.createUrlTree(['/checkin']);
};
