# Documentação do Homepage Backend

Oct 11, 2026 · @Weslei

## 1. Visão geral

O Homepage Backend é uma API REST que guarda no PostgreSQL as categorias e os cards (nome, link, ícone) exibidos no dashboard Homelab. O frontend lê o catálogo completo em uma chamada e usa as demais rotas para criar, renomear, editar e remover categorias e cards.

| Item | Valor |
| --- | --- |
| Nome do artefato | `com.homelab:homepage-backend:0.0.1-SNAPSHOT` |
| Package base | `com.homelab.homepage` |
| Linguagem | Java 25 |
| Framework | Spring Boot 4.0.6 (`spring-boot-starter-parent`) |
| Web | `spring-boot-starter-webmvc` |
| Persistência | `spring-boot-starter-data-jpa` (Hibernate) + driver PostgreSQL |
| Validação | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Banco de dados | PostgreSQL (subido via `compose.yaml`) |
| Build | Maven (wrapper `mvnw` incluído) |
| Porta padrão | 8080 (variável `PORT`) |

O acesso é protegido por login (JWT) e por roles: `USER` só visualiza o catálogo e `ADMIN` também cria, edita e remove (ver seção 10).

## 2. Arquitetura e estrutura de packages

O projeto segue o padrão MVC adaptado a uma API REST: o *Model* são as entidades JPA, a *View* é a representação JSON exposta pelos DTOs e o *Controller* recebe as requisições HTTP. Entre o controller e o banco existem as camadas de serviço (regras de negócio) e de repositório (acesso a dados). Cada camada só conhece a camada logo abaixo.

&#91;embedded content: camadas da API · 5 níveis, de cima para baixo\]

A requisição desce do frontend até o banco e a resposta volta pelo mesmo caminho; a regra de negócio fica no serviço (destacado).

| Package | Classes | Responsabilidade |
| --- | --- | --- |
| `com.homelab.homepage` | `HomepageApplication` | Ponto de entrada (`@SpringBootApplication`). |
| `controller` | `CatalogController` | Mapeia as rotas `/api/**`, valida o corpo (`@Valid`) e delega ao serviço. |
| `service` | `CatalogService` | Regras de negócio, transações, normalização de textos e cálculo de posição. |
| `repository` | `CategoryRepository`, `CardRepository` | Acesso ao banco via Spring Data JPA e consultas JPQL. |
| `model` | `Category`, `Card` | Entidades JPA mapeadas para as tabelas do banco. |
| `dto` | `CategoryRequest`, `CategoryResponse`, `CardRequest`, `CardResponse` | Contratos de entrada e saída da API (records imutáveis) e regras de validação. |
| `mapper` | `CatalogMapper` | Converte entidades em DTOs de resposta. |
| `exception` | `ResourceNotFoundException`, `GlobalExceptionHandler` | Erro de "não encontrado" e tradução de erros em respostas HTTP. |
| `config` | `CorsConfig`, `CatalogSeedLoader` | Configuração de CORS e carga inicial do catálogo. |

### Fluxo de uma requisição

1. O cliente envia a requisição HTTP para `/api/...`; o `CorsConfig` libera as origens configuradas.
2. O `CatalogController` desserializa o JSON em um DTO de entrada e o Bean Validation executa as anotações (`@NotBlank`, `@Size`, `@Pattern`).
3. Se a validação falhar, o `GlobalExceptionHandler` responde `400` e o serviço nem é chamado.
4. O `CatalogService` abre uma transação, busca ou grava as entidades pelos repositórios e aplica as regras (trim, `blankToNull`, próxima posição).
5. Se um registro não existir, o serviço lança `ResourceNotFoundException` e o handler responde `404`.
6. O `CatalogMapper` converte a entidade em DTO de resposta, que o controller devolve como JSON.

## 3. Modelo de dados

O banco tem duas tabelas em relação um-para-muitos: uma categoria possui vários cards. Apagar uma categoria apaga seus cards (`cascade = ALL` e `orphanRemoval = true`). As tabelas são criadas e atualizadas automaticamente pelo Hibernate (`ddl-auto: update`).

### Tabela `catalog_category` (entidade `Category`)

| Coluna | Campo Java | Tipo | Restrições |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | Chave primária, gerada por `IDENTITY`. |
| `label` | `label` | `String` | Obrigatório, até 100 caracteres. |
| `position` | `position` | `int` | Obrigatório. Ordem de exibição entre as categorias. |

