import { Pipe, PipeTransform } from '@angular/core';

const FORMATO = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Centavos → "R$ 1.860,00". Uso: {{ fatura.totalCentavos | brl }} */
@Pipe({ name: 'brl' })
export class BrlPipe implements PipeTransform {
  transform(centavos: number | null | undefined): string {
    if (centavos == null || Number.isNaN(centavos)) return '';
    return FORMATO.format(centavos / 100).replace(/ /g, ' ');
  }
}
