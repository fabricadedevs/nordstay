import { Component, input, output } from '@angular/core';
import { ReservaResponse } from '../../../shared/models/nordstay.models';
import { BrlPipe } from '../../../shared/pipes/brl.pipe';
import { DataCurtaPipe } from '../../../shared/pipes/data-curta-pipe';

@Component({
  imports: [BrlPipe, DataCurtaPipe],
  selector: 'app-passo-conferir',
  styleUrl: './passo-conferir.scss',
  templateUrl: './passo-conferir.html',
})
export class PassoConferir {
  readonly reserva = input.required<ReservaResponse>();
  readonly continuar = output<void>();
  readonly voltar = output<void>();
}
