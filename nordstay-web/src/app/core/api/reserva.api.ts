import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CheckinAceitoResponse,
  CheckinRequest,
  CheckoutAceitoResponse,
  ReservaResponse,
} from '../../shared/models/nordstay.models';

/** reservation-service · /api/reservas */
@Injectable({ providedIn: 'root' })
export class ReservaApi {
  private readonly http = inject(HttpClient);

  buscar(reservaId: string, documentoFinal: string): Observable<ReservaResponse> {
    return this.http.get<ReservaResponse>(`/api/reservas/${encodeURIComponent(reservaId)}`, {
      headers: { 'X-Documento-Final': documentoFinal },
    });
  }

  /** idempotencyKey: passe a mesma ao tentar de novo depois de uma falha. */
  fazerCheckin(
    reservaId: string,
    documentoFinal: string,
    corpo: CheckinRequest,
    idempotencyKey?: string,
  ): Observable<CheckinAceitoResponse> {
    return this.http.post<CheckinAceitoResponse>(
      `/api/reservas/${encodeURIComponent(reservaId)}/checkin`,
      corpo,
      { headers: cabecalhos({ 'X-Documento-Final': documentoFinal }, idempotencyKey) },
    );
  }

  solicitarCheckout(
    reservaId: string,
    idempotencyKey?: string,
  ): Observable<CheckoutAceitoResponse> {
    return this.http.post<CheckoutAceitoResponse>(
      `/api/reservas/${encodeURIComponent(reservaId)}/checkout`,
      null,
      { headers: cabecalhos({}, idempotencyKey) },
    );
  }
}

export function cabecalhos(base: Record<string, string>, idempotencyKey?: string): HttpHeaders {
  return new HttpHeaders(idempotencyKey ? { ...base, 'Idempotency-Key': idempotencyKey } : base);
}
