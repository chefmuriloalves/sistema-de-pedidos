package org.example.pedidos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PedidoApiIntegrationTest {
    private static final String CLIENTE = "11111111-1111-4111-8111-111111111111";
    private static final String TECLADO = "22222222-2222-4222-8222-222222222222";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void criaCalculaNoServidorEDevolveMesmoPedidoNoRetry() throws Exception {
        String payload = """
                {"clienteId":"%s","itens":[{"produtoId":"%s","quantidade":2}]}
                """.formatted(CLIENTE, TECLADO);

        MvcResult primeiraResposta = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Idempotency-Key", "teste-criacao-unica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.total").value(300.00))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(150.00))
                .andReturn();

        String primeiroId = com.jayway.jsonpath.JsonPath.read(primeiraResposta.getResponse().getContentAsString(), "$.id");
        String retryId = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/v1/pedidos")
                        .header("Idempotency-Key", "teste-criacao-unica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(retryId).isEqualTo(primeiroId);
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Idempotency-Key", "teste-criacao-unica")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":"%s","itens":[{"produtoId":"%s","quantidade":1}]}
                                """.formatted(CLIENTE, TECLADO)))
                .andExpect(status().isConflict());
    }

    @Test
    void consultaListaEControlaVersaoDeStatus() throws Exception {
        String payload = """
                {"clienteId":"%s","itens":[{"produtoId":"%s","quantidade":1}]}
                """.formatted(CLIENTE, TECLADO);
        String body = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Idempotency-Key", "teste-status-unico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/pedidos/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""));
        mockMvc.perform(get("/api/v1/pedidos").param("clienteId", CLIENTE).param("tamanho", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo").isArray());

        mockMvc.perform(patch("/api/v1/pedidos/" + id + "/status")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSANDO\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
        mockMvc.perform(patch("/api/v1/pedidos/" + id + "/status")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void rejeitaPedidoComTotalMaiorQuePrecisaoSuportadaPeloBanco() throws Exception {
        jdbcTemplate.update("UPDATE produtos SET preco = 9999999999.99 WHERE id = ?", UUID.fromString(TECLADO));
        try {
            mockMvc.perform(post("/api/v1/pedidos")
                            .header("Idempotency-Key", "teste-total-excedido")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"clienteId":"%s","itens":[{"produtoId":"%s","quantidade":101}]}
                                    """.formatted(CLIENTE, TECLADO)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("O subtotal do item excede o limite suportado."));
        } finally {
            jdbcTemplate.update("UPDATE produtos SET preco = 150.00 WHERE id = ?", UUID.fromString(TECLADO));
        }
    }
}
