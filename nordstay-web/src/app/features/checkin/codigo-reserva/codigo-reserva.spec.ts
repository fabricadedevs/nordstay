import { formatarCodigo, somenteDigitos, PADRAO_CODIGO } from './codigo-reserva';

describe('formatarCodigo', () => {
  it('monta o código enquanto o hóspede digita', () => {
    expect(formatarCodigo('2')).toBe('RSV-2');
    expect(formatarCodigo('20261120')).toBe('RSV-20261120');
    expect(formatarCodigo('202611200042')).toBe('RSV-20261120-0042');
  });

  it('aceita o código colado inteiro, em minúsculas ou com espaços', () => {
    expect(formatarCodigo('rsv-20261120-0042')).toBe('RSV-20261120-0042');
    expect(formatarCodigo(' RSV 2026 1120 0042 ')).toBe('RSV-20261120-0042');
  });

  it('corta o excesso e deixa vazio quando não há números', () => {
    expect(formatarCodigo('2026112000429999')).toBe('RSV-20261120-0042');
    expect(formatarCodigo('abc')).toBe('');
  });

  it('o resultado completo passa no padrão', () => {
    expect(PADRAO_CODIGO.test(formatarCodigo('202611200042'))).toBe(true);
  });
});

describe('somenteDigitos', () => {
  it('mantém só os 4 primeiros números', () => {
    expect(somenteDigitos('9a1-32x7', 4)).toBe('9132');
  });
});
