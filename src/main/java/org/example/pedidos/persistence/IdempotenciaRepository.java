package org.example.pedidos.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotenciaRepository extends JpaRepository<RegistroIdempotencia, String> {
    @Modifying
    @Query(value = """
            INSERT INTO pedido_idempotencia (chave, hash_payload, criado_em)
            VALUES (:chave, :hash, :criadoEm)
            ON CONFLICT (chave) DO NOTHING
            """, nativeQuery = true)
    int reservar(@Param("chave") String chave, @Param("hash") String hash, @Param("criadoEm") Instant criadoEm);

    @Modifying
    @Query(value = "UPDATE pedido_idempotencia SET pedido_id = :pedidoId WHERE chave = :chave", nativeQuery = true)
    int associarPedido(@Param("chave") String chave, @Param("pedidoId") java.util.UUID pedidoId);

    Optional<RegistroIdempotencia> findByChave(String chave);
}
