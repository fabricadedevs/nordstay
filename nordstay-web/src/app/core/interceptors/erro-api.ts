import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetails } from '../../shared/models/nordstay.models';

/** Erro que as telas recebem: status, mensagem pronta em português e o Problem Details original. */
export class ErroApi extends Error {
  constructor(
    readonly status: number,
    readonly mensagem: string,
    readonly tipo: string | null,
    readonly correlationId: string | null,
    readonly problema: ProblemDetails | null,
  ) {
    super(mensagem);
    this.name = 'ErroApi';
  }

  static de(erro: HttpErrorResponse): ErroApi {
    const problema = ehProblemDetails(erro.error) ? erro.error : null;
    return new ErroApi(
      erro.status,
      mensagemPara(erro.status, problema),
      problema?.type ?? null,
      problema?.correlationId ?? null,
      problema,
    );
  }
}

export function mensagemPara(status: number, problema: ProblemDetails | null): string {
  switch (status) {
    case 0:
      return 'Sem conexão, tente de novo.';
    case 401:
      return 'Sua sessão expirou. Faça o check-in de novo.';
    case 404:
      return problema?.type === '/erros/reserva-nao-encontrada'
        ? 'Não encontramos essa reserva. Confira o código e os dígitos.'
        : (problema?.detail ?? 'Não encontramos o que você pediu.');
    case 429:
      return 'Muitas tentativas. Espere um minuto.';
    case 400:
    case 403:
    case 409:
    case 422:
      return problema?.detail ?? 'Não foi possível concluir. Confira os dados.';
    default:
      return 'Algo deu errado do nosso lado. Tente de novo em instantes.';
  }
}

function ehProblemDetails(v: unknown): v is ProblemDetails {
  return typeof v === 'object' && v !== null && 'status' in v && 'title' in v;
}
