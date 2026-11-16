import { BrlPipe } from './brl.pipe';

describe('BrlPipe', () => {
  const pipe = new BrlPipe();

  it('converte centavos em reais', () => {
    expect(pipe.transform(186000)).toBe('R$ 1.860,00');
    expect(pipe.transform(4800)).toBe('R$ 48,00');
    expect(pipe.transform(0)).toBe('R$ 0,00');
  });

  it('valor ausente vira texto vazio', () => {
    expect(pipe.transform(null)).toBe('');
  });
});
