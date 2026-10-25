# T4F-006 — Mapear entidades e persistência do catálogo

Relatório de execução (worker `trevo-backend`, run 12, 2026-10-25). Todos os comandos abaixo foram
executados dentro do sandbox Docker, no workspace `/workspace`, com daemon privado de testes
(Testcontainers/Ryuk ativos, PostgreSQL 16 real, sem H2, sem uso de `app/db` para testes).

## Escopo entregue

Camada JPA completa do catálogo e de bilhetes, sem controllers/rotas, login, importação, emissão ou
confirmação (tarefas posteriores).

### Entidades e enums (`src/main/java/com/trevo4folhas/`)

| Arquivo | Tabela | Observações |
| --- | --- | --- |
| `shared/BaseEntity.java` | — | `@MappedSuperclass`: UUID atribuído pela aplicação + `created_at`/`updated_at` somente leitura (`insertable=false, updatable=false`), gerados pelo banco (defaults + trigger V1). |
| `admin/Admin.java` | `admins` | Hash de senha nunca sai da entidade (sem getter público que o copie); `matchesPasswordHash` para comparação interna. |
| `match/Match.java`, `match/MatchStatus.java` | `matches` | `startsAt` mapeado explicitamente para `match_date` (timestamptz/Instant); `@Version` com semente 1; status `OPEN/SUSPENDED/CLOSED/CANCELLED`. |
| `market/Market.java` | `markets` | Identidade composta de 9 componentes (`uq_markets_identity`); sentinela `NONE`; `line` canônica; `@Version`. |
| `market/MarketSelection.java` | `market_selections` | Odds `numeric(5,2)` (`BigDecimal`, precision=5, scale=2); `@Version`; `HOME/DRAW/AWAY` são codes, não enum universal. |
| `ticket/Ticket.java` | `tickets` | `code` → `ticket_code`; `request_hash`/`session_hash` com mapeamento explícito `@JdbcTypeCode(SqlTypes.CHAR)` para `char(64)`; `total_odds numeric(41,20)`; `@Version`. |
| `ticket/TicketItem.java`, `ticket/TicketStatus.java` | `ticket_items` | Snapshots imutáveis (sem mutadores); sem `@Version` (schema não tem `version`); caminho Match→Market→MarketSelection preservado. |

### Repositories (`JpaRepository`, sem expor JPA em DTOs)

`admin/AdminRepository`, `match/MatchRepository`, `market/MarketRepository`,
`market/MarketSelectionRepository`, `ticket/TicketRepository`, `ticket/TicketItemRepository`.
Métodos bulk `recordRequestAttempt` (Match, Market, MarketSelection) atualizam metadados
operacionais sem renovar a versão comercial.

### DTOs (records, sem tipos JPA, sem PII/segredos)

`match/dto/MatchDto`, `market/dto/CatalogSelectionDto` (resolve `matchId/marketId/selectionId` +
`matchVersion/marketVersion/selectionVersion` + componentes semânticos + odds string),
`ticket/dto/TicketDto` (sem nome/telefone/sessão/hashes do apostador), `ticket/dto/TicketItemSnapshotDto`.

## Testes (JUnit 5, Testcontainers PostgreSQL 16 real, Flyway V1–V8, `ddl-auto=validate`)

- `src/test/java/com/trevo4folhas/db/JpaRoundTripAuditTest.java` (4 testes) — checkpoint 1:
  round-trip de `BaseEntity`+`Admin` e `BaseEntity`+`Match`, UUID atribuído, auditoria gerada pelo
  banco, mudança comercial incrementa `version` 1→2 com `created_at` preservado e `updated_at`
  renovado pelo trigger em transação distinta, identidade externa sem duplicação, metadados
  operacionais não renovam versão.
- `src/test/java/com/trevo4folhas/db/CatalogTicketPersistenceTest.java` (10 testes) — grafo
  Match→Market→MarketSelection com resolução canônica e DTOs; identidade composta de mercado
  rejeita duplicata mesmo com rótulos diferentes; sentinela `NONE` distinta de `0`; uma seleção por
  partida por bilhete (`uq_ticket_items_ticket_match`); snapshots imutáveis quando o catálogo se
  move (muda preço → `selectionVersion` 2, snapshot continua 1); pais de `ticket_items` imutáveis
  mesmo via SQL direto (trigger V8); metadados operacionais não renovam `selectionVersion`;
  precisão `numeric(5,2)` e `numeric(41,20)` preservada (escala 2 e 20); `ticket_code` e
  `request_key` únicos; auditoria em UTC.

