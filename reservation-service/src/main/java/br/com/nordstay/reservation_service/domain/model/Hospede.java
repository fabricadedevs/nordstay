package br.com.nordstay.reservation_service.domain.model;

import java.util.Objects;

public record Hospede(String nome, Documento documento) {
    public Hospede {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do hóspede é obrigatório");
        }
        Objects.requireNonNull(documento, "Documento é obrigatório");
    }
}