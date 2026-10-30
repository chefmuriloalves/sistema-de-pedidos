CREATE TABLE clientes (
    id UUID PRIMARY KEY,
    nome VARCHAR(160) NOT NULL
);

CREATE TABLE produtos (
    id UUID PRIMARY KEY,
    descricao VARCHAR(160) NOT NULL,
    preco NUMERIC(12, 2) NOT NULL CHECK (preco >= 0)
);

CREATE TABLE pedidos (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CRIADO', 'PROCESSANDO', 'FATURADO', 'CANCELADO')),
    total NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
    moeda VARCHAR(3) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_pedidos_cliente_criado ON pedidos (cliente_id, criado_em DESC, id DESC);
CREATE INDEX idx_pedidos_criado ON pedidos (criado_em DESC, id DESC);

CREATE TABLE itens_pedido (
    id UUID PRIMARY KEY,
    pedido_id UUID NOT NULL REFERENCES pedidos (id) ON DELETE CASCADE,
    produto_id UUID NOT NULL,
    descricao_produto VARCHAR(160) NOT NULL,
    quantidade INTEGER NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(12, 2) NOT NULL CHECK (preco_unitario >= 0),
    subtotal NUMERIC(14, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_itens_pedido_pedido ON itens_pedido (pedido_id);

CREATE TABLE pedido_idempotencia (
    chave VARCHAR(128) PRIMARY KEY,
    hash_payload CHAR(64) NOT NULL,
    pedido_id UUID UNIQUE REFERENCES pedidos (id),
    criado_em TIMESTAMPTZ NOT NULL
);

INSERT INTO clientes (id, nome) VALUES
    ('11111111-1111-4111-8111-111111111111', 'Cliente demonstração');

INSERT INTO produtos (id, descricao, preco) VALUES
    ('22222222-2222-4222-8222-222222222222', 'Teclado', 150.00),
    ('33333333-3333-4333-8333-333333333333', 'Mouse', 75.50);
