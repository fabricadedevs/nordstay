import {
  HttpErrorResponse,
  HttpEvent,
  HttpHeaders,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Observable, defer, of, switchMap, throwError, timer } from 'rxjs';
import {
  Categoria,
  CheckinAceitoResponse,
  CheckinRequest,
  CheckoutAceitoResponse,
  ConsumoAceitoResponse,
  ItemCatalogoResponse,
  ItemFaturaResponse,
  ProblemDetails,
  RegistrarConsumoRequest,
} from '../../shared/models/nordstay.models';
import { Cenario } from './cenarios';
import { CATALOGO, DOCUMENTO_FINAL, RESERVA_ID, TOKEN_MOCK, dataIso, somarDias } from './fixtures';
import { MockStore } from './mock-store';

/**
 * Rotas do backend falso. Cada rota imita um endpoint do contrato:
 * mesmo formato de resposta, mesmos códigos HTTP e erros em Problem Details.
 *
 * Uso no mockApiInterceptor:
 *   const rota = ROTAS_MOCK.find(r => r.metodo === req.method && r.padrao.test(req.url));
 *   return rota ? rota.responder(req, inject(MockStore), cenarioAtual()) : next(req);
 *
 * A latência já vem embutida (sucesso e erro). Não use .pipe(delay()) no interceptor:
 * o operador delay não atrasa erros.
 */
export interface RotaMock {
  metodo: 'GET' | 'POST';
  padrao: RegExp;
  descricao: string;
  responder(
    req: HttpRequest<unknown>,
    store: MockStore,
    cenario: Cenario,
  ): Observable<HttpEvent<unknown>>;
}

type Resposta = HttpResponse<unknown> | HttpErrorResponse;
type Tratador = (
  req: HttpRequest<unknown>,
  store: MockStore,
  cenario: Cenario,
  params: string[],
) => Resposta;

const RE_RESERVA = /^RSV-\d{8}-\d{4}$/;
const CATEGORIAS: readonly Categoria[] = ['FRIGOBAR', 'PETISCO', 'EXPERIENCIA'];

// ---------------------------------------------------------------- helpers de resposta

export function ok<T>(
  req: HttpRequest<unknown>,
  body: T,
  status = 200,
  headers: Record<string, string> = {},
): HttpResponse<T> {
  return new HttpResponse<T>({
    body,
    status,
    statusText: status === 202 ? 'Accepted' : 'OK',
    url: req.url,
    headers: new HttpHeaders({ 'Content-Type': 'application/json', ...headers }),
  });
}

export function problema(
  req: HttpRequest<unknown>,
  status: number,
  tipo: string,
  titulo: string,
  detalhe: string,
): HttpErrorResponse {
  const corpo: ProblemDetails = {
    type: `/erros/${tipo}`,
    title: titulo,
    status,
    detail: detalhe,
    correlationId: crypto.randomUUID(),
  };
  return new HttpErrorResponse({
    error: corpo,
    status,
    statusText: titulo,
    url: req.url,
    headers: new HttpHeaders({ 'Content-Type': 'application/problem+json' }),
  });
}

function semRede(req: HttpRequest<unknown>): HttpErrorResponse {
  return new HttpErrorResponse({
    error: null,
    status: 0,
    statusText: 'Unknown Error',
    url: req.url,
  });
}

function latencia(cenario: Cenario): number {
  if (cenario === 'sem-rede') return 400;
  if (cenario === 'lento') return 6_000 + Math.round(Math.random() * 4_000);
  return 300 + Math.round(Math.random() * 500);
}

/**
 * Monta a rota: aplica o cenário sem-rede, a idempotência e a latência.
 * O estado muda na hora em que a requisição chega (como num servidor); a resposta sai depois da latência.
 */
function rota(
  metodo: RotaMock['metodo'],
  padrao: RegExp,
  descricao: string,
  tratar: Tratador,
  idempotente = false,
): RotaMock {
  return {
    metodo,
    padrao,
    descricao,
    responder: (req, store, cenario) =>
      defer(() => {
        const resposta =
          cenario === 'sem-rede'
            ? semRede(req)
            : executar(req, store, cenario, padrao, tratar, idempotente);
        return timer(latencia(cenario)).pipe(
          switchMap(() =>
            resposta instanceof HttpErrorResponse ? throwError(() => resposta) : of(resposta),
          ),
        );
      }),
  };
}

