package org.example.pedidos.api;

public class PreconditionRequiredException extends RuntimeException {
    public PreconditionRequiredException() {
        super("O cabeçalho If-Match é obrigatório para alterar o status.");
    }
}
