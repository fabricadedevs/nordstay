export type Cenario =
  | 'feliz'
  | 'reserva-inexistente'
  | 'fora-da-data'
  | 'fatura-fechando'
  | 'sem-rede'
  | 'lento'
  | 'chave-revogada';

export const CENARIOS: ReadonlyArray<{ id: Cenario; descricao: string }> = [
  { id: 'feliz', descricao: 'Tudo responde certo' },
  { id: 'reserva-inexistente', descricao: 'Busca e check-in devolvem 404' },
  { id: 'fora-da-data', descricao: 'Check-in devolve 422' },
  { id: 'fatura-fechando', descricao: 'Pedido devolve 409 e o fechamento demora 8 s' },
  { id: 'sem-rede', descricao: 'Toda chamada falha (status 0)' },
  { id: 'lento', descricao: 'Respostas em 6 a 10 s' },
  { id: 'chave-revogada', descricao: 'A chave é revogada 5 s depois de chegar' },
];

const CHAVE_STORAGE = 'ns.cenario';

export function cenarioAtual(): Cenario {
  try {
    const salvo = localStorage.getItem(CHAVE_STORAGE);
    return CENARIOS.some((c) => c.id === salvo) ? (salvo as Cenario) : 'feliz';
  } catch {
    return 'feliz';
  }
}

export function definirCenario(cenario: Cenario): void {
  try {
    localStorage.setItem(CHAVE_STORAGE, cenario);
  } catch {
    // navegador sem localStorage: o mock segue no cenário feliz
  }
}
