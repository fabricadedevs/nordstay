import { Pipe, PipeTransform } from '@angular/core';

const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

@Pipe({
  name: 'dataCurta',
})
export class DataCurtaPipe implements PipeTransform {
  transform(isoDia: string | null | undefined): string {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDia ?? '');
    if(!m) return '';
    const data = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    return `${DIAS[data.getDay()]}, ${data.getDate()} ${MESES[data.getMonth()]}`;
  }
}
