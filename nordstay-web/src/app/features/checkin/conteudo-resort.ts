/**
 * Conteúdo institucional das telas web do check-in (Figma "NS-17 · Web").
 * Não vem da API: são textos e fotos fixos do resort.
 */
import { FotoGaleria } from '../../shared/ui/galeria-fotos/galeria-fotos';

const IMG = '/img/checkin';

/**
 * Todas as fotos da galeria. As 5 primeiras aparecem no mosaico da tela;
 * o resto entra no "+N fotos". Para ter mais, basta pôr a imagem em
 * public/img/checkin e acrescentar aqui.
 */
export const FOTOS_QUARTO: readonly FotoGaleria[] = [
  { src: `${IMG}/quarto.jpg`, legenda: 'Quarto Q-214 · cama king' },
  { src: `${IMG}/quarto-2.jpg`, legenda: 'Quarto · área de descanso' },
  { src: `${IMG}/quarto-3.jpg`, legenda: 'Quarto · cama e janela' },
  { src: `${IMG}/quarto-4.jpg`, legenda: 'Quarto · sala de estar' },
  { src: `${IMG}/piscina.jpg`, legenda: 'Piscina à noite' },
  { src: `${IMG}/resort.jpg`, legenda: 'Vista geral do resort' },
  { src: `${IMG}/area-piscinas.jpg`, legenda: 'Piscinas · 7h às 22h' },
  { src: `${IMG}/area-praia.jpg`, legenda: 'Praia privativa' },
  { src: `${IMG}/area-restaurante.jpg`, legenda: 'Restaurante' },
  { src: `${IMG}/area-spa.jpg`, legenda: 'Spa' },
  { src: `${IMG}/drinks.jpg`, legenda: 'Bar da piscina' },
];

const FOTOS_NO_MOSAICO = 5;

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
  galeria: FOTOS_QUARTO,
  fotos: FOTOS_QUARTO.slice(0, FOTOS_NO_MOSAICO),
  fotosExtras: Math.max(0, FOTOS_QUARTO.length - FOTOS_NO_MOSAICO),
  comodidades: [
    'Cama king e varanda com vista para o mar',
    'Frigobar abastecido, pedido pelo app',
    'Wi-Fi rápido e ar-condicionado',
    'Acesso a piscina e academia com a chave digital',
    'Café da manhã incluído',
    'Checkout pelo celular, sem fila',
  ],
} as const;
