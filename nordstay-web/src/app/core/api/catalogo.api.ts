import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Categoria, ItemCatalogoResponse } from '../../shared/models/nordstay.models';

/** billing-service · /api/catalogo (público) */
@Injectable({ providedIn: 'root' })
export class CatalogoApi {
  private readonly http = inject(HttpClient);

  listar(categoria?: Categoria): Observable<ItemCatalogoResponse[]> {
    const params = categoria ? new HttpParams().set('categoria', categoria) : undefined;
    return this.http.get<ItemCatalogoResponse[]>('/api/catalogo', { params });
  }
}