O campo `cards` (`List<Card>`) é o lado inverso da relação (`mappedBy = "category"`) e vem ordenado por `position ASC, id ASC`.

### Tabela `catalog_card` (entidade `Card`)

| Coluna | Campo Java | Tipo | Restrições |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | Chave primária, gerada por `IDENTITY`. |
| `category_id` | `category` | `Category` | Chave estrangeira obrigatória, carregamento `LAZY`. |
| `name` | `name` | `String` | Obrigatório, até 100 caracteres. |
| `url` | `url` | `String` | Obrigatório, até 2048 caracteres. |
| `description` | `description` | `String` | Opcional, até 255 caracteres. |
| `icon` | `icon` | `String` | Opcional, até 2048 caracteres. Chave de ícone embutido no frontend (ex.: `proxmox`) ou URL http(s). |
| `position` | `position` | `int` | Obrigatório. Ordem do card dentro da categoria. |

### Posição (ordenação)

Novas categorias e novos cards entram sempre no final. A posição vem de uma consulta `coalesce(max(position), -1) + 1`: para categorias considera todas as linhas e para cards considera só a categoria do card. Não há rota para reordenar; remover um item deixa um buraco na sequência, o que não afeta a ordenação.

## 4. Referência da API REST

Todas as rotas ficam sob o prefixo `/api`, trocam JSON (`Content-Type: application/json`) e estão no `CatalogController`. A base local é `http://localhost:8080`.

| Método | Rota | Corpo | Sucesso | Método do serviço |
| --- | --- | --- | --- | --- |
| GET | `/api/catalog` | — | 200 + lista de `CategoryResponse` | `listCatalog` |
| POST | `/api/categories` | `CategoryRequest` | 201 + `CategoryResponse` | `createCategory` |
| PUT | `/api/categories/{id}` | `CategoryRequest` | 200 + `CategoryResponse` | `renameCategory` |
| DELETE | `/api/categories/{id}` | — | 204 sem corpo | `deleteCategory` |
| POST | `/api/categories/{id}/cards` | `CardRequest` | 201 + `CardResponse` | `createCard` |
| PUT | `/api/cards/{id}` | `CardRequest` | 200 + `CardResponse` | `updateCard` |
| DELETE | `/api/cards/{id}` | — | 204 sem corpo | `deleteCard` |

### Objetos de entrada

`CategoryRequest`

| Campo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `label` | texto | Sim | Não pode ser vazio; até 100 caracteres. O serviço aplica `trim()`. |

`CardRequest`

| Campo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `name` | texto | Sim | Não pode ser vazio; até 100 caracteres. |
| `url` | texto | Sim | Até 2048 caracteres e deve casar com `(?i)^https?://\S+$`, ou seja, só `http://` ou `https://`, sem espaços. Bloqueia esquemas como `javascript:`. |
| `description` | texto | Não | Até 255 caracteres. Vazio ou só espaços vira `null`. |
| `icon` | texto | Não | Até 2048 caracteres; uma chave de até 60 letras, números ou hífens (`proxmox`) ou uma URL http(s). Omitido ou `null` fica `null`; texto vazio é rejeitado (400). |

### Objetos de saída

`CategoryResponse`: `id`, `label` e `services` (lista de `CardResponse`). O nome `services` é mantido por compatibilidade com o frontend, embora internamente sejam cards.

`CardResponse`: `id`, `name`, `url`, `description`, `icon`.

### Exemplos

Listar o catálogo:

```json
GET /api/catalog

200 OK
[
  {
    "id": 1,
    "label": "Infraestrutura",
    "services": [
      {
        "id": 1,
        "name": "Proxmox",
        "url": "https://proxmox.local:8006",
        "description": "Virtualizacao (VMs e LXC)",
        "icon": "proxmox"
      }
    ]
  }
]
```

Criar um card:

```json
POST /api/categories/1/cards
{ "name": "Grafana", "url": "http://192.168.3.10:3000", "description": "Metricas", "icon": "grafana" }

201 Created
{ "id": 12, "name": "Grafana", "url": "http://192.168.3.10:3000", "description": "Metricas", "icon": "grafana" }
```

Criar uma categoria:

```json
POST /api/categories
{ "label": "Monitoramento" }

201 Created
{ "id": 4, "label": "Monitoramento", "services": [] }
```

### Comportamentos a conhecer

