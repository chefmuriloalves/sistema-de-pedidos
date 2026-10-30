package org.example.pedidos.application;

public class ConflitoIdempotenciaException extends RuntimeException {
    public ConflitoIdempotenciaException() {
        super("A chave Idempotency-Key já foi usada com outro conteúdo.");
    }
}
