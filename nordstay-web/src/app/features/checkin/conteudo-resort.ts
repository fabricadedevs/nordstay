/**
 * Conteúdo institucional das telas web do check-in (Figma "NS-17 · Web").
 * Não vem da API: são textos e fotos fixos do resort.
 */
const IMG = '/img/checkin';

export const PASSOS_CHECKIN = [
  {
    numero: 1,
    titulo: 'Faça o check-in',
    texto: 'Confirme a reserva e aceite os termos. Leva um minuto.',
    foto: `${IMG}/quarto.jpg`,
  },
  {
    numero: 2,
    titulo: 'Receba a chave digital',
    texto: 'O QR code chega no app e abre quarto, piscina e academia.',
    foto: `${IMG}/piscina.jpg`,
  },
  {
    numero: 3,
    titulo: 'Peça e acompanhe a conta',
    texto: 'Frigobar, petiscos e passeios vão direto para a fatura, ao vivo.',
    foto: `${IMG}/drinks.jpg`,
  },
] as const;

export const AREAS_RESORT = [
  { nome: 'Piscinas', texto: '3 piscinas · 7h às 22h', foto: `${IMG}/area-piscinas.jpg` },
  { nome: 'Praia privativa', texto: 'Serviço de praia incluído', foto: `${IMG}/area-praia.jpg` },
  { nome: 'Restaurante', texto: 'Café, almoço e jantar', foto: `${IMG}/area-restaurante.jpg` },
  { nome: 'Spa', texto: 'Massagens com agendamento', foto: `${IMG}/area-spa.jpg` },
] as const;

export const QUARTO = {
  categoria: 'Resort · Beira-mar',
  vista: 'Vista mar',
  local: 'NordStay Resort · Porto de Galinhas, Pernambuco',
  nota: '9,2',
  conceito: 'Fantástico',
  avaliacoes: '1.284 avaliações',
  fotosExtras: 18,
  fotos: [
    `${IMG}/quarto.jpg`,
    `${IMG}/quarto-2.jpg`,
    `${IMG}/quarto-3.jpg`,
    `${IMG}/quarto-4.jpg`,
    `${IMG}/piscina.jpg`,
  ],
  comodidades: [
    'Cama king e varanda com vista para o mar',
    'Frigobar abastecido, pedido pelo app',
    'Wi-Fi rápido e ar-condicionado',
    'Acesso a piscina e academia com a chave digital',
    'Café da manhã incluído',
    'Checkout pelo celular, sem fila',
  ],
} as const;
