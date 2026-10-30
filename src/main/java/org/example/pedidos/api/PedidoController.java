package org.example.pedidos.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.pedidos.application.PedidoService;
import org.example.pedidos.domain.StatusPedido;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoController {
    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody PedidoRequest request) {
        if (chave.isBlank() || chave.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key deve conter entre 1 e 128 caracteres.");
        }
        PedidoResponse response = pedidoService.criar(chave, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> consultar(@PathVariable UUID id) {
        PedidoResponse pedido = pedidoService.consultar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + pedido.versao() + "\"")
                .body(pedido);
    }

    @GetMapping
    public PaginaResponse<PedidoResponse> listar(
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        Page<PedidoResponse> page = pedidoService.listar(clienteId, pagina, tamanho);
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PedidoResponse> alterarStatus(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody AlterarStatusRequest request) {
        long versao = parseIfMatch(ifMatch);
        PedidoResponse pedido = pedidoService.alterarStatus(id, request.status(), versao);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + pedido.versao() + "\"")
                .body(pedido);
    }

    private long parseIfMatch(String ifMatch) {
        if (ifMatch == null) {
            throw new PreconditionRequiredException();
        }
        try {
            String value = ifMatch.trim();
            if (!value.matches("\"\\d+\"")) {
                throw new NumberFormatException();
            }
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match deve conter a versão no formato ETag, por exemplo \"0\".");
        }
    }

    public record AlterarStatusRequest(@jakarta.validation.constraints.NotNull StatusPedido status) {
    }
}
