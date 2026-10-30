package org.example.pedidos.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.example.pedidos.domain.StatusPedido;
import org.example.pedidos.domain.TransicaoPedidoInvalidaException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
public class Pedido {
    @Id
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedido status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    @BatchSize(size = 25)
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> itens = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(UUID id, UUID clienteId, BigDecimal total, String moeda, Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.total = total;
        this.moeda = moeda;
        this.criadoEm = criadoEm;
        this.status = StatusPedido.CRIADO;
    }

    public void adicionarItem(ItemPedido item) {
        itens.add(item);
        item.associarPedido(this);
    }

    public void atualizarTotal(BigDecimal total) {
        this.total = total;
    }

    public void alterarStatus(StatusPedido novoStatus) {
        boolean permitido = switch (status) {
            case CRIADO -> novoStatus == StatusPedido.PROCESSANDO || novoStatus == StatusPedido.CANCELADO;
            case PROCESSANDO -> novoStatus == StatusPedido.FATURADO;
            case FATURADO, CANCELADO -> false;
        };
        if (!permitido) {
            throw new TransicaoPedidoInvalidaException(status, novoStatus);
        }
        status = novoStatus;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getMoeda() {
        return moeda;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public long getVersao() {
        return versao;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }
}
