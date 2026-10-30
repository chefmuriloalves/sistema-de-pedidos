package org.example.pedidos.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pedido_idempotencia")
public class RegistroIdempotencia {
    @Id
    @Column(name = "chave", length = 128)
    private String chave;

    @Column(name = "hash_payload", nullable = false, length = 64)
    private String hashPayload;

    @Column(name = "pedido_id")
    private UUID pedidoId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RegistroIdempotencia() {
    }

    public String getHashPayload() {
        return hashPayload;
    }

    public UUID getPedidoId() {
        return pedidoId;
    }
}
