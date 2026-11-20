import { Checkin } from './checkin';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessaoStore } from '../../core/auth/sessao.store';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { idempotencyInterceptor } from '../../core/interceptors/idempotency.interceptor';
import { definirCenario } from '../../core/mocks/cenarios';
import { mockApiInterceptor } from '../../core/mocks/mock-api.interceptor';
import { MockStore } from '../../core/mocks/mock-store';

/** Teste de ponta a ponta da tela usando o backend simulado (mesmo do npm start). */
describe('Checkin', () => {
  let fixture: ComponentFixture<Checkin>;
  let tela: HTMLElement;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [Checkin],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([idempotencyInterceptor, errorInterceptor, mockApiInterceptor])),
      ],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(Checkin);
    tela = fixture.nativeElement;
    await atualizar();
  });

  afterEach(() => {
    TestBed.inject(MockStore).reiniciar();
    vi.useRealTimers();
  });

  async function atualizar(ms = 0): Promise<void> {
    vi.advanceTimersByTime(ms);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function digitar(seletor: string, valor: string): void {
    const campo = tela.querySelector<HTMLInputElement>(seletor)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  const botao = (texto: string) =>
    [...tela.querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.includes(texto))!;

  async function buscarReserva(documento = '9132'): Promise<void> {
    digitar('#codigo', '202611200042');
    digitar('#documento', documento);
    await atualizar();
    botao('Buscar reserva').click();
    await atualizar(1_000);
  }

  it('aplica a máscara e só habilita a busca com os dois campos válidos', async () => {
    expect(botao('Buscar reserva').disabled).toBe(true);
    digitar('#codigo', '202611200042');
    digitar('#documento', '91a32');
    await atualizar();
    expect(tela.querySelector<HTMLInputElement>('#codigo')!.value).toBe('RSV-20261120-0042');
    expect(tela.querySelector<HTMLInputElement>('#documento')!.value).toBe('9132');
    expect(botao('Buscar reserva').disabled).toBe(false);
  });

  it('documento errado mostra o erro 404 e marca os campos', async () => {
    await buscarReserva('0000');
    expect(tela.querySelector('[role=alert]')?.textContent).toContain('Não encontramos essa reserva');
    expect(tela.querySelector('#codigo')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('fluxo completo: buscar → conferir → detalhes → check-in → tela da chave', async () => {
    await buscarReserva();
    expect(tela.textContent).toContain('Confira sua reserva');
    expect(tela.textContent).toContain('***.***.*91-32');

    botao('Continuar').click();
    await atualizar();
    expect(botao('Fazer check-in').disabled).toBe(true);

    tela.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    await atualizar();
    botao('Fazer check-in').click();
    botao('Fazer check-in').click(); // toque duplo
    await atualizar(1_000);

    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith(['/estadia', 'RSV-20261120-0042', 'chave']);
    expect(TestBed.inject(SessaoStore).reservaId()).toBe('RSV-20261120-0042');
  });

  it('cenário fora-da-data mostra a mensagem do 422', async () => {
    await buscarReserva();
    botao('Continuar').click();
    await atualizar();
    definirCenario('fora-da-data');
    tela.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    await atualizar();
    botao('Fazer check-in').click();
    await atualizar(1_000);
    expect(tela.querySelector('[role=alert]')?.textContent).toContain('só pode ser feito em');
    expect(navegar).not.toHaveBeenCalled();
  });
});
