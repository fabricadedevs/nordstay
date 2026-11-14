package br.com.nordstay.reservation_service.domain.model;

import java.util.Objects;

public final class Documento {
    private final String digitos;

    public Documento(String valor) {
        Objects.requireNonNull(valor, "Documento é obrigatório");

        String normalizado = valor.replace(".", "").replace("-", "").replace(" ", "");
        if (!normalizado.matches("\\d{11}")) {
            throw new IllegalArgumentException("Documento deve conter 11 dígitos");
        }

        this.digitos = normalizado;
    }

    public String ultimosQuatro() {
        return digitos.substring(7);
    }

    public String mascarado() {
        return "***.***.*" + digitos.substring(6, 9) + "-" + digitos.substring(9);
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        if (!(objeto instanceof Documento outro)) {
            return false;
        }
        return digitos.equals(outro.digitos);
    }

    @Override
    public int hashCode() {
        return digitos.hashCode();
    }

    @Override
    public String toString() {
        return "Documento{valor=[REDACTED]}";
    }
}