import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Esqueleto da rota. A tela é construída na NS-19 (layout no Figma, página "03 · Mobile"). */
@Component({
  selector: 'ns-catalogo-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ns-em-construcao">
      <h1>Catálogo</h1>
      <p>Tela da NS-19.</p>
    </section>
  `,
})
export class CatalogoPage {}
