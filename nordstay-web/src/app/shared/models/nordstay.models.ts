// Contrato da API do NordStay (docs/NordStay_Endpoints_e_Assinaturas.md).
// Mesmos nomes de campo dos records Java. Valores em dinheiro sempre em centavos.

export type StatusReserva =
  'CONFIRMADA' | 'CHECKED_IN' | 'CHECKOUT_SOLICITADO' | 'CHECKED_OUT' | 'NO_SHOW';

export interface HospedeResumo {
  nome: string;
  documentoMascarado: string;
}

export interface ReservaResponse {
  reservaId: string;
  quartoId: string;
  status: StatusReserva;
  hospede: HospedeResumo;
  entrada: string; // yyyy-MM-dd
  saida: string; // yyyy-MM-dd
  diariasCentavos: number;
}

export interface CheckinRequest {
  acompanhantes: number;
  aceiteTermos: boolean;
  previsaoChegada?: string; // HH:mm
}

export interface CheckinAceitoResponse {
  reservaId: string;
  quartoId: string;
  status: 'CHECKIN_EM_PROCESSAMENTO';
  correlationId: string;
  websocket: string;
}

export interface CheckoutAceitoResponse {
  reservaId: string;
  status: 'CHECKOUT_SOLICITADO';
  correlationId: string;
}

export type Categoria = 'FRIGOBAR' | 'PETISCO' | 'EXPERIENCIA';

export interface ImagemResponse {
  objectKey: string;
  presignedUrl: string;
  expiraEm: string;
}

export interface ItemCatalogoResponse {
  sku: string;
  nome: string;
  categoria: Categoria;
  precoCentavos: number;
  exigeAgendamento: boolean;
  imagem: ImagemResponse;
}

export interface ItemPedido {
  sku: string;
  quantidade: number;
  agendadoPara?: string; // ISO-8601 com fuso, obrigatório só para EXPERIENCIA
}

export interface RegistrarConsumoRequest {
  reservaId: string;
  itens: ItemPedido[];
}

export interface ConsumoAceitoResponse {
  eventId: string;
  status: 'PENDENTE';
  totalCentavos: number;
  correlationId: string;
}

export type StatusFatura = 'ABERTA' | 'FECHANDO' | 'FECHADA';

export interface ItemFaturaResponse {
  sku: string;
  nome: string;
  quantidade: number;
  subtotalCentavos: number;
}

export interface FaturaResponse {
  reservaId: string;
  status: StatusFatura;
  versao: number;
  pendentes: number;
  diariasCentavos: number;
  consumoCentavos: number;
  totalCentavos: number;
  itens: ItemFaturaResponse[];
}

/** RFC 9457 — corpo de todo erro da API (Content-Type: application/problem+json). */
export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail: string;
  correlationId?: string;
}

/** Mensagens do WebSocket /ws/estadias/{reservaId} (servidor → app). */
export type MensagemEstadia =
  | { tipo: 'CHAVE_GERADA'; jti: string; token: string; portas: string[]; validoAte: string }
  | { tipo: 'FATURA_ATUALIZADA'; fatura: FaturaResponse }
  | { tipo: 'FATURA_FECHADA'; fatura: FaturaResponse }
  | { tipo: 'CHAVE_REVOGADA'; jti: string }
  | { tipo: 'PING' };
