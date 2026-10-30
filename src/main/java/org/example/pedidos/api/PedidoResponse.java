package org.example.pedidos.api;

import org.example.pedidos.domain.StatusPedido;
import org.example.pedidos.persistence.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PedidoResponse(
        UUID id,
        UUID clienteId,
        StatusPedido status,
        BigDecimal total,
        String moeda,
        Instant criadoEm,
        long versao,
        List<ItemResponse> itens
) {
    public static PedidoResponse de(Pedido pedido) {
        List<ItemResponse> itens = pedido.getItens().stream()
                .map(item -> new ItemResponse(item.getProdutoId(), item.getDescricaoProduto(),
                        item.getQuantidade(), item.getPrecoUnitario(), item.getSubtotal()))
                .toList();
        return new PedidoResponse(pedido.getId(), pedido.getClienteId(), pedido.getStatus(),
                pedido.getTotal(), pedido.getMoeda(), pedido.getCriadoEm(), pedido.getVersao(), itens);
    }

    public record ItemResponse(
            UUID produtoId,
            String descricao,
            int quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotal
    ) {
    }
}
