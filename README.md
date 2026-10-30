# Sistema de Pedidos

API REST para criação e consulta de pedidos, construída com Java 17, Spring Boot e PostgreSQL. O servidor consulta os preços do catálogo, calcula os valores do pedido e persiste os dados usando migrations Flyway.

## Requisitos

- JDK 17
- Docker Desktop com o mecanismo de containers Linux iniciado
- Acesso à internet na primeira execução, para baixar o Maven e as imagens Docker

O Maven é fornecido pelo Wrapper do projeto; não é necessário instalá-lo separadamente.

## Executar localmente no Windows

Abra o PowerShell na pasta do projeto. Inicie o PostgreSQL:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
docker compose ps
```

Espere o serviço `postgres` ficar saudável. A configuração local padrão é banco `pedidos`, usuário e senha `pedidos`, porta `55432`. Depois, inicie a API em outro terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

A aplicação fica disponível em `http://localhost:8080`. Confirme que iniciou corretamente:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

As migrations em `src/main/resources/db/migration` são executadas automaticamente na inicialização. O Hibernate valida o schema (`ddl-auto: validate`), mas não o cria nem atualiza.

O Compose lê o arquivo `.env`; a aplicação Spring Boot não lê esse arquivo automaticamente. Os valores padrão da aplicação coincidem com o banco local. Para executar com valores personalizados, carregue as variáveis no terminal da aplicação antes de iniciá-la:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') {
        Set-Item -Path "Env:$($matches[1])" -Value $matches[2]
    }
}
.\mvnw.cmd spring-boot:run
```

Se alterar `POSTGRES_PORT`, atualize também a porta em `DB_URL`. As credenciais padrão são exclusivamente para desenvolvimento local; use credenciais apropriadas em outros ambientes.

## Executar em Linux ou macOS

Com Docker e JDK 17 instalados:

```shell
docker compose up -d postgres
./mvnw spring-boot:run
```

## Executar os testes

Os testes de integração iniciam PostgreSQL 16 em um container Testcontainers. Mantenha o Docker Desktop ativo durante a execução.

No PowerShell:

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw.cmd '-Dapi.version=1.44' test
```

No Linux ou macOS:

```shell
./mvnw test
```

Confira o resumo do Maven: os testes de integração devem aparecer como executados, sem ignorados. O teste de domínio pode ser executado isoladamente sem Docker:

```powershell
.\mvnw.cmd -Dtest=PedidoTest test
```

## Gerar e executar o pacote

Com Docker ativo, compile, execute todos os testes e empacote a aplicação:

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw.cmd '-Dapi.version=1.44' clean verify
```

O JAR executável gerado é `target/sistema-pedidos-1.0.0.jar`. Com o PostgreSQL ainda ativo, execute:

```powershell
java -jar .\target\sistema-pedidos-1.0.0.jar
```

Para encerrar o PostgreSQL sem apagar os dados:

```powershell
docker compose down
```

Para remover também o volume e todos os dados locais do banco:

```powershell
docker compose down -v
```

## API

URL base: `http://localhost:8080/api/v1`.

### Criar pedido

O cabeçalho `Idempotency-Key` é obrigatório. Envie o mesmo valor ao repetir uma tentativa da mesma criação.

```powershell
curl.exe -i -X POST http://localhost:8080/api/v1/pedidos `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: pedido-cliente-001" `
  -d '{"clienteId":"11111111-1111-4111-8111-111111111111","itens":[{"produtoId":"22222222-2222-4222-8222-222222222222","quantidade":2}]}'
```

O servidor retorna `201 Created`, o pedido no corpo e o cabeçalho `Location`. Preços, subtotais e total são obtidos/calculados no servidor; o cliente envia somente IDs e quantidades. O preço e a descrição são gravados no pedido como snapshot.

IDs disponíveis no catálogo inicial:

| Recurso | ID | Preço |
|---|---|---:|
| Cliente demonstração | `11111111-1111-4111-8111-111111111111` | — |
| Teclado | `22222222-2222-4222-8222-222222222222` | R$ 150,00 |
| Mouse | `33333333-3333-4333-8333-333333333333` | R$ 75,50 |

Uma repetição com a mesma chave e o mesmo conteúdo retorna o mesmo pedido. Reutilizar a chave com conteúdo diferente retorna `409 Conflict`. As chaves são armazenadas no PostgreSQL e não expiram nesta versão.

### Consultar e listar

Substitua `{id}` pelo UUID do pedido:

```powershell
curl.exe -i "http://localhost:8080/api/v1/pedidos/{id}"
curl.exe "http://localhost:8080/api/v1/pedidos?clienteId=11111111-1111-4111-8111-111111111111&pagina=0&tamanho=20"
```

A consulta retorna `200 OK` e um `ETag` com a versão do pedido. A listagem é ordenada por data e ID decrescentes, aceita filtro opcional por cliente e limita `tamanho` a 100 (padrão 20).

### Alterar status ou cancelar

A alteração exige o `ETag` recebido na consulta, enviado no cabeçalho `If-Match`:

```powershell
curl.exe -i -X PATCH "http://localhost:8080/api/v1/pedidos/{id}/status" `
  -H "Content-Type: application/json" `
  -H 'If-Match: "0"' `
  -d '{"status":"PROCESSANDO"}'
```

Transições permitidas: `CRIADO -> PROCESSANDO`, `CRIADO -> CANCELADO` e `PROCESSANDO -> FATURADO`. A resposta contém o novo `ETag`. Transição inválida ou chave idempotente em conflito retorna `409`; versão desatualizada retorna `412`; ausência de `If-Match` retorna `428`.

## Decisões e escopo

- PostgreSQL é a fonte de verdade; Flyway versiona e aplica o schema e os dados iniciais.
- Valores monetários usam `BigDecimal`, moeda BRL e duas casas decimais.
- A idempotência usa uma restrição única no PostgreSQL, compartilhada entre instâncias.
- A alteração de status usa concorrência otimista (`@Version`), `ETag` e `If-Match`.
- O catálogo e o cliente são dados de demonstração incluídos na migration inicial; não existem endpoints para administrá-los.
- Descontos, tributos, autenticação, broker, processamento assíncrono e rate limiting não fazem parte desta implementação.
- Os testes cobrem criação e cálculo, idempotência, consultas, listagem e versão/transições de status; não são teste de carga.
- O health check está em `/actuator/health` e as métricas Micrometer em `/actuator/metrics`.