- `PUT /api/categories/{id}` só renomeia; os cards não são alterados.
- `PUT /api/cards/{id}` substitui nome, URL, descrição e ícone. Omitir `description` ou `icon` os grava como `null`. A categoria e a posição do card não mudam, então não dá para mover um card de categoria.
- `DELETE` de categoria remove todos os cards dela.

## 5. Validações e tratamento de erros

A API devolve dois tipos de erro, ambos com o mesmo formato de corpo: `{ "message": "texto do erro" }`. O tratamento é centralizado no `GlobalExceptionHandler` (`@RestControllerAdvice`).

| Situação | Exceção | Status | Mensagem |
| --- | --- | --- | --- |
| Corpo viola uma regra de validação | `MethodArgumentNotValidException` | 400 Bad Request | A mensagem do primeiro campo com erro; se não houver, `Requisicao invalida`. |
| Categoria inexistente | `ResourceNotFoundException` | 404 Not Found | `Categoria nao encontrada` |
| Card inexistente | `ResourceNotFoundException` | 404 Not Found | `Card nao encontrado` |

### Mensagens de validação

| Campo | Condição violada | Mensagem |
| --- | --- | --- |
| `CategoryRequest.label` | Vazio | `O nome da categoria e obrigatorio` |
| `CategoryRequest.label` | Mais de 100 caracteres | `O nome da categoria pode ter no maximo 100 caracteres` |
| `CardRequest.name` | Vazio | `O nome e obrigatorio` |
| `CardRequest.name` | Mais de 100 caracteres | `O nome pode ter no maximo 100 caracteres` |
| `CardRequest.url` | Vazia | `A URL e obrigatoria` |
| `CardRequest.url` | Mais de 2048 caracteres | `A URL pode ter no maximo 2048 caracteres` |
| `CardRequest.url` | Não começa com http:// ou https:// | `A URL deve comecar com http:// ou https://` |
| `CardRequest.description` | Mais de 255 caracteres | `A descricao pode ter no maximo 255 caracteres` |
| `CardRequest.icon` | Mais de 2048 caracteres ou formato inválido | `Icone invalido` |

Um campo pode violar mais de uma regra ao mesmo tempo (uma URL vazia viola `@NotBlank` e `@Pattern`). Como só uma mensagem é devolvida e a ordem das validações do Bean Validation não é garantida, a mensagem exibida pode variar entre essas regras.

Os textos das mensagens não têm acentos, como no código original. Erros não previstos (por exemplo, falha de conexão com o banco) seguem o tratamento padrão do Spring Boot e retornam `500`.

## 6. Camadas em detalhe

### Controller: `CatalogController`

Anotado com `@RestController` e `@RequestMapping("/api")`. Não tem regra de negócio: cada método recebe o parâmetro de rota (`@PathVariable`), valida o corpo (`@Valid @RequestBody`), chama o `CatalogService` e define o status de sucesso com `@ResponseStatus` (201 nas criações, 204 nas exclusões).

| Método | Rota | Delega a |
| --- | --- | --- |
| `listCatalog()` | GET `/catalog` | `catalogService.listCatalog()` |
| `createCategory( request )` | POST `/categories` | `catalogService.createCategory( request )` |
| `renameCategory( id, request )` | PUT `/categories/{id}` | `catalogService.renameCategory( id, request )` |
| `deleteCategory( id )` | DELETE `/categories/{id}` | `catalogService.deleteCategory( id )` |
| `createCard( id, request )` | POST `/categories/{id}/cards` | `catalogService.createCard( id, request )` |
| `updateCard( id, request )` | PUT `/cards/{id}` | `catalogService.updateCard( id, request )` |
| `deleteCard( id )` | DELETE `/cards/{id}` | `catalogService.deleteCard( id )` |

### Service: `CatalogService`

A classe inteira é `@Transactional`; apenas `listCatalog()` usa `@Transactional(readOnly = true)`. Isso importa porque `open-in-view` está desligado: as entidades só podem ser lidas dentro da transação do serviço, e por isso a conversão para DTO acontece nele. As alterações em `renameCategory` e `updateCard` não chamam `save`: a entidade é gerenciada e o Hibernate grava ao final da transação (dirty checking).

