import {
  HttpErrorResponse,
  HttpEvent,
  HttpHeaders,
  HttpParams,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  FaturaResponse,
  MensagemEstadia,
  ProblemDetails,
} from '../../shared/models/nordstay.models';
import { Cenario } from './cenarios';
import { RESERVA_ID, TOKEN_MOCK } from './fixtures';
import { MockStore } from './mock-store';
import { ROTAS_MOCK } from './rotas';

type Resultado = { resposta?: HttpResponse<any>; erro?: HttpErrorResponse };

describe('ROTAS_MOCK', () => {
  let store: MockStore;
  let mensagens: MensagemEstadia[];

  beforeEach(() => {
    vi.useFakeTimers();
    store = new MockStore();
    mensagens = [];
    store.eventos$.subscribe((m) => mensagens.push(m));
  });

  afterEach(() => {
    store.reiniciar();
    vi.useRealTimers();
  });

  function chamar(req: HttpRequest<unknown>, cenario: Cenario = 'feliz'): Resultado {
    const rota = ROTAS_MOCK.find((r) => r.metodo === req.method && r.padrao.test(req.url));
    if (!rota) throw new Error(`sem rota mock para ${req.method} ${req.url}`);
    const r: Resultado = {};
    rota.responder(req, store, cenario).subscribe({
      next: (e: HttpEvent<unknown>) => (r.resposta = e as HttpResponse<any>),
      error: (e: HttpErrorResponse) => (r.erro = e),
    });
    vi.advanceTimersByTime(800);
    return r;
  }

  const headers = (h: Record<string, string>) => new HttpHeaders(h);
  const buscar = (doc = '9132', id = RESERVA_ID) =>
    new HttpRequest('GET', `/api/reservas/${id}`, {
      headers: headers({ 'X-Documento-Final': doc }),
    });
  const checkin = (
    chave = 'k-1',
    corpo: object = { acompanhantes: 1, aceiteTermos: true, previsaoChegada: '15:00' },
  ) =>
    new HttpRequest('POST', `/api/reservas/${RESERVA_ID}/checkin`, corpo, {
      headers: headers({ 'X-Documento-Final': '9132', 'Idempotency-Key': chave }),
    });
  const comChave = (h: Record<string, string> = {}) =>
    headers({ Authorization: `Bearer ${TOKEN_MOCK}`, ...h });
  const pedir = (chave = 'p-1', itens: object[] = [{ sku: 'BEB-DRINKS-TRIO', quantidade: 1 }]) =>
    new HttpRequest(
      'POST',
      '/api/consumos',
      { reservaId: RESERVA_ID, itens },
      { headers: comChave({ 'Idempotency-Key': chave }) },
    );

  function fazerCheckin(): void {
    chamar(checkin());
    vi.advanceTimersByTime(2_000);
  }

  it('busca a reserva com o documento certo', () => {
    const { resposta } = chamar(buscar());
    expect(resposta?.status).toBe(200);
    expect(resposta?.body.reservaId).toBe(RESERVA_ID);
    expect(resposta?.body.hospede.documentoMascarado).toContain('***');
  });

  it('documento errado e cenário reserva-inexistente devolvem o mesmo 404 em Problem Details', () => {
    for (const r of [chamar(buscar('0000')), chamar(buscar(), 'reserva-inexistente')]) {
      const corpo = r.erro?.error as ProblemDetails;
      expect(r.erro?.status).toBe(404);
      expect(r.erro?.headers.get('Content-Type')).toBe('application/problem+json');
      expect(corpo.type).toBe('/erros/reserva-nao-encontrada');
    }
  });

  it('código fora do formato devolve 400', () => {
    expect(chamar(buscar('9132', 'ABC')).erro?.status).toBe(400);
  });

  it('check-in devolve 202 e a CHAVE_GERADA chega 2 s depois', () => {
    const { resposta } = chamar(checkin());
    expect(resposta?.status).toBe(202);
    expect(resposta?.headers.get('Location')).toBe(`/api/reservas/${RESERVA_ID}`);
    expect(mensagens).toHaveLength(0);

    vi.advanceTimersByTime(2_000);
    expect(mensagens[0]).toMatchObject({
      tipo: 'CHAVE_GERADA',
      token: TOKEN_MOCK,
      portas: ['Q-214', 'PISCINA', 'ACADEMIA'],
    });
  });

  it('mesma Idempotency-Key devolve a mesma resposta; chave nova depois do check-in dá 409', () => {
    const a = chamar(checkin('k-1')).resposta;
    const b = chamar(checkin('k-1')).resposta;
    expect(b?.body.correlationId).toBe(a?.body.correlationId);
    expect(chamar(checkin('k-2')).erro?.status).toBe(409);
  });

  it('check-in sem aceite dos termos dá 400 e sem Idempotency-Key também', () => {
    expect(chamar(checkin('k-1', { acompanhantes: 1, aceiteTermos: false })).erro?.status).toBe(
      400,
    );
    const semChave = new HttpRequest(
      'POST',
      `/api/reservas/${RESERVA_ID}/checkin`,
      { acompanhantes: 0, aceiteTermos: true },
      {
        headers: headers({ 'X-Documento-Final': '9132' }),
      },
    );
    expect(chamar(semChave).erro?.status).toBe(400);
  });

  it('cenário fora-da-data devolve 422', () => {
    expect(chamar(checkin(), 'fora-da-data').erro?.status).toBe(422);
  });

  it('catálogo filtra por categoria e devolve URL de imagem; categoria inválida dá 400', () => {
    const todos = chamar(new HttpRequest('GET', '/api/catalogo')).resposta?.body;
    expect(todos).toHaveLength(12);
    const exp = chamar(
      new HttpRequest('GET', '/api/catalogo', {
        params: new HttpParams().set('categoria', 'EXPERIENCIA'),
      }),
    ).resposta?.body;
    expect(exp).toHaveLength(3);
    expect(exp[0].imagem.presignedUrl).toMatch(/^\/mock\/catalogo\//);
    const ruim = new HttpRequest('GET', '/api/catalogo', {
      params: new HttpParams().set('categoria', 'BAR'),
    });
    expect(chamar(ruim).erro?.status).toBe(400);
  });

  it('pedido aumenta pendentes na hora e entra na fatura 1,5 s depois com versão +1', () => {
    fazerCheckin();
    const { resposta } = chamar(pedir());
    expect(resposta?.status).toBe(202);
    expect(resposta?.body.totalCentavos).toBe(4800);
    expect(store.fatura().pendentes).toBe(1);

    vi.advanceTimersByTime(1_500);
    const msg = mensagens.at(-1) as { tipo: string; fatura: FaturaResponse };
    expect(msg.tipo).toBe('FATURA_ATUALIZADA');
    expect(msg.fatura).toMatchObject({
      versao: 2,
      pendentes: 0,
      consumoCentavos: 4800,
      totalCentavos: 186000 + 4800,
    });
  });

  it('pedido sem token dá 401; experiência sem horário dá 400; SKU inexistente dá 422', () => {
    fazerCheckin();
    const semToken = new HttpRequest(
      'POST',
      '/api/consumos',
      { reservaId: RESERVA_ID, itens: [{ sku: 'BEB-DRINKS-TRIO', quantidade: 1 }] },
      {
        headers: headers({ 'Idempotency-Key': 'x' }),
      },
    );
    expect(chamar(semToken).erro?.status).toBe(401);
    expect(
      chamar(pedir('p-2', [{ sku: 'EXP-JANGADA-PISCINAS', quantidade: 1 }])).erro?.status,
    ).toBe(400);
    expect(chamar(pedir('p-3', [{ sku: 'NAO-EXISTE', quantidade: 1 }])).erro?.status).toBe(422);
  });

  it('cenário fatura-fechando: pedido devolve 409', () => {
    fazerCheckin();
    expect(chamar(pedir(), 'fatura-fechando').erro?.status).toBe(409);
  });

  it('cenário sem-rede devolve status 0', () => {
    expect(chamar(buscar(), 'sem-rede').erro?.status).toBe(0);
  });

  it('checkout fecha a fatura e depois revoga a chave', () => {
    fazerCheckin();
    const req = new HttpRequest('POST', `/api/reservas/${RESERVA_ID}/checkout`, null, {
      headers: comChave({ 'Idempotency-Key': 'c-1' }),
    });
    expect(chamar(req).resposta?.status).toBe(202);
    expect(store.fatura().status).toBe('FECHANDO');

    vi.advanceTimersByTime(3_000);
    expect(mensagens.map((m) => m.tipo)).toEqual([
      'CHAVE_GERADA',
      'FATURA_FECHADA',
      'CHAVE_REVOGADA',
    ]);
    expect(store.reserva().status).toBe('CHECKED_OUT');
  });

  it('fatura antes do check-in dá 404; de outra reserva dá 403', () => {
    const fatura = (id: string) =>
      new HttpRequest('GET', `/api/faturas/${id}`, { headers: comChave() });
    expect(chamar(fatura(RESERVA_ID)).erro?.status).toBe(404);
    fazerCheckin();
    expect(chamar(fatura(RESERVA_ID)).resposta?.body.reservaId).toBe(RESERVA_ID);
    expect(chamar(fatura('RSV-20261120-0099')).erro?.status).toBe(403);
  });
});
