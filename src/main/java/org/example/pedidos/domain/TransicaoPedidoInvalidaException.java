package org.example.pedidos.domain;

public class TransicaoPedidoInvalidaException extends RuntimeException {
    public TransicaoPedidoInvalidaException(StatusPedido atual, StatusPedido solicitado) {
        super("Não é permitido alterar o pedido de " + atual + " para " + solicitado + ".");
    }
}