| Método | O que faz |
| --- | --- |
| `listCatalog()` | Busca categorias com seus cards em uma só consulta (`findAllWithCards`) e converte em DTOs. |
| `createCategory( request )` | Cria a categoria com `label.trim()` e a próxima posição. |
| `renameCategory( categoryId, request )` | Busca a categoria (404 se não existir) e troca o `label`. |
| `deleteCategory( categoryId )` | Busca a categoria (404 se não existir) e a remove com seus cards. |
| `createCard( categoryId, request )` | Busca a categoria, normaliza os textos e cria o card na próxima posição da categoria. |
| `updateCard( cardId, request )` | Busca o card e chama `Card.updateData(...)`. |
| `deleteCard( cardId )` | Busca o card (404 se não existir) e o remove. |
| `findCategoryOrFail`, `findCardOrFail` | Privados. Buscam por id ou lançam `ResourceNotFoundException`. |
| `blankToNull( value )` | Privado. Devolve `null` para texto nulo ou em branco; senão, o texto com `trim()`. |

### Repository

Ambos estendem `JpaRepository`, que já fornece `findById`, `save`, `delete` e `count`.

- `CategoryRepository.findAllWithCards()`: `select distinct c from Category c left join fetch c.cards order by c.position, c.id`. O `join fetch` evita o problema de N+1 consultas ao listar o catálogo; o `distinct` remove categorias repetidas causadas pelo join.
- `CategoryRepository.nextPosition()`: `select coalesce(max(c.position), -1) + 1 from Category c`.
- `CardRepository.nextPosition( categoryId )`: o mesmo cálculo, filtrado por `c.category.id = :categoryId`.

### Model: `Category` e `Card`

Entidades JPA com construtor protegido sem argumentos (exigido pelo JPA) e um construtor público para uso no código. Não há setters genéricos: só `Category.setLabel`, `Card.setUrl` e o método `Card.updateData( name, url, description, icon )`, que altera os quatro campos editáveis de uma vez. `id`, `position` e a categoria do card não mudam depois da criação.

### DTO e Mapper

Os DTOs são `record`s, portanto imutáveis, e separam o contrato da API das entidades (que nunca são expostas). O `CatalogMapper` é uma classe utilitária final com métodos estáticos `toResponse( Card )` e `toResponse( Category )`. O segundo converte também os cards da categoria, no campo `services`.

### Exception

`ResourceNotFoundException` estende `RuntimeException` e carrega a mensagem. `GlobalExceptionHandler` traduz as exceções em respostas HTTP (ver seção 5).

## 7. Carga inicial (seed) e migração de dados legados

Na primeira execução, com o banco vazio, o `CatalogSeedLoader` carrega o catálogo padrão do arquivo `src/main/resources/seed-catalog.json`. Ele implementa `ApplicationRunner`, então roda uma vez a cada inicialização, logo depois de o contexto do Spring subir.

1. Se `categoryRepository.count() > 0`, não faz nada. Por isso o seed nunca sobrescreve dados já existentes.
2. Lê `seed-catalog.json` do classpath e converte em uma lista de categorias (`SeedCategory`), cada uma com seus cards (`SeedCard`).
3. Procura links salvos na versão anterior do sistema (ver abaixo).
4. Em uma única transação, cria cada categoria e cada card, com `position` seguindo a ordem do arquivo, e grava tudo.
5. Registra no log: `Catalogo padrao carregado (N categorias)`.

### Formato do arquivo

Uma lista de categorias. Cada uma tem `label` e `services`, e cada item de `services` tem `name`, `url`, `icon` e `description`. A chave se chama `services` no arquivo (e na API), mas vira `Card` no banco.

```json
[
  {
    "label": "Infraestrutura",
    "services": [
      { "name": "Proxmox", "url": "#", "icon": "proxmox", "description": "Virtualizacao (VMs e LXC)" }
    ]
  }
]
```

O seed não passa pela validação da API, então aceita `url` como `#`, como no arquivo atual.

### Migração de links legados

Antes de gravar, o loader tenta ler as tabelas antigas `service_link` e `tb_service_link` (consulta `select service_name, url`). Para cada card do seed cujo `name` coincide com um `service_name`, usa a URL antiga em vez da URL do arquivo. Se a tabela não existe, o erro é ignorado e nada é migrado. As tabelas antigas não são alteradas nem apagadas.

## 8. Configuração, CORS e execução

### Variáveis de ambiente

A configuração fica em `src/main/resources/application.yaml`. Cada valor pode ser trocado por variável de ambiente, sem alterar o código.

| Variável | Propriedade | Padrão | Para que serve |
| --- | --- | --- | --- |
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/homepage` | URL JDBC do PostgreSQL. |
| `DB_USER` | `spring.datasource.username` | `homepage` | Usuário do banco. |
| `DB_PASSWORD` | `spring.datasource.password` | `homepage` | Senha do banco. Troque em produção. |
| `PORT` | `server.port` | `8080` | Porta HTTP da API. |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | `http://localhost:5173` | Origens permitidas, separadas por vírgula. |

