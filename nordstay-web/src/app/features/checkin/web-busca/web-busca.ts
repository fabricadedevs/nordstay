import { Component, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SiteCabecalho } from '../../../shared/ui/site-cabecalho/site-cabecalho';
import { SiteRodape } from '../../../shared/ui/site-rodape/site-rodape';
import {
  formatarCodigo,
  PADRAO_CODIGO,
  PADRAO_DOCUMENTO,
  somenteDigitos,
} from '../codigo-reserva/codigo-reserva';
import { AREAS_RESORT, PASSOS_CHECKIN } from '../conteudo-resort';
import { BuscaReserva } from '../passo-buscar/passo-buscar';

/** Tela web do passo 1 · Figma "NS-17 · Web · Check-in" (1440 px). */
@Component({
  selector: 'ns-web-busca',
  imports: [ReactiveFormsModule, SiteCabecalho, SiteRodape],
  styleUrl: './web-busca.scss',
  templateUrl: './web-busca.html',
})
export class WebBusca {
  readonly carregando = input(false);
  readonly erro = input<string | null>(null);
  readonly buscar = output<BuscaReserva>();

  protected readonly passos = PASSOS_CHECKIN;
  protected readonly areas = AREAS_RESORT;

  protected readonly form = inject(NonNullableFormBuilder).group({
    codigo: ['', [Validators.required, Validators.pattern(PADRAO_CODIGO)]],
    documentoFinal: ['', [Validators.required, Validators.pattern(PADRAO_DOCUMENTO)]],
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
    this.buscar.emit(this.form.getRawValue());
  }
}
