import { Component, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FotoGaleria, GaleriaFotos } from './galeria-fotos';

const FOTOS: FotoGaleria[] = [
  { src: '/a.jpg', legenda: 'Foto A' },
  { src: '/b.jpg', legenda: 'Foto B' },
  { src: '/c.jpg', legenda: 'Foto C' },
];

@Component({
  imports: [GaleriaFotos],
  template: `<ns-galeria-fotos [fotos]="fotos" />`,
})
class Host {
  readonly fotos = FOTOS;
  readonly galeria = viewChild.required(GaleriaFotos);
}

describe('GaleriaFotos', () => {
  function montar() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const dialogo = el.querySelector('dialog')!;
    const legenda = () => el.querySelector('figcaption')?.textContent?.trim();
    const contador = () => el.querySelector('.contador')?.textContent?.trim();
    return { fixture, el, dialogo, legenda, contador, galeria: fixture.componentInstance.galeria() };
  }

  it('abre na foto clicada e fecha', () => {
    const { fixture, dialogo, legenda, contador, galeria } = montar();
    galeria.abrir(1);
    fixture.detectChanges();
    expect(dialogo.hasAttribute('open')).toBe(true);
    expect(legenda()).toBe('Foto B');
    expect(contador()).toBe('2 / 3');

    galeria.fechar();
    expect(dialogo.hasAttribute('open')).toBe(false);
  });

  it('navega pelas setas e dá a volta nas pontas', () => {
    const { fixture, el, legenda, galeria } = montar();
    galeria.abrir(0);
    fixture.detectChanges();

    (el.querySelector('.seta--anterior') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(legenda()).toBe('Foto C');

    (el.querySelector('.seta--proxima') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(legenda()).toBe('Foto A');
  });

  it('aceita as setas do teclado e as miniaturas', () => {
    const { fixture, el, dialogo, legenda, galeria } = montar();
    galeria.abrir(0);
    fixture.detectChanges();

    dialogo.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(legenda()).toBe('Foto B');

    (el.querySelectorAll('.miniaturas button')[2] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(legenda()).toBe('Foto C');
    expect(el.querySelector('.miniaturas .ativa img')?.getAttribute('src')).toBe('/c.jpg');
  });

  it('troca de foto ao arrastar para os lados', () => {
    const { fixture, el, legenda, galeria } = montar();
    galeria.abrir(0);
    fixture.detectChanges();
    const palco = el.querySelector('.palco')!;
    const arrastar = (de: number, ate: number) => {
      palco.dispatchEvent(new MouseEvent('pointerdown', { clientX: de }));
      palco.dispatchEvent(new MouseEvent('pointerup', { clientX: ate }));
      fixture.detectChanges();
    };

    arrastar(300, 100);
    expect(legenda()).toBe('Foto B');
    arrastar(100, 300);
    expect(legenda()).toBe('Foto A');
    arrastar(100, 120);
    expect(legenda()).toBe('Foto A');
  });
});