Outras propriedades fixas: `spring.application.name = homepage-backend`, `spring.jpa.hibernate.ddl-auto = update` (o Hibernate cria e ajusta as tabelas sozinho) e `spring.jpa.open-in-view = false` (a sessão do banco não fica aberta durante a serialização da resposta).

### CORS

O `CorsConfig` implementa `WebMvcConfigurer` e libera as origens de `app.cors.allowed-origins` apenas para `/api/**`, com os métodos `GET`, `POST`, `PUT`, `DELETE` e `OPTIONS`. Em desenvolvimento o Vite usa proxy, então o CORS nem é necessário. Em produção, se o frontend ficar em outro endereço, defina `CORS_ALLOWED_ORIGINS` com a URL dele, por exemplo `CORS_ALLOWED_ORIGINS=http://192.168.3.20:8080,https://home.exemplo.lan`.

### Banco de dados com Docker

O `compose.yaml` sobe um PostgreSQL 17 (container `homepage-postgres`) com banco, usuário e senha `homepage`, porta 5432 e volume persistente `homepage-data`, com `restart: unless-stopped`.

### Executando

1. Suba o banco: `docker compose up -d`.
2. Rode a aplicação com JDK 25: `./mvnw spring-boot:run` (no Windows, `mvnw.cmd spring-boot:run`) ou, no IntelliJ, abra o `pom.xml` e execute `HomepageApplication`.
3. Confira: `curl http://localhost:8080/api/catalog` deve devolver o catálogo padrão.

Para gerar um jar executável: `./mvnw clean package`, e depois `java -jar target/homepage-backend-0.0.1-SNAPSHOT.jar`. Para apontar para outro banco: `DB_URL=jdbc:postgresql://host:5432/homepage DB_USER=... DB_PASSWORD=... java -jar ...`.

## 9. Como estender e observações técnicas

### Adicionando um novo recurso

Siga a mesma ordem das camadas, de baixo para cima. Exemplo: adicionar um campo `color` ao card.

1. **Model:** inclua o campo em `Card` (com `@Column`) e ajuste `updateData` e o construtor. O Hibernate cria a coluna no próximo start.
2. **DTO:** adicione o campo em `CardRequest` (com a validação) e em `CardResponse`.
3. **Mapper:** inclua o campo em `CatalogMapper.toResponse( Card )`.
4. **Service:** passe o valor ao criar e ao atualizar o card em `CatalogService`.
5. **Controller:** normalmente não muda, porque ele só repassa os DTOs.

Para uma nova entidade, crie o `model`, o `repository`, os `dto`, o mapper e um service (ou métodos no existente) e, por fim, um controller com as rotas. Erros de "não encontrado" já são tratados pela `ResourceNotFoundException`.

### Convenções de código

- Espaços dentro dos parênteses em declarações e chamadas: `public void metodo( Teste teste )`.
- Chaves de métodos e construtores na linha seguinte; chaves de `if`, `for` e `try` na mesma linha. O padrão vem do `CorsConfig`.
- Nomes completos para variáveis e campos (`categoryRepository`, não `categories`).
- Comentários Javadoc e mensagens ao usuário em português, sem acentos, como no código original.

### Limitações conhecidas

| Ponto | Efeito | Como tratar |
| --- | --- | --- |
| Token JWT sem logout no servidor | Um token roubado vale até expirar (24 h). | Trocar `JWT_SECRET` invalida todos; a role, porém, é sempre lida do banco. |
| `ddl-auto: update` | O Hibernate altera o esquema sozinho, sem histórico de versões. | Adotar Flyway ou Liquibase se o esquema crescer. |
| Credenciais padrão `homepage/homepage` | Fracas, e a porta 5432 do compose fica exposta. | Definir `DB_USER`, `DB_PASSWORD` e restringir a porta. |
| Sem rota de reordenação | A ordem só é a de criação. | Adicionar rota que altere `position`. |
| `PUT /api/cards/{id}` não move o card | Não dá para trocar a categoria. | Apagar e recriar, ou adicionar `categoryId` ao `CardRequest`. |
| Sem testes automatizados | Não existe a pasta `src/test` no projeto. | Adicionar testes do serviço e `@WebMvcTest` do controller. |
| Mensagens de validação sem acento | Texto menos polido para o usuário final. | Usar UTF-8 nas mensagens (o `encodings.xml` do IDE já está configurado). |

