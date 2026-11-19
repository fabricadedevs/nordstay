/** Formato do código da reserva: RSV-AAAAMMDD-NNNN */
export const PADRAO_CODIGO = /^RSV-\d{8}-\d{4}$/;
export const PADRAO_DOCUMENTO = /^\d{4}$/;

/**
 * Máscara do campo "Código da reserva": o hóspede digita só números
 * (ou cola o código inteiro) e o campo vira RSV-20261120-0042.
 */
export function formatarCodigo(valor: string): string {
  const digitos = valor.replace(/\D/g, '').slice(0, 12);
  if (!digitos) return '';
  const data = digitos.slice(0, 8);
  const sequencia = digitos.slice(8);
  return `RSV-${data}${sequencia ? `-${sequencia}` : ''}`;
}

export function somenteDigitos(valor: string, maximo: number): string {
  return valor.replace(/\D/g, '').slice(0, maximo);
}
