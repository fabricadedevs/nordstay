import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessaoStore } from '../../core/auth/sessao.store';

/** Layout das telas da estadia: cabeçalho, conteúdo e navegação inferior (Chave · Catálogo · Conta). */
@Component({
  selector: 'ns-estadia-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="topo">
      <span class="marca" aria-hidden="true"></span>
      <div class="titulo">
        <small>NordStay</small>
        <strong>{{ quarto() }}</strong>
      </div>
      <span class="hospede">{{ primeiroNome() }}</span>
    </header>

    <main class="conteudo">
      <router-outlet />
    </main>

    <nav class="abas" aria-label="Navegação da estadia">
      <a routerLink="chave" routerLinkActive="ativa" ariaCurrentWhenActive="page">Chave</a>
      <a routerLink="catalogo" routerLinkActive="ativa" ariaCurrentWhenActive="page">Catálogo</a>
      <a routerLink="fatura" routerLinkActive="ativa" ariaCurrentWhenActive="page">Conta</a>
    </nav>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100dvh;
      background: var(--ns-fundo);
    }
    .topo {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 20px;
      background: var(--ns-navy);
      color: #fff;
    }
    .marca {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: var(--ns-laranja);
      flex: none;
    }
    .titulo {
      display: flex;
      flex-direction: column;
      flex: 1;
    }
    .titulo small {
      color: #f4b57a;
      font-size: 12px;
    }
    .titulo strong {
      font-size: 20px;
    }
    .hospede {
      font-size: 12px;
      opacity: 0.75;
    }
    .conteudo {
      flex: 1;
      padding: 20px;
    }
    .abas {
      position: sticky;
      bottom: 0;
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      background: #fff;
      border-top: 1px solid var(--ns-linha);
    }
    .abas a {
      padding: 14px 0 18px;
      text-align: center;
      font-size: 12px;
      font-weight: 500;
      color: var(--ns-cinza);
      text-decoration: none;
    }
    .abas a.ativa {
      color: var(--ns-laranja);
      font-weight: 600;
    }
  `,
})
export class EstadiaShellComponent {
  private readonly sessao = inject(SessaoStore);
  protected readonly quarto = computed(() => `Quarto ${this.sessao.quartoId() ?? ''}`.trim());
  protected readonly primeiroNome = computed(() => this.sessao.nomeHospede()?.split(' ')[0] ?? '');
}
