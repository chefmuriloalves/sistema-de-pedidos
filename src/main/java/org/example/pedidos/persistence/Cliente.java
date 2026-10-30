package org.example.pedidos.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "clientes")
public class Cliente {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String nome;

    protected Cliente() {
    }
}
