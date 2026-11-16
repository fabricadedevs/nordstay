import { Routes } from '@angular/router';
import { estadiaGuard } from './core/auth/estadia.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'checkin' },
  {
    path: 'checkin',
    title: 'NordStay · Check-in',
    loadComponent: () => import('./features/checkin/checkin.page').then((m) => m.CheckinPage),
  },
  {
    path: 'estadia/:reservaId',
    canActivate: [estadiaGuard],
    loadComponent: () =>
      import('./features/estadia/estadia-shell.component').then((m) => m.EstadiaShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'chave' },
      {
        path: 'chave',
        title: 'NordStay · Sua chave',
        loadComponent: () => import('./features/chave/chave.page').then((m) => m.ChavePage),
      },
      {
        path: 'catalogo',
        title: 'NordStay · Catálogo',
        loadComponent: () =>
          import('./features/catalogo/catalogo.page').then((m) => m.CatalogoPage),
      },
      {
        path: 'fatura',
        title: 'NordStay · Sua conta',
        loadComponent: () => import('./features/fatura/fatura.page').then((m) => m.FaturaPage),
      },
      {
        path: 'checkout',
        title: 'NordStay · Checkout',
        loadComponent: () =>
          import('./features/checkout/checkout.page').then((m) => m.CheckoutPage),
      },
    ],
  },
  { path: '**', redirectTo: 'checkin' },
];
