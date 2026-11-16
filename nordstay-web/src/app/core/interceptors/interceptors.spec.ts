import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessaoStore } from '../auth/sessao.store';
import { authInterceptor } from './auth.interceptor';
import { ErroApi } from './erro-api';
import { errorInterceptor } from './error.interceptor';
import { idempotencyInterceptor } from './idempotency.interceptor';

describe('interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let sessao: SessaoStore;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([authInterceptor, idempotencyInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    sessao = TestBed.inject(SessaoStore);
    sessao.iniciarEstadia('RSV-20261120-0042', 'Q-214', 'Marina Albuquerque');
    sessao.definirChave({
      tipo: 'CHAVE_GERADA',
      jti: 'k-1',
      token: 'tok',
      portas: ['Q-214'],
      validoAte: '2026-11-23T12:00:00-03:00',
    });
  });

  afterEach(() => backend.verify());

  it('Authorization só nas rotas protegidas', () => {
    http.get('/api/faturas/RSV-20261120-0042').subscribe();
    http.get('/api/catalogo').subscribe();
    expect(
      backend.expectOne('/api/faturas/RSV-20261120-0042').request.headers.get('Authorization'),
    ).toBe('Bearer tok');
    expect(backend.expectOne('/api/catalogo').request.headers.has('Authorization')).toBe(false);
  });

  it('todo POST ganha Idempotency-Key, e uma chave já informada é mantida', () => {
    http.post('/api/consumos', {}).subscribe();
    http.post('/api/consumos', {}, { headers: { 'Idempotency-Key': 'repetir' } }).subscribe();
    http.get('/api/catalogo').subscribe();
    const [a, b] = backend.match('/api/consumos');
    expect(a.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(b.request.headers.get('Idempotency-Key')).toBe('repetir');
    expect(backend.expectOne('/api/catalogo').request.headers.has('Idempotency-Key')).toBe(false);
  });

  it('Problem Details vira ErroApi com a mensagem do detail', () => {
    let erro: ErroApi | undefined;
    http.post('/api/consumos', {}).subscribe({ error: (e) => (erro = e) });
    backend
      .expectOne('/api/consumos')
      .flush(
        {
          type: '/erros/fatura-fechada',
          title: 'Fatura não aceita consumo',
          status: 409,
          detail: 'A fatura está FECHANDO',
        },
        { status: 409, statusText: 'Conflict' },
      );
    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro?.mensagem).toBe('A fatura está FECHANDO');
    expect(erro?.tipo).toBe('/erros/fatura-fechada');
  });

  it('404 de reserva, 429 e falta de rede têm mensagens fixas', () => {
    const erros: ErroApi[] = [];
    for (const url of ['/a', '/b', '/c']) http.get(url).subscribe({ error: (e) => erros.push(e) });
    backend
      .expectOne('/a')
      .flush(
        { type: '/erros/reserva-nao-encontrada', title: 'x', status: 404, detail: 'y' },
        { status: 404, statusText: 'Not Found' },
      );
    backend.expectOne('/b').flush(null, { status: 429, statusText: 'Too Many Requests' });
    backend.expectOne('/c').error(new ProgressEvent('error'));
    expect(erros.map((e) => e.mensagem)).toEqual([
      'Não encontramos essa reserva. Confira o código e os dígitos.',
      'Muitas tentativas. Espere um minuto.',
      'Sem conexão, tente de novo.',
    ]);
  });

  it('401 limpa a sessão e volta para /checkin', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.get('/api/faturas/RSV-20261120-0042').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/faturas/RSV-20261120-0042')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(sessao.checkinFeito()).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/checkin']);
  });
});
