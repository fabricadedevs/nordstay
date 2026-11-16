import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { environment } from '../environments/environment';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { idempotencyInterceptor } from './core/interceptors/idempotency.interceptor';
import { MockEstadiaSocket } from './core/mocks/mock-estadia-socket';
import { mockApiInterceptor } from './core/mocks/mock-api.interceptor';
import { EstadiaSocket } from './core/realtime/estadia-socket';
import { WsEstadiaSocket } from './core/realtime/ws-estadia-socket';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // ordem importa: o mock é o último porque faz o papel do backend
    provideHttpClient(
      withInterceptors([
        authInterceptor,
        idempotencyInterceptor,
        errorInterceptor,
        mockApiInterceptor,
      ]),
    ),
    {
      provide: EstadiaSocket,
      useClass: environment.useMocks ? MockEstadiaSocket : WsEstadiaSocket,
    },
  ],
};
