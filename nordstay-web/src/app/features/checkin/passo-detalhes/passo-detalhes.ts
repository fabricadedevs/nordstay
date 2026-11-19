import { Component, inject, input, output } from '@angular/core';
import { CheckinRequest } from '../../../shared/models/nordstay.models';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

const MAX_ACOMPANHANTES = 6;

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-passo-detalhes',
  styleUrl: './passo-detalhes.scss',
  templateUrl: './passo-detalhes.html',
})
export class PassoDetalhes {
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly confirmar = output<CheckinRequest>();

  protected readonly max = MAX_ACOMPANHANTES;

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
