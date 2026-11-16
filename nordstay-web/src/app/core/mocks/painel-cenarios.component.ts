import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessaoStore } from '../auth/sessao.store';
import { EstadiaSocket } from '../realtime/estadia-socket';
import { CENARIOS, Cenario, cenarioAtual, definirCenario } from './cenarios';
import { DOCUMENTO_FINAL, RESERVA_ID } from './fixtures';
import { MockEstadiaSocket } from './mock-estadia-socket';
import { MockStore } from './mock-store';

/** Botão "MOCK" + painel de cenários. Só é renderizado quando environment.useMocks = true. */
@Component({
  selector: 'ns-painel-cenarios',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="fab"
      (click)="aberto.set(!aberto())"
      [attr.aria-expanded]="aberto()"
      aria-controls="ns-painel"
    >
      MOCK
    </button>

    @if (aberto()) {
      <div class="fundo" (click)="aberto.set(false)"></div>
      <section id="ns-painel" class="painel" role="dialog" aria-label="Cenários de teste">
        <header>
          <span class="selo">MOCK</span>
          <h2>Cenários de teste</h2>
        </header>
        <p class="dica">
          Reserva <b>{{ reservaId }}</b> · documento final <b>{{ documento }}</b>
        </p>

        <fieldset>
          <legend>Cenário</legend>
          @for (c of cenarios; track c.id) {
            <label [class.ativo]="c.id === atual()">
              <input
                type="radio"
                name="ns-cenario"
                [value]="c.id"
                [checked]="c.id === atual()"
                (change)="escolher(c.id)"
              />
              <span
                ><b>{{ c.id }}</b
                ><small>{{ c.descricao }}</small></span
              >
            </label>
          }
        </fieldset>

        <div class="acoes">
          <button type="button" (click)="derrubarConexao()">Derrubar conexão</button>
          <button type="button" (click)="store.expirarChave()">Expirar chave</button>
          <button type="button" (click)="store.enviarMensagemForaDeOrdem()">
            Mensagem fora de ordem
          </button>
          <button type="button" (click)="reiniciar()">Reiniciar mock</button>
        </div>
      </section>
    }
  `,
  styles: `
    .fab {
      position: fixed;
      right: 16px;
      bottom: 96px;
      z-index: 1000;
      width: 52px;
      height: 52px;
      border-radius: 50%;
      border: 0;
      background: #111827;
      color: #fde68a;
      font: 800 10px/1 var(--ns-fonte);
      letter-spacing: 0.08em;
      cursor: pointer;
      box-shadow: 0 6px 20px rgb(0 0 0 / 0.25);
    }
    .fundo {
      position: fixed;
      inset: 0;
      z-index: 1000;
      background: rgb(11 18 32 / 0.5);
    }
    .painel {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      z-index: 1001;
      max-height: 80dvh;
      overflow: auto;
      padding: 24px;
      background: #fff;
      border-radius: 24px 24px 0 0;
      font-family: var(--ns-fonte);
      color: var(--ns-tinta);
    }
    header {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    h2 {
      margin: 0;
      font-size: 20px;
    }
    .selo {
      background: #111827;
      color: #fde68a;
      font-size: 11px;
      font-weight: 800;
      padding: 4px 8px;
      border-radius: 6px;
    }
    .dica {
      font-size: 13px;
      color: var(--ns-cinza);
    }
    fieldset {
      border: 0;
      padding: 0;
      margin: 0 0 16px;
      display: grid;
      gap: 8px;
    }
    legend {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
    }
    label {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 10px 14px;
      border: 1.5px solid var(--ns-linha);
      border-radius: 12px;
      cursor: pointer;
    }
    label.ativo {
      border-color: var(--ns-laranja);
      background: #fff7ee;
    }
    label span {
      display: flex;
      flex-direction: column;
      font-size: 13px;
    }
    label small {
      color: var(--ns-cinza);
      font-size: 11px;
    }
    input {
      accent-color: var(--ns-laranja);
    }
    .acoes {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
    }
    .acoes button {
      padding: 10px;
      border: 1.5px solid var(--ns-linha);
      border-radius: 12px;
      background: #fff;
      font: 600 13px var(--ns-fonte);
      cursor: pointer;
    }
  `,
})
export class PainelCenariosComponent {
  protected readonly store = inject(MockStore);
  private readonly socket = inject(EstadiaSocket);
  private readonly sessao = inject(SessaoStore);
  private readonly router = inject(Router);

  protected readonly cenarios = CENARIOS;
  protected readonly reservaId = RESERVA_ID;
  protected readonly documento = DOCUMENTO_FINAL;
  protected readonly aberto = signal(false);
  protected readonly atual = signal<Cenario>(cenarioAtual());

  protected escolher(cenario: Cenario): void {
    definirCenario(cenario);
    this.atual.set(cenario);
  }

  protected derrubarConexao(): void {
    if (this.socket instanceof MockEstadiaSocket) this.socket.derrubarConexao();
  }

  protected reiniciar(): void {
    this.store.reiniciar();
    this.sessao.limpar();
    this.aberto.set(false);
    void this.router.navigate(['/checkin']);
  }
}
