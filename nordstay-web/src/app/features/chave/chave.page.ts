import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Esqueleto da rota. A tela é construída na NS-18 (layout no Figma, página "03 · Mobile"). */
@Component({
  selector: 'ns-chave-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ns-em-construcao">
      <h1>Sua chave</h1>
      <p>Tela da NS-18.</p>
    </section>
  `,
})
export class ChavePage {}
