import { Component, input, output } from '@angular/core';
import { ReservaResponse } from '../../../shared/models/nordstay.models';
import { BrlPipe } from '../../../shared/pipes/brl.pipe';
import { DataCurtaPipe } from '../../../shared/pipes/data-curta-pipe';
import { GaleriaFotos } from '../../../shared/ui/galeria-fotos/galeria-fotos';
import { FOTOS_QUARTO } from '../conteudo-resort';

@Component({
  imports: [BrlPipe, DataCurtaPipe, GaleriaFotos],
  selector: 'ns-passo-conferir',
  styleUrl: './passo-conferir.scss',
  templateUrl: './passo-conferir.html',
})
export class PassoConferir {
  readonly reserva = input.required<ReservaResponse>();
  readonly continuar = output<void>();
  readonly voltar = output<void>();

  protected readonly fotos = FOTOS_QUARTO;
}