function executar(
  req: HttpRequest<unknown>,
  store: MockStore,
  cenario: Cenario,
  padrao: RegExp,
  tratar: Tratador,
  idempotente: boolean,
): Resposta {
  const params = padrao.exec(req.url)?.slice(1) ?? [];
  if (!idempotente) return tratar(req, store, cenario, params);

  const chave = req.headers.get('Idempotency-Key');
  if (!chave)
    return problema(
      req,
      400,
      'idempotency-key-ausente',
      'Requisição inválida',
      'O header Idempotency-Key é obrigatório',
    );

  const id = `${req.method} ${req.url} ${chave}`;
  const salva = store.respostaSalva(id);
  if (salva) return salva;

  const resposta = tratar(req, store, cenario, params);
  if (resposta instanceof HttpResponse) store.salvarResposta(id, resposta);
  return resposta;
}

// ---------------------------------------------------------------- validações comuns

function naoEncontrada(req: HttpRequest<unknown>): HttpErrorResponse {
  // mesma mensagem para código ou documento errado: não revela qual dos dois
  return problema(
    req,
    404,
    'reserva-nao-encontrada',
    'Reserva não encontrada',
    'Confira o código da reserva e os 4 últimos dígitos do documento',
  );
}

function conferirDocumento(
  req: HttpRequest<unknown>,
  cenario: Cenario,
  reservaId: string,
): HttpErrorResponse | null {
  if (!RE_RESERVA.test(reservaId)) {
    return problema(
      req,
      400,
      'requisicao-invalida',
      'Código inválido',
      'Use o formato RSV-AAAAMMDD-NNNN',
    );
  }
  const documento = req.headers.get('X-Documento-Final') ?? '';
  if (!/^\d{4}$/.test(documento)) {
    return problema(
      req,
      400,
      'requisicao-invalida',
      'Documento inválido',
      'Informe os 4 últimos dígitos do documento',
    );
  }
  if (
    cenario === 'reserva-inexistente' ||
    reservaId !== RESERVA_ID ||
    documento !== DOCUMENTO_FINAL
  ) {
    return naoEncontrada(req);
  }
  return null;
}

/** Igual ao resource server: exige a chave JWT (aqui, o token falso do mock). */
function autenticar(req: HttpRequest<unknown>): HttpErrorResponse | null {
  if (req.headers.get('Authorization') !== `Bearer ${TOKEN_MOCK}`) {
    return problema(
      req,
      401,
      'nao-autenticado',
      'Não autenticado',
      'Faça o check-in de novo para receber a chave digital',
    );
  }
  return null;
}

/** O sub da chave é a reserva; pedir dados de outra reserva dá 403. */
function mesmaReserva(req: HttpRequest<unknown>, reservaId: string): HttpErrorResponse | null {
  if (reservaId !== RESERVA_ID) {
    return problema(
      req,
      403,
      'reserva-de-outra-chave',
      'Acesso negado',
      'Esta chave não pertence a essa reserva',
    );
  }
  return null;
}

function dataBr(isoDia: string): string {
  const [a, m, d] = isoDia.split('-');
  return `${d}/${m}/${a}`;
}

function validarCheckin(corpo: Partial<CheckinRequest> | null): string | null {
  if (!corpo) return 'Corpo da requisição ausente';
  if (
    !Number.isInteger(corpo.acompanhantes) ||
    corpo.acompanhantes! < 0 ||
    corpo.acompanhantes! > 6
  ) {
    return 'acompanhantes deve ser um número de 0 a 6';
  }
  if (corpo.aceiteTermos !== true) return 'É preciso aceitar os termos da estadia';
  if (corpo.previsaoChegada != null && !/^\d{2}:\d{2}$/.test(corpo.previsaoChegada)) {
    return 'previsaoChegada deve estar no formato HH:mm';
  }
  return null;
}

