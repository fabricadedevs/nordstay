import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ConsumoAceitoResponse,
  RegistrarConsumoRequest,
} from '../../shared/models/nordstay.models';
import { cabecalhos } from './reserva.api';

/** billing-service · /api/consumos (exige a chave) */
@Injectable({ providedIn: 'root' })
export class ConsumoApi {
  private readonly http = inject(HttpClient);

  registrar(
    corpo: RegistrarConsumoRequest,
    idempotencyKey?: string,
  ): Observable<ConsumoAceitoResponse> {
    return this.http.post<ConsumoAceitoResponse>('/api/consumos', corpo, {
      headers: cabecalhos({}, idempotencyKey),
    });
  }
}
