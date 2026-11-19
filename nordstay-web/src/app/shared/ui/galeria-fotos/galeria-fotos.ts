import { Component, ElementRef, input, signal, viewChild, viewChildren } from '@angular/core';

const DISTANCIA_SWIPE = 50;

export interface FotoGaleria {
  readonly src: string;
  readonly legenda: string;
}

/**
 * Galeria em tela cheia (lightbox) usando <dialog> nativo:
 * Esc fecha, setas do teclado navegam e o foco fica preso no modal.
 * Uso: <ns-galeria-fotos #galeria [fotos]="..."/> e (click)="galeria.abrir(i)".
 */
@Component({
  selector: 'ns-galeria-fotos',
  styleUrl: './galeria-fotos.scss',
  templateUrl: './galeria-fotos.html',
})
export class GaleriaFotos {
  readonly fotos = input.required<readonly FotoGaleria[]>();
  readonly titulo = input('Fotos');

  protected readonly atual = signal(0);
  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');
  private readonly miniaturas = viewChildren<ElementRef<HTMLButtonElement>>('miniatura');

  abrir(indice = 0): void {
    this.ir(indice);
    const d = this.dialogo().nativeElement;
    if (d.open) return;
    if (typeof d.showModal === 'function') d.showModal();
    else d.setAttribute('open', '');
  }

  fechar(): void {
    const d = this.dialogo().nativeElement;
    if (typeof d.close === 'function') d.close();
    else d.removeAttribute('open');
  }

  protected ir(indice: number): void {
    const total = this.fotos().length;
    if (!total) return;
    const novo = ((indice % total) + total) % total;
    this.atual.set(novo);
    this.miniaturas()[novo]?.nativeElement.scrollIntoView?.({ block: 'nearest', inline: 'center' });
  }

  protected anterior(): void {
    this.ir(this.atual() - 1);
  }

  protected proxima(): void {
    this.ir(this.atual() + 1);
  }

  protected teclado(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowLeft') this.anterior();
    else if (evento.key === 'ArrowRight') this.proxima();
    else return;
    evento.preventDefault();
  }

  private inicioToque: number | null = null;

  /** Arrastar o dedo para os lados troca a foto (celular). */
  protected comecarToque(evento: PointerEvent): void {
    this.inicioToque = evento.clientX;
  }

  protected terminarToque(evento: PointerEvent): void {
    if (this.inicioToque === null) return;
    const deslocamento = evento.clientX - this.inicioToque;
    this.inicioToque = null;
    if (Math.abs(deslocamento) < DISTANCIA_SWIPE) return;
    if (deslocamento < 0) this.proxima();
    else this.anterior();
  }

  /** Clique fora da foto (no fundo do diálogo) fecha. */
  protected cliqueFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) this.fechar();
  }
}
