import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FaturaResponse } from '../../shared/models/nordstay.models';

/** billing-service · /api/faturas (exige a chave) */
@Injectable({ providedIn: 'root' })
export class FaturaApi {
  private readonly http = inject(HttpClient);

  buscar(reservaId: string): Observable<FaturaResponse> {
    return this.http.get<FaturaResponse>(`/api/faturas/${encodeURIComponent(reservaId)}`);
  }
}