As fixtures existentes foram reutilizadas sem alteração (`TestDb`, package-private em
`com.trevo4folhas.db`); os novos testes JPA ficam nesse pacote. Assertions aprovadas das suites
antigas preservadas (nenhuma suite pulou; nenhuma assertion foi removida).

## Verificações executadas (resultados reais)

1. `./mvnw -B test -Dtest=JpaRoundTripAuditTest` — `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
   (log: `target/verification-logs/t4f006-01-checkpoint-roundtrip.log`).
   - Primeira execução falhou com erro real de validação de schema: `request_hash found [bpchar
     (Types#CHAR)], but expecting [varchar(64) (Types#VARCHAR)]` — a hipótese de `char(64)` exigir
     mapeamento explícito foi validada por teste executável e corrigida com
     `@JdbcTypeCode(SqlTypes.CHAR)` em `request_hash`/`session_hash`.
2. `./mvnw -B test -Dtest=CatalogTicketPersistenceTest` — `Tests run: 10, Failures: 0, Errors: 0,
   Skipped: 0` (log: `target/verification-logs/t4f006-02-catalog-ticket-invariants.log`).
3. `./mvnw -B spotless:apply` — formato Google Java Format aplicado (apenas nos arquivos novos).
4. `./mvnw -B spotless:check verify` — BUILD SUCCESS, `Tests run: 62, Failures: 0, Errors: 0,
   Skipped: 0` (log: `target/verification-logs/t4f006-03-verify-full.log`). Suites:
   BettingPropertiesTest 7, ClockConfigTest 3, CatalogTicketPersistenceTest 10,
   JpaRoundTripAuditTest 4, MigrationRestartTest 1, MigrationCatalogTest 13,
   TestDbTeardownTest 2, MigrationInvariantsTest 22 — total 62 (48 pré-existentes + 14 novas).

## Preservações e limites respeitados

- Migrations V1–V8 não tocadas (somente leitura); hashes registrados neste relatório:
  V1 `ecf9f02e…98ee6`, V2 `48ecf10e…b6e4e0`, V3 `ba64f18c…b08dde`, V4 `98480e79…0a4363`,
  V5 `cf0d1878…8ec3132`, V6 `409d5b96…80a58bbe`, V7 `b120c591…67e4c1`, V8 `eaf6e504…f0d93a32ee`.
- `git status` no sandbox: únicos arquivos versionados modificados continuam sendo os documentos
  pré-existentes (AGENTS.md, PRD.md, docs/agents/*), alterações anteriores preservadas; todos os
  arquivos criados por esta tarefa são novos nos diretórios autorizados.
- Nenhum container de teste restou após o verify (apenas `trevo4folhas-db-1` do compose, healthy);
  Ryuk habilitado; nenhum teste destrutivo usou `app/db`.
- Sem commit, push, merge, deploy, mudança de perfil/configuração global, segredos ou chamadas
  externas reais. Gate `t_75733c77` e gates 028/029 intocados.

## Decisões de mapeamento validadas por teste

1. `LocalDateTime` para auditoria (`timestamp` sem fuso) e `Instant` para datas de negócio
   (`timestamptz`) — round-trip verde com conexões UTC.
2. `char(64)` exige `@JdbcTypeCode(SqlTypes.CHAR)`; `String` puro mapeia `varchar` e o `validate`
   rejeita (erro reproduzido acima).
3. `@Version` nas quatro entidades exigidas com valor inicial 1 satisfaz `ck_*_version_positive`;
   atualização comercial incrementa 1→2.
4. Metadados operacionais (`request_attempt_at`) atualizados por bulk update não renovam a versão
   comercial — comportamento exigido pelo PRD §8.3 comprovado em teste.

## Pendências (fora do escopo deste cartão, para tarefas posteriores)

- Services transacionais (emissão, confirmação, cancelamento, expiração) e controllers/rotas.
- `TicketService` com idempotência por sessão, locks e revalidação de elegibilidade (T4F-007+).
- `AdminUserDetailsService`, `SecurityConfig` e autenticação de operador.
- Camada `SportsDataProvider`/importação (gates 028/029 seguem bloqueados).
- Testes MockMvc de autorização/CSRF e benchmark p95 < 200 ms (exige endpoints).

## Rodada de correção — revisão independente, rodada 1 (worker `trevo-backend`, run 13, 2026-10-25)

Correção focada dos dois defeitos reproduzidos pela revisão (`t4f006-review-round1.md`), sem
ampliar escopo. TDD: as falhas foram reproduzidas com as regressões novas antes de qualquer
correção (log: `target/verification-logs/t4f006-04-review-red.log` — `Tests run: 10, Failures: 9`
com as mesmas falhas de assertion da sonda independente: `request_attempt_at` voltando a `NULL` e
campos decimais como nós JSON numéricos), depois corrigidas e verificadas em verde.

### R1 — metadados operacionais preservados diante de flush comercial

Correção de classe em `Match`, `Market` e `MarketSelection`: as quatro colunas de metadados
operacionais (`request_attempt_at`, `last_successful_sync_at`, `provider_updated_at`,
`manual_reviewed_at`) agora são mapeadas `@Column(..., updatable = false)`, seguindo o mesmo padrão
de `BaseEntity` para colunas gerenciadas fora do UPDATE do JPA. Escrita de metadados operacionais
continua exclusivamente pelos bulk updates dos repositories (`recordRequestAttempt`), que nunca
renovam a versão comercial; nenhuma entidade possui mutadores para esses campos. Com isso o UPDATE
gerado pelo JPA em mudança comercial nunca regrava essas colunas com snapshot antigo do contexto —
na mesma transação nem entre transações. Não há `clear`/detach como disfarce: a alteração comercial
do objeto gerenciado persiste normalmente (versão 1→2 comprovada em todas as regressões).

Semântica pós-bulk documentada (Javadoc das entidades e dos três repositories): o bulk update
ignora o contexto de persistência; instâncias gerenciadas mantêm o valor em memória até serem
relidas e a linha do banco é a autoridade. Como as colunas são `updatable = false`, um flush
posterior de um contexto com snapshot antigo jamais sobrescreve bookkeeping já confirmado.

Regressões permanentes: `src/test/java/com/trevo4folhas/db/OperationalMetadataFlushRegressionTest.java`
(6 testes, PostgreSQL 16 real) — para cada uma das três entidades: (a) persistir/carregar gerenciado
→ `recordRequestAttempt` → mudança comercial no mesmo objeto → flush/clear → reload preserva
`request_attempt_at` e `version` 2; (b) bookkeeping confirmado em transação própria
(`REQUIRES_NEW`) enquanto outra transação segura snapshot anterior → flush comercial posterior não
sobrescreve a tentativa. Pós-commit também revalidado.

### R2 — decimais financeiros como strings JSON (PRD §8.4/§8.5)

`TicketDto.stakeAmount/totalOdds/potentialReturn` e `TicketItemSnapshotDto.selectedOdds` mantêm
`BigDecimal` internamente (precisão/escala exatas, sem double/float) e agora serializam como string
decimal textual via `@JsonSerialize(using = ToStringSerializer.class)` por campo — garantia no
próprio DTO, independente de configuração global de `ObjectMapper` (a sonda usa mapper puro).

Regressões permanentes: `src/test/java/com/trevo4folhas/ticket/dto/FinancialDecimalStringSerializationTest.java`
(4 testes) — todos os campos financeiros dos dois DTOs com tipo textual e conteúdo exato
(`"10.00"`, `"25.00"`, `"2.50"`, largura total `numeric(41,20)`
`"12345678901234567890.01234567890123456789"`), produto de dez odds com escala 20 exata
(`"2280.73660815000000000000"`) e round-trip JSON preservando valor e escala de `BigDecimal`.

### Verificações executadas (resultados reais)

1. `./mvnw -B test -Dtest=OperationalMetadataFlushRegressionTest,FinancialDecimalStringSerializationTest`
   — RED: `Tests run: 10, Failures: 9, Errors: 0, Skipped: 0` (log:
   `target/verification-logs/t4f006-04-review-red.log`); GREEN após correções: `Tests run: 10,
   Failures: 0, Errors: 0, Skipped: 0` (log: `target/verification-logs/t4f006-05-review-green.log`).
2. Sonda independente `target/review-006/ReviewAcceptanceTest.java` executada sem modificação
   (cópia temporária no pacote `com.trevo4folhas.db`, removida após a execução): `Tests run: 4,
   Failures: 0, Errors: 0, Skipped: 0` (log: `target/verification-logs/t4f006-06-review-probe.log`;
   hash da sonda preservado: `18e664fe5cd4ab1aceca489f4f651d03d036af950d2468dd0c444ad3f31cb276`).
3. `./mvnw -B spotless:apply` (log: `target/verification-logs/t4f006-07-spotless-apply.log`).
4. `./mvnw -B spotless:check verify` — BUILD SUCCESS, `Tests run: 72, Failures: 0, Errors: 0,
   Skipped: 0` (log: `target/verification-logs/t4f006-08-verify-full.log`; XMLs em
   `target/surefire-reports/`). Suites: BettingPropertiesTest 7, ClockConfigTest 3,
   FinancialDecimalStringSerializationTest 4, CatalogTicketPersistenceTest 10,
   JpaRoundTripAuditTest 4, MigrationRestartTest 1, MigrationCatalogTest 13, TestDbTeardownTest 2,
   MigrationInvariantsTest 22, OperationalMetadataFlushRegressionTest 6 — total 72 (62 anteriores
   preservadas + 10 novas). Testcontainers PostgreSQL 16 real, Ryuk habilitado, `ddl-auto=validate`;
   nenhum container de teste restou após a execução.

### Hashes SHA-256 dos fontes alterados nesta rodada

- `match/Match.java` `925b7406fd682ee73b6deb3b64714da06c134fb973a80403fcac4a1f2c6aee43`
- `match/MatchRepository.java` `a5e43abbaa5bfae4c5f434ac7f479d9c3e4e3b269aa47a7eb46ace3060716324`
- `market/Market.java` `fb246910ec3af3c8145eb5a298da3a86bd135542f4106e590163fd22a7a99ae4`
- `market/MarketRepository.java` `3373c5ee5164ca3bf25cf34197508d2a156e18cf454e4551148c4e5f61af9703`
- `market/MarketSelection.java` `1896cb4f51c8e232a600ff7f82c09627f0dbcb9985a8435aadebaaac842696e4`
- `market/MarketSelectionRepository.java` `0ef7251aa49041689293c92a1dd23f738a08fd4aabcb45b1abfc16995db03dac`
- `ticket/dto/TicketDto.java` `30f487ba8407a15bcf2abb01b9faa6be040d915922d7192225c683f3d7aa23fe`
- `ticket/dto/TicketItemSnapshotDto.java` `0d7a8024eddda48668f38a4fde3f944069d84d2e48ba2e69dd2c1d397f3e7260`
- `src/test/java/com/trevo4folhas/db/OperationalMetadataFlushRegressionTest.java` `1944edbb97885bff71a2b9e2fcef98bb3af832eb1bbfd561d284665cd61a6bab`
- `src/test/java/com/trevo4folhas/ticket/dto/FinancialDecimalStringSerializationTest.java` `5548663b865dd9ad0b55b2677700c1153264e0564ea8afe57f569f663419b19e`

### Preservações e limites respeitados nesta rodada

- Migrations V1–V8 não tocadas; hashes revalidados idênticos aos registrados acima
  (V1 `ecf9f02e…`, V2 `48ecf10e…`, V3 `ba64f18c…`, V4 `98480e79…`, V5 `cf0d1878…`, V6 `409d5b96…`,
  V7 `b120c591…`, V8 `eaf6e504…`).
- Nenhuma assertion existente removida/enfraquecida; suites anteriores intactas e verdes.
- Escrito apenas o previsto: entidades/repositories/DTOs implicados em R1/R2, testes Java e este
  relatório. Documentos pré-existentes modificados (AGENTS.md, PRD.md, docs/agents/*) preservados
  sem tocar. Sondas da revisão preservadas em `target/review-006/`.
- Sem commit/push/merge/deploy, sem mudanças de infraestrutura/perfis/build/dependências; gates
  `t_75733c77` e 028/029 continuam bloqueados; T4F-007 não iniciada.
