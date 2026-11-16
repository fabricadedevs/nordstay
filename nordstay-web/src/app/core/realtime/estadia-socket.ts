import { Observable } from 'rxjs';
import { MensagemEstadia } from '../../shared/models/nordstay.models';

/**
 * Canal de tempo real da estadia (WS /ws/estadias/{reservaId}?token=).
 * As telas só conhecem esta classe; o app.config escolhe a implementação:
 * MockEstadiaSocket (useMocks = true) ou WsEstadiaSocket (backend real).
 */
export abstract class EstadiaSocket {
  abstract conectar(reservaId: string, token: string): Observable<MensagemEstadia>;
}
