# Homepage Backend

API Spring Boot 4 / Java 25 que guarda no PostgreSQL as categorias e os cards (nome, link, icone) do dashboard.

## Rodando

1. Suba o Postgres: `docker compose up -d` (ou use um Postgres proprio e ajuste `DB_URL`, `DB_USER`, `DB_PASSWORD`).
2. No IntelliJ: *File > Open* na pasta, deixe importar o `pom.xml`, use o JDK 25 e rode `HomepageApplication`.
   Ou via terminal: `mvn spring-boot:run`.

As tabelas `catalog_category` e `catalog_card` sao criadas automaticamente. A API sobe em `http://localhost:8080`.

## Endpoints

| Metodo | Rota | Descricao |
|--------|------|-----------|
| GET | `/api/catalog` | Categorias com seus cards (`services`), na ordem de exibicao |
| POST | `/api/categories` | Cria categoria — `{ "label": "..." }` |
| PUT | `/api/categories/{id}` | Renomeia categoria |
| DELETE | `/api/categories/{id}` | Remove a categoria e seus cards |
| POST | `/api/categories/{id}/cards` | Cria card — `{ "name", "url", "description?", "icon?" }` |
| PUT | `/api/cards/{id}` | Atualiza card |
| DELETE | `/api/cards/{id}` | Remove card |

`url` aceita apenas `http://` e `https://`. `icon` e a chave de um icone embutido no frontend (ex.: `proxmox`) ou uma URL.

Na primeira execucao (banco vazio) o catalogo padrao e carregado de `src/main/resources/seed-catalog.json`.
Links salvos na versao anterior (tabela `service_link`) sao reaproveitados nesse momento.

## Variaveis de ambiente

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT`, `CORS_ALLOWED_ORIGINS`.

## Estrutura (MVC)

```
com.homelab.homepage
├── HomepageApplication
├── config       CorsConfig, CatalogSeedLoader (carga inicial do catalogo)
├── controller   CatalogController            (C - rotas REST)
├── service      CatalogService               (regras de negocio)
├── repository   CategoryRepository, CardRepository
├── model        Category, Card               (M - entidades JPA)
├── dto          CategoryRequest/Response, CardRequest/Response (V - o que a API expoe)
├── mapper       CatalogMapper                (entidade -> DTO)
└── exception    ResourceNotFoundException, GlobalExceptionHandler
```
