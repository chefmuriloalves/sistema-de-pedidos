package org.example.pedidos.application;

import org.example.pedidos.api.PedidoRequest;
import org.example.pedidos.api.PedidoResponse;
import org.example.pedidos.domain.StatusPedido;
import org.example.pedidos.persistence.ClienteRepository;
import org.example.pedidos.persistence.IdempotenciaRepository;
import org.example.pedidos.persistence.ItemPedido;
import org.example.pedidos.persistence.Pedido;
import org.example.pedidos.persistence.PedidoRepository;
import org.example.pedidos.persistence.Produto;
import org.example.pedidos.persistence.ProdutoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PedidoService {
    private static final String MOEDA = "BRL";
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("999999999999.99");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final IdempotenciaRepository idempotenciaRepository;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
                        ProdutoRepository produtoRepository, IdempotenciaRepository idempotenciaRepository) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.idempotenciaRepository = idempotenciaRepository;
    }

    @Transactional
    public PedidoResponse criar(String chave, PedidoRequest request) {
        String hash = hashPayload(request);
        if (idempotenciaRepository.reservar(chave, hash, Instant.now()) == 0) {
            var existente = idempotenciaRepository.findByChave(chave)
                    .orElseThrow(() -> new IllegalStateException("Registro idempotente não encontrado após conflito."));
            if (!existente.getHashPayload().equals(hash)) {
                throw new ConflitoIdempotenciaException();
            }
            UUID pedidoId = existente.getPedidoId();
            if (pedidoId == null) {
                throw new IllegalStateException("Registro idempotente sem pedido associado.");
            }
            return consultar(pedidoId);
        }

        if (!clienteRepository.existsById(request.clienteId())) {
            throw new RecursoNaoEncontradoException("Cliente não encontrado.");
        }
        var idsProduto = request.itens().stream().map(PedidoRequest.ItemRequest::produtoId).distinct().toList();
        Map<UUID, Produto> produtos = produtoRepository.findAllById(idsProduto).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Pedido pedido = new Pedido(UUID.randomUUID(), request.clienteId(), total, MOEDA, Instant.now());
        for (PedidoRequest.ItemRequest item : request.itens()) {
            Produto produto = produtos.get(item.produtoId());
            if (produto == null) {
                throw new RecursoNaoEncontradoException("Produto " + item.produtoId() + " não encontrado.");
            }
            BigDecimal preco = produto.getPreco().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = preco.multiply(BigDecimal.valueOf(item.quantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
            if (subtotal.compareTo(VALOR_MAXIMO) > 0) {
                throw new IllegalArgumentException("O subtotal do item excede o limite suportado.");
            }
            total = total.add(subtotal);
            if (total.compareTo(VALOR_MAXIMO) > 0) {
                throw new IllegalArgumentException("O total do pedido excede o limite suportado.");
            }
            pedido.adicionarItem(new ItemPedido(produto.getId(), produto.getDescricao(),
                    item.quantidade(), preco, subtotal));
        }
        pedido.atualizarTotal(total);
        Pedido salvo = pedidoRepository.saveAndFlush(pedido);
        idempotenciaRepository.associarPedido(chave, salvo.getId());
        return PedidoResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public PedidoResponse consultar(UUID id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        return PedidoResponse.de(pedido);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResponse> listar(UUID clienteId, int pagina, int tamanho) {
        PageRequest pageable = PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
        Page<Pedido> resultado = clienteId == null
                ? pedidoRepository.findAll(pageable)
                : pedidoRepository.findByClienteId(clienteId, pageable);
        return resultado.map(PedidoResponse::de);
    }

    @Transactional
    public PedidoResponse alterarStatus(UUID id, StatusPedido status, long versaoEsperada) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
        if (pedido.getVersao() != versaoEsperada) {
            throw new PrecondicaoVersaoException();
        }
        pedido.alterarStatus(status);
        return PedidoResponse.de(pedidoRepository.saveAndFlush(pedido));
    }

    private String hashPayload(PedidoRequest request) {
        String canonical = request.clienteId() + "|" + request.itens().stream()
                .map(item -> item.produtoId() + ":" + item.quantidade())
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }
}
