package org.example.pedidos.domain;

import org.example.pedidos.persistence.Pedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoTest {
    @Test
    void permiteTransicoesAssumidasERejeitaCancelamentoAposProcessamento() {
        Pedido pedido = new Pedido(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("10.00"), "BRL", Instant.now());

        pedido.alterarStatus(StatusPedido.PROCESSANDO);
        assertEquals(StatusPedido.PROCESSANDO, pedido.getStatus());
        assertThrows(TransicaoPedidoInvalidaException.class,
                () -> pedido.alterarStatus(StatusPedido.CANCELADO));

        pedido.alterarStatus(StatusPedido.FATURADO);
        assertEquals(StatusPedido.FATURADO, pedido.getStatus());
        assertThrows(TransicaoPedidoInvalidaException.class,
                () -> pedido.alterarStatus(StatusPedido.PROCESSANDO));
    }
}
