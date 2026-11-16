import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Esqueleto da rota. A tela é construída na NS-17 (layout no Figma, página "03 · Mobile"). */
@Component({
  selector: 'ns-checkin-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="ns-em-construcao">
      <h1>Check-in</h1>
      <p>Tela da NS-17.</p>
    </section>
  `,
})
export class CheckinPage {}
