import { Component, computed, input } from '@angular/core';

@Component({
  imports: [],
  selector: 'app-passos',
  styleUrl: './passos.scss',
  templateUrl: './passos.html',
})
export class Passos {
  readonly atual = input.required<number>();
  readonly total = input(3);
  readonly rotulo = input('Etapas');
  protected readonly numeros = computed(() =>
    Array.from({ length: this.total() }, (_, i) => i + 1)
  );
}
