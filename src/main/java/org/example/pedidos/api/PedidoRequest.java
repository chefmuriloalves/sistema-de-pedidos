package org.example.pedidos.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record PedidoRequest(
        @NotNull UUID clienteId,
        @NotEmpty List<@Valid ItemRequest> itens
) {
    public record ItemRequest(
            @NotNull UUID produtoId,
            @Positive int quantidade
    ) {
    }
}
