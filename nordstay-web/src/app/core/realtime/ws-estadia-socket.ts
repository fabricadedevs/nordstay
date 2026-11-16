import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { webSocket } from 'rxjs/webSocket';
import { MensagemEstadia } from '../../shared/models/nordstay.models';
import { EstadiaSocket } from './estadia-socket';

/**
 * WebSocket real do billing-service (via Nginx em /ws).
 * Reconexão com backoff e recarga do snapshot ficam para a NS-22.
 */
@Injectable()
export class WsEstadiaSocket extends EstadiaSocket {
  conectar(reservaId: string, token: string): Observable<MensagemEstadia> {
    const protocolo = location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${protocolo}://${location.host}/ws/estadias/${encodeURIComponent(reservaId)}?token=${encodeURIComponent(token)}`;
    return webSocket<MensagemEstadia>(url).asObservable();
  }
}
