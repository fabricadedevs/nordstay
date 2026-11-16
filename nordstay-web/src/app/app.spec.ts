import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { EstadiaSocket } from './core/realtime/estadia-socket';
import { MockEstadiaSocket } from './core/mocks/mock-estadia-socket';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: EstadiaSocket, useClass: MockEstadiaSocket }],
    }).compileComponents();
  });

  it('cria o app', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('mostra o botão MOCK em desenvolvimento (useMocks = true)', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const botao = (fixture.nativeElement as HTMLElement).querySelector('ns-painel-cenarios button');
    expect(botao?.textContent?.trim()).toBe('MOCK');
  });
});
