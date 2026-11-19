import { Component, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { formatarCodigo, PADRAO_CODIGO, PADRAO_DOCUMENTO, somenteDigitos } from '../codigo-reserva/codigo-reserva';

export interface BuscaReserva {
  codigo: string;
  documentoFinal: string;
}

@Component({
  imports: [ReactiveFormsModule],
  selector: 'ns-passo-buscar',
  styleUrl: './passo-buscar.scss',
  templateUrl: './passo-buscar.html',
})
export class PassoBuscar {
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly buscar = output<BuscaReserva>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    codigo: ['', [Validators.required, Validators.pattern(PADRAO_CODIGO)]],
    documentoFinal: ['', [Validators.required, Validators.pattern(PADRAO_DOCUMENTO)]]
  });

  protected mascararCodigo(): void {
    const campo = this.form.controls.codigo;
    campo.setValue(formatarCodigo(campo.value), { emitEvent: false });
  }

  protected mascararDocumento(): void {
    const campo = this.form.controls.documentoFinal;
    campo.setValue(somenteDigitos(campo.value, 4), { emitEvent: false });
  }

  protected enviar(): void {
    if (this.form.invalid || this.carregando()) return;
    const { codigo, documentoFinal } = this.form.getRawValue();
    this.buscar.emit({ codigo, documentoFinal });
  }
}
