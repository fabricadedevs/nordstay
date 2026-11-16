import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { environment } from '../environments/environment';
import { PainelCenariosComponent } from './core/mocks/painel-cenarios.component';

@Component({
  imports: [RouterOutlet, PainelCenariosComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly usaMocks = environment.useMocks;
}
