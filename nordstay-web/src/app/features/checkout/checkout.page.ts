import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Esqueleto da rota. A tela é construída na NS-21 (layout no Figma, página "03 · Mobile"). */
@Component({
  selector: 'ns-checkout-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ns-em-construcao">
      <h1>Checkout</h1>
      <p>Tela da NS-21.</p>
    </section>
  `,
})
export class CheckoutPage {}