### Observação sobre este documento

O código foi refatorado para MVC sem mudar rotas, JSON nem tabelas, mas a compilação completa não pôde ser executada no ambiente em que a refatoração foi feita (sem acesso ao Maven Central). Rode `./mvnw clean package` uma vez para confirmar.

## 10. Autenticação e controle de acesso (roles)

### Roles

| Role | Pode |
| --- | --- |
| `USER` | Fazer login e visualizar o catálogo (`GET /api/catalog`). Não edita nada. |
| `ADMIN` | Tudo do `USER`, mais criar, editar e remover categorias e cards, e gerenciar usuários e roles. |

O frontend também esconde os controles de edição e desativa o clique nos cards para `USER`, mas quem garante o bloqueio é o backend: uma chamada de escrita feita por um `USER` recebe `403`, mesmo fora da interface.

### Como funciona

1. `POST /api/v1/auth/login` valida e-mail e senha e devolve `{ token, username, email, role }`. O token é um JWT assinado (HS256) válido por 24 horas.
2. O cliente envia `Authorization: Bearer <token>` nas demais chamadas.
3. O `SecurityFilter` valida o token e **busca o usuário no banco** para obter a role atual. Assim, rebaixar ou apagar um usuário vale na próxima requisição, sem esperar o token expirar.
4. O `SecurityConfig` aplica as regras por rota (tabela abaixo).

### Regras de acesso por rota

| Rota | Acesso |
| --- | --- |
| `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, Swagger | Público |
| `GET /api/catalog` | `USER` ou `ADMIN` |
| `POST/PUT/DELETE /api/categories/**` e `/api/cards/**` | Somente `ADMIN` |
| `GET /api/v1/users`, `PUT /api/v1/users/{id}/role` | Somente `ADMIN` |
| Qualquer outra rota | Autenticado |

### Códigos de resposta

| Status | Quando | Corpo |
| --- | --- | --- |
| 401 | Sem token, token inválido ou expirado; ou e-mail/senha incorretos no login | `{ "message": "..." }` |
| 403 | Logado, mas sem a role exigida | `{ "message": "Voce nao tem permissao para esta acao" }` |
| 409 | E-mail já cadastrado; ou tentativa de remover o último administrador | `{ "message": "..." }` |

### Cadastro e primeiro administrador

- `POST /api/v1/auth/register` é público e **sempre cria `USER`**. Um campo `role` enviado no corpo é ignorado.
- O primeiro `ADMIN` é definido por variáveis de ambiente, lidas pelo `AdminSeedLoader` a cada inicialização:
  - `ADMIN_EMAIL` e `ADMIN_PASSWORD` (e opcionalmente `ADMIN_USERNAME`): se o e-mail não existe, o usuário é criado como `ADMIN`.
  - Se o e-mail **já existe** (por exemplo, uma conta criada antes das roles), ele é promovido a `ADMIN` e a senha atual é mantida.
- Usuários criados antes das roles (coluna `role` nula) são tratados como `USER`.

### Gestão de usuários (somente ADMIN)

| Método | Rota | Corpo | Resposta |
| --- | --- | --- | --- |
| GET | `/api/v1/users` | — | Lista de `{ id, username, email, role }` |
| PUT | `/api/v1/users/{id}/role` | `{ "role": "ADMIN" }` ou `{ "role": "USER" }` | Usuário atualizado; `404` se não existir; `409` se deixaria o sistema sem administrador |

### Novas variáveis de ambiente

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `ADMIN_EMAIL` | vazio | E-mail do administrador inicial (criado ou promovido). |
| `ADMIN_PASSWORD` | vazio | Senha usada somente se o administrador ainda não existir. |
| `ADMIN_USERNAME` | `admin` | Nome do administrador criado. |
| `JWT_SECRET` | `chave-temporaria-para-desenvolvimento` | Chave de assinatura dos tokens. **Troque em produção.** |

### Classes novas ou alteradas

`Role` (enum), `User` (campo `role` e autoridade `ROLE_<role>`), `SecurityFilter`, `SecurityConfig`, `AdminSeedLoader`, `UserController`, `UserService`, `UserMapper`, `UserResponseDTO`, `UpdateUserRoleRequestDTO`, `ConflictException`, `GlobalExceptionHandler` (409 e 401), `AuthService` (registro sempre `USER`, e-mail único) e `LoginResponseDTO` (agora com `username`, `email` e `role`).
