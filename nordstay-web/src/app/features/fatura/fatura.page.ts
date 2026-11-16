import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Esqueleto da rota. A tela é construída na NS-20 (layout no Figma, página "03 · Mobile"). */
@Component({
  selector: 'ns-fatura-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ns-em-construcao">
      <h1>Sua conta</h1>
      <p>Tela da NS-20.</p>
    </section>
  `,
})
export class FaturaPage {}
