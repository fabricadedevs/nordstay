import { SlicePipe } from '@angular/common';
import { Component, computed, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CheckinRequest, ReservaResponse } from '../../../shared/models/nordstay.models';
import { BrlPipe } from '../../../shared/pipes/brl.pipe';
import { DataCurtaPipe } from '../../../shared/pipes/data-curta-pipe';
import { GaleriaFotos } from '../../../shared/ui/galeria-fotos/galeria-fotos';
import { SiteCabecalho } from '../../../shared/ui/site-cabecalho/site-cabecalho';
import { SiteRodape } from '../../../shared/ui/site-rodape/site-rodape';
import { QUARTO } from '../conteudo-resort';

const MAX_ACOMPANHANTES = 6;
const DIA_MS = 86_400_000;

/**
 * Tela web dos passos 2 e 3 juntos · Figma "NS-17 · Web · Conferir e fazer check-in".
 * À esquerda o quarto; à direita o resumo da reserva com os últimos detalhes.
 */
@Component({
  selector: 'ns-web-reserva',
  imports: [ReactiveFormsModule, SlicePipe, BrlPipe, DataCurtaPipe, SiteCabecalho, SiteRodape, GaleriaFotos],
  styleUrl: './web-reserva.scss',
  templateUrl: './web-reserva.html',
})
export class WebReserva {
  readonly reserva = input.required<ReservaResponse>();
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly mostrarIrParaChave = input(false);
  readonly confirmar = output<CheckinRequest>();
  readonly buscarOutra = output<void>();
  readonly irParaChave = output<void>();

  protected readonly quarto = QUARTO;
  protected readonly max = MAX_ACOMPANHANTES;

  protected readonly iniciais = computed(() =>
    this.reserva()
      .hospede.nome.split(' ')
      .filter(Boolean)
      .map((p, i, todas) => (i === 0 || i === todas.length - 1 ? p[0] : ''))
      .join('')
      .toUpperCase(),
  );

  protected readonly noites = computed(() => {
    const { entrada, saida } = this.reserva();
    return Math.max(1, Math.round((Date.parse(saida) - Date.parse(entrada)) / DIA_MS));
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    acompanhantes: [0, [Validators.min(0), Validators.max(MAX_ACOMPANHANTES)]],
    previsaoChegada: [''],
    aceiteTermos: [false, Validators.requiredTrue],
  });

  protected get acompanhantes(): number {
    return this.form.controls.acompanhantes.value;
  }

  protected alterar(delta: number): void {
    const novo = Math.min(MAX_ACOMPANHANTES, Math.max(0, this.acompanhantes + delta));
    this.form.controls.acompanhantes.setValue(novo);
  }

  protected enviar(): void {
    if (this.form.invalid || this.carregando()) return;
    const { acompanhantes, previsaoChegada, aceiteTermos } = this.form.getRawValue();
    this.confirmar.emit({
      acompanhantes,
      aceiteTermos,
      ...(previsaoChegada ? { previsaoChegada } : {}),
    });
  }
}