function validarPedido(corpo: Partial<RegistrarConsumoRequest> | null): string | null {
  if (!corpo || typeof corpo.reservaId !== 'string' || !corpo.reservaId)
    return 'reservaId é obrigatório';
  if (!Array.isArray(corpo.itens) || corpo.itens.length < 1 || corpo.itens.length > 20)
    return 'O pedido deve ter de 1 a 20 itens';
  for (const item of corpo.itens) {
    if (!item || typeof item.sku !== 'string' || !item.sku) return 'Todo item precisa de sku';
    if (!Number.isInteger(item.quantidade) || item.quantidade < 1 || item.quantidade > 20) {
      return `Quantidade de ${item.sku} deve ser de 1 a 20`;
    }
  }
  return null;
}

function itemCatalogo(fixture: (typeof CATALOGO)[number]): ItemCatalogoResponse {
  const { arquivo, ...item } = fixture;
  return {
    ...item,
    imagem: {
      objectKey: `catalogo/${arquivo}`,
      presignedUrl: `/mock/catalogo/${arquivo}`,
      expiraEm: new Date(Date.now() + 5 * 60_000).toISOString(),
    },
  };
}

// ---------------------------------------------------------------- rotas

export const ROTAS_MOCK: readonly RotaMock[] = [
  // GET /api/reservas/{reservaId} · reservation-service · NS-03
  rota(
    'GET',
    /^\/api\/reservas\/([^/?]+)$/,
    'Consultar reserva',
    (req, store, cenario, [reservaId]) => {
      const erro = conferirDocumento(req, cenario, reservaId);
      return erro ?? ok(req, store.reserva());
    },
  ),

  // POST /api/reservas/{reservaId}/checkin · reservation-service · NS-04
  rota(
    'POST',
    /^\/api\/reservas\/([^/?]+)\/checkin$/,
    'Fazer check-in',
    (req, store, cenario, [reservaId]) => {
      const erroDocumento = conferirDocumento(req, cenario, reservaId);
      if (erroDocumento) return erroDocumento;

      const erroCorpo = validarCheckin(req.body as Partial<CheckinRequest> | null);
      if (erroCorpo)
        return problema(req, 400, 'requisicao-invalida', 'Requisição inválida', erroCorpo);

      const reserva = store.reserva();
      if (reserva.status !== 'CONFIRMADA') {
        return problema(
          req,
          409,
          'checkin-ja-realizado',
          'Check-in não permitido',
          `A reserva ${reservaId} está ${reserva.status}`,
        );
      }
      if (cenario === 'fora-da-data') {
        const amanha = dataIso(somarDias(new Date(), 1));
        return problema(
          req,
          422,
          'fora-da-data-de-entrada',
          'Check-in fora da data',
          `O check-in de ${reservaId} só pode ser feito em ${dataBr(amanha)}`,
        );
      }

      store.fazerCheckin(cenario);
      const corpo: CheckinAceitoResponse = {
        reservaId,
        quartoId: reserva.quartoId,
        status: 'CHECKIN_EM_PROCESSAMENTO',
        correlationId: crypto.randomUUID(),
        websocket: `/ws/estadias/${reservaId}`,
      };
      return ok(req, corpo, 202, { Location: `/api/reservas/${reservaId}` });
    },
    true,
  ),

  // POST /api/reservas/{reservaId}/checkout · reservation-service · NS-05
  rota(
    'POST',
    /^\/api\/reservas\/([^/?]+)\/checkout$/,
    'Solicitar checkout',
    (req, store, cenario, [reservaId]) => {
      const erro = autenticar(req) ?? mesmaReserva(req, reservaId);
      if (erro) return erro;

      const status = store.reserva().status;
      if (status !== 'CHECKED_IN') {
        return problema(
          req,
          409,
          'checkout-nao-permitido',
          'Checkout não permitido',
          `A reserva ${reservaId} está ${status}`,
        );
      }

      store.solicitarCheckout(cenario);
      const corpo: CheckoutAceitoResponse = {
        reservaId,
        status: 'CHECKOUT_SOLICITADO',
        correlationId: crypto.randomUUID(),
      };
      return ok(req, corpo, 202);
    },
    true,
  ),

  // GET /api/catalogo?categoria= · billing-service · NS-11 (público)
  rota('GET', /^\/api\/catalogo(?:\?.*)?$/, 'Listar catálogo', (req) => {
    const categoria =
      req.params.get('categoria') ??
      new URL(req.urlWithParams, 'http://mock').searchParams.get('categoria');
    if (categoria && !CATEGORIAS.includes(categoria as Categoria)) {
      return problema(
        req,
        400,
        'requisicao-invalida',
        'Categoria inválida',
        'Use FRIGOBAR, PETISCO ou EXPERIENCIA',
      );
    }
    const itens = CATALOGO.filter((i) => !categoria || i.categoria === categoria).map(itemCatalogo);
    return ok(req, itens);
  }),

  // POST /api/consumos · billing-service · NS-12
  rota(
    'POST',
    /^\/api\/consumos$/,
    'Registrar consumo',
    (req, store, cenario) => {
      const erroAuth = autenticar(req);
      if (erroAuth) return erroAuth;

      const corpo = req.body as Partial<RegistrarConsumoRequest> | null;
      const erroCorpo = validarPedido(corpo);
      if (erroCorpo)
        return problema(req, 400, 'requisicao-invalida', 'Requisição inválida', erroCorpo);
      const pedido = corpo as RegistrarConsumoRequest;

      const erroReserva = mesmaReserva(req, pedido.reservaId);
      if (erroReserva) return erroReserva;

      const fatura = store.fatura();
      if (cenario === 'fatura-fechando' || fatura.status !== 'ABERTA') {
        const status = fatura.status === 'ABERTA' ? 'FECHANDO' : fatura.status;
        return problema(
          req,
          409,
          'fatura-fechada',
          'Fatura não aceita consumo',
          `A fatura de ${pedido.reservaId} está ${status}`,
        );
      }

      const fimDaEstadia = new Date(`${store.reserva().saida}T12:00:00-03:00`).getTime();
      const linhas: ItemFaturaResponse[] = [];
      for (const item of pedido.itens) {
        const produto = CATALOGO.find((p) => p.sku === item.sku);
        if (!produto) {
          return problema(
            req,
            422,
            'sku-inexistente',
            'Item indisponível',
            `O item ${item.sku} não está mais disponível`,
          );
        }
        if (produto.exigeAgendamento) {
          if (!item.agendadoPara) {
            return problema(
              req,
              400,
              'requisicao-invalida',
              'Requisição inválida',
              `${produto.nome} precisa de data e hora`,
            );
          }
          const quando = new Date(item.agendadoPara).getTime();
          if (Number.isNaN(quando) || quando <= Date.now() || quando > fimDaEstadia) {
            return problema(
              req,
              422,
              'agendamento-fora-da-estadia',
              'Agendamento inválido',
              `Escolha um horário entre agora e o checkout para ${produto.nome}`,
            );
          }
        }
        linhas.push({
          sku: produto.sku,
          nome: produto.nome,
          quantidade: item.quantidade,
          subtotalCentavos: produto.precoCentavos * item.quantidade,
        });
      }

      store.registrarConsumo(linhas, cenario);
      const resposta: ConsumoAceitoResponse = {
        eventId: crypto.randomUUID(),
        status: 'PENDENTE',
        totalCentavos: linhas.reduce((t, l) => t + l.subtotalCentavos, 0),
        correlationId: crypto.randomUUID(),
      };
      return ok(req, resposta, 202);
    },
    true,
  ),

  // GET /api/faturas/{reservaId} · billing-service · NS-14
  rota(
    'GET',
    /^\/api\/faturas\/([^/?]+)$/,
    'Consultar fatura',
    (req, store, _cenario, [reservaId]) => {
      const erro = autenticar(req) ?? mesmaReserva(req, reservaId);
      if (erro) return erro;
      if (store.reserva().status === 'CONFIRMADA') {
        return problema(
          req,
          404,
          'fatura-nao-encontrada',
          'Fatura não encontrada',
          'A fatura é aberta no check-in',
        );
      }
      return ok(req, store.fatura());
    },
  ),
];
