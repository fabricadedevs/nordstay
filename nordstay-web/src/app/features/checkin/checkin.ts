import { Component, inject, signal } from '@angular/core';
import { Passos } from '../../shared/ui/passos/passos';
import { BuscaReserva, PassoBuscar } from './passo-buscar/passo-buscar';
import { PassoConferir } from './passo-conferir/passo-conferir';
import { PassoDetalhes } from './passo-detalhes/passo-detalhes';
import { ReservaApi } from '../../core/api/reserva.api';
import { SessaoStore } from '../../core/auth/sessao.store';
import { Router } from '@angular/router';
import { CheckinRequest, ReservaResponse } from '../../shared/models/nordstay.models';
import { ErroApi } from '../../core/interceptors/erro-api';
import { finalize } from 'rxjs';

type Passo = 1 | 2 | 3;

@Component({
  imports: [Passos, PassoBuscar, PassoConferir, PassoDetalhes],
  selector: 'ns-checkin',
  styleUrl: './checkin.scss',
  templateUrl: './checkin.html',
})
export class Checkin {
  private readonly api = inject(ReservaApi);
  private readonly sessao = inject(SessaoStore);
  private readonly router = inject(Router);

  protected readonly passo = signal<Passo>(1);
  protected readonly reserva = signal<ReservaResponse | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<ErroApi | null>(null);

  private documentoFinal = '';

  private chaveCheckin: string | null = null;

  protected buscar({ codigo, documentoFinal }: BuscaReserva): void {
    if (this.carregando()) return;
    this.iniciarChamada();
    this.api
      .buscar(codigo, documentoFinal)
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: reserva => {
          this.reserva.set(reserva);
          this.documentoFinal = documentoFinal;
          this.chaveCheckin = null;
          this.irPara(2);
        },
        error: (e: ErroApi) => this.erro.set(e),
      });
  }

  protected fazerCheckin(dados: CheckinRequest): void {
    const reserva = this.reserva();
    if (!reserva || this.carregando()) return;
    this.chaveCheckin ??= crypto.randomUUID();
    this.iniciarChamada();
    this.api
      .fazerCheckin(reserva.reservaId, this.documentoFinal, dados, this.chaveCheckin)
      .pipe(finalize(() => this.carregando.set(false)))
      .subscribe({
        next: aceito => {
          this.sessao.iniciarEstadia(
            aceito.reservaId,
            aceito.quartoId,
            reserva.hospede.nome
          );
          void this.router.navigate(['/estadia', aceito.reservaId, 'chave']);
        },
        error: (e: ErroApi) => this.erro.set(e),
      });
  }

  /** 409: o check-in já tinha sido feito (por exemplo, em outro aparelho). */
  protected irParaChave(): void {
    const reserva = this.reserva();
    if(!reserva) return;
    this.sessao.iniciarEstadia(
      reserva.reservaId,
      reserva.quartoId,
      reserva.hospede.nome
    );
    void this.router.navigate(['/estadia', reserva.reservaId, 'chave']);
  }

  protected irPara(passo: Passo): void {
    this.erro.set(null);
    this.passo.set(passo);
  }

  protected voltar(): void {
    this.irPara(this.passo() === 3 ? 2 : 1);
  }

  protected recomecar(): void {
    this.reserva.set(null);
    this.irPara(1);
  }

  private iniciarChamada(): void {
    this.erro.set(null);
    this.carregando.set(true);
  }
}
