import { Categoria, FaturaResponse, ReservaResponse } from '../../shared/models/nordstay.models';

// Dados de teste: reserva RSV-20261120-0042 com os 4 últimos dígitos 9132.
export const RESERVA_ID = 'RSV-20261120-0042';
export const DOCUMENTO_FINAL = '9132';
export const QUARTO_ID = 'Q-214';
export const JTI_MOCK = 'k-7f3a9c21';
export const PORTAS_MOCK: readonly string[] = ['Q-214', 'PISCINA', 'ACADEMIA'];
/** JWT falso. Só serve para o mock: a assinatura não é válida. */
export const TOKEN_MOCK =
  'eyJhbGciOiJSUzI1NiIsImtpZCI6Im5vcmRzdGF5LW1vY2sifQ.' +
  'eyJzdWIiOiJSU1YtMjAyNjExMjAtMDA0MiIsImp0aSI6ImstN2YzYTljMjEifQ.mock';

const DIARIAS_CENTAVOS = 186000;

export function dataIso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function somarDias(d: Date, dias: number): Date {
  const r = new Date(d);
  r.setDate(r.getDate() + dias);
  return r;
}

/** A entrada é sempre hoje, para o check-in poder ser testado em qualquer dia. */
export function reservaInicial(hoje = new Date()): ReservaResponse {
  return {
    reservaId: RESERVA_ID,
    quartoId: QUARTO_ID,
    status: 'CONFIRMADA',
    hospede: { nome: 'Marina Albuquerque', documentoMascarado: '***.***.*91-32' },
    entrada: dataIso(hoje),
    saida: dataIso(somarDias(hoje, 3)),
    diariasCentavos: DIARIAS_CENTAVOS,
  };
}

export function faturaInicial(): FaturaResponse {
  return {
    reservaId: RESERVA_ID,
    status: 'ABERTA',
    versao: 1,
    pendentes: 0,
    diariasCentavos: DIARIAS_CENTAVOS,
    consumoCentavos: 0,
    totalCentavos: DIARIAS_CENTAVOS,
    itens: [],
  };
}

export interface ItemCatalogoFixture {
  sku: string;
  nome: string;
  categoria: Categoria;
  precoCentavos: number;
  exigeAgendamento: boolean;
  /** Arquivo em public/mock/catalogo/. */
  arquivo: string;
}

// 12 itens: 5 frigobar, 4 petiscos, 3 experiências.
export const CATALOGO: readonly ItemCatalogoFixture[] = [
  {
    sku: 'BEB-DRINKS-TRIO',
    nome: 'Drinks tropicais (trio)',
    categoria: 'FRIGOBAR',
    precoCentavos: 4800,
    exigeAgendamento: false,
    arquivo: 'drinks-tropicais.webp',
  },
  {
    sku: 'BEB-CHOPE-500',
    nome: 'Chope artesanal 500 ml',
    categoria: 'FRIGOBAR',
    precoCentavos: 2400,
    exigeAgendamento: false,
    arquivo: 'chope-artesanal.webp',
  },
  {
    sku: 'BEB-AGUA-COCO-500',
    nome: 'Água de coco 500 ml',
    categoria: 'FRIGOBAR',
    precoCentavos: 1200,
    exigeAgendamento: false,
    arquivo: 'agua-de-coco.webp',
  },
  {
    sku: 'BEB-REFRI-LATA',
    nome: 'Refrigerante lata 350 ml',
    categoria: 'FRIGOBAR',
    precoCentavos: 800,
    exigeAgendamento: false,
    arquivo: 'refrigerante.webp',
  },
  {
    sku: 'BEB-AGUA-500',
    nome: 'Água mineral 500 ml',
    categoria: 'FRIGOBAR',
    precoCentavos: 600,
    exigeAgendamento: false,
    arquivo: 'agua-mineral.webp',
  },
  {
    sku: 'PET-BOWL-TROPICAL',
    nome: 'Bowl tropical',
    categoria: 'PETISCO',
    precoCentavos: 3800,
    exigeAgendamento: false,
    arquivo: 'bowl-tropical.webp',
  },
  {
    sku: 'PET-TOSTADA-OVO',
    nome: 'Tostada com ovo',
    categoria: 'PETISCO',
    precoCentavos: 2800,
    exigeAgendamento: false,
    arquivo: 'tostada-com-ovo.webp',
  },
  {
    sku: 'PET-TAPIOCA-COALHO',
    nome: 'Tapioca de queijo coalho',
    categoria: 'PETISCO',
    precoCentavos: 2800,
    exigeAgendamento: false,
    arquivo: 'tapioca.webp',
  },
  {
    sku: 'PET-BOLINHO-MACAXEIRA',
    nome: 'Bolinho de macaxeira',
    categoria: 'PETISCO',
    precoCentavos: 3200,
    exigeAgendamento: false,
    arquivo: 'bolinho-macaxeira.webp',
  },
  {
    sku: 'EXP-JANGADA-PISCINAS',
    nome: 'Passeio às piscinas naturais',
    categoria: 'EXPERIENCIA',
    precoCentavos: 7500,
    exigeAgendamento: true,
    arquivo: 'piscinas-naturais.webp',
  },
  {
    sku: 'EXP-MERGULHO-CILINDRO',
    nome: 'Mergulho com cilindro',
    categoria: 'EXPERIENCIA',
    precoCentavos: 28000,
    exigeAgendamento: true,
    arquivo: 'mergulho.webp',
  },
  {
    sku: 'EXP-MASSAGEM-50',
    nome: 'Massagem relaxante',
    categoria: 'EXPERIENCIA',
    precoCentavos: 18000,
    exigeAgendamento: true,
    arquivo: 'massagem.webp',
  },
];
