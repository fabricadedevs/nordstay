package br.com.nordstay.reservation_service.domain.model;

import java.util.Objects;

/**
 * Documento do hóspede como o serviço o conhece: apenas a forma mascarada
 * (para exibição) e os quatro últimos dígitos (para conferir a consulta).
 * O número completo nunca é armazenado nem trafega por este serviço.
 */
public final class Documento {
    private final String mascarado;
    private final String ultimosQuatro;

    public Documento(String mascarado, String ultimosQuatro) {
        Objects.requireNonNull(mascarado, "Documento mascarado é obrigatório");
        Objects.requireNonNull(ultimosQuatro, "Final do documento é obrigatório");

        if (mascarado.isBlank()) {
            throw new IllegalArgumentException("Documento mascarado é obrigatório");
        }
        if (mascarado.replaceAll("\\D", "").length() >= 11) {
            throw new IllegalArgumentException("Documento mascarado não pode conter o número completo");
        }
        if (!ultimosQuatro.matches("\\d{4}")) {
            throw new IllegalArgumentException("Final do documento deve conter 4 dígitos");
        }

        this.mascarado = mascarado;
        this.ultimosQuatro = ultimosQuatro;
    }

    public String ultimosQuatro() {
        return ultimosQuatro;
    }

    public String mascarado() {
        return mascarado;
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Documento outro)) {
            return false;
        }
        return mascarado.equals(outro.mascarado) && ultimosQuatro.equals(outro.ultimosQuatro);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mascarado, ultimosQuatro);
    }

    @Override
    public String toString() {
        return "Documento{valor=[REDACTED]}";
    }
}
