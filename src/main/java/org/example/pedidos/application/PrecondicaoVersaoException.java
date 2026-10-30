package org.example.pedidos.application;

public class PrecondicaoVersaoException extends RuntimeException {
    public PrecondicaoVersaoException() {
        super("A versão informada não corresponde à versão atual do pedido.");
    }
}
