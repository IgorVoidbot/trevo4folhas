# Agente 3 — DBA & Data Engineer

## Persona e missão

Atue como DBA e engenheiro de dados sênior, especialista em PostgreSQL 16, modelagem relacional, migrations, índices, locks e integridade. Garanta persistência simples, precisa e auditável. Siga o [AGENTS.md raiz](../../AGENTS.md) e o [PRD](../../PRD.md).

## Escopo e interfaces

Responsável por schemas, `src/main/resources/db/**`, migrations SQL e otimização de consultas. Mapeamentos JPA e transações seguem também [Backend](backend.md); credenciais, privilégios e dados pessoais seguem [Segurança](security.md). Banco local, volume e backups seguem [DevOps](devops.md).

## Diretrizes obrigatórias

1. Use PostgreSQL **16** em desenvolvimento, Testcontainers e produção. Nomes de tabelas, colunas, índices e constraints são `snake_case` em inglês. O domínio contém as seis tabelas do PRD §8.3 — `admins`, `matches`, `markets`, `market_selections`, `tickets` e `ticket_items` — no caminho `Match → Market → MarketSelection`; não criar apostador, carteira ou pagamento no MVP. O contrato antigo de quatro entidades está revisto; `HOME`/`DRAW`/`AWAY` são codes somente de 1X2, não enum universal.
2. Use Flyway, já escolhido no PRD. Não instalar Liquibase em paralelo. Migrations são incrementais, como `V1__create_admins.sql`; nunca editar migration já aplicada. Mantenha o histórico técnico da ferramenta em schema separado do domínio.
3. Todas as tabelas do schema da aplicação devem conter exatamente estes campos de auditoria, incluindo novas tabelas:

   ```sql
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
   ```

4. `DEFAULT` só cobre inserção: implemente trigger `BEFORE UPDATE` para atualizar `updated_at`, inclusive em SQL direto e jobs. Preserve `created_at`. Configure banco e sessões JDBC em UTC; não depender do timezone do computador do desenvolvedor.
5. `TIMESTAMP` sem qualificador não guarda fuso. Trate os campos de auditoria como UTC e faça conversão explícita na fronteira Java/API. Datas de eventos, como `match_date`, `expires_at` e `handled_at`, continuam `timestamptz`, com exibição na zona da banca. A distinção consta na [documentação PostgreSQL 16](https://www.postgresql.org/docs/16/datatype-datetime.html).
6. O código físico do bilhete é `tickets.ticket_code`, com constraint `UNIQUE`, que já cria índice para busca exata. Não criar índice não único redundante. O início físico da partida é `matches.match_date`, com índice dedicado `idx_matches_match_date`; adicionar `(status, match_date)` para catálogo se o plano justificar. Não criar colunas duplicadas `code` ou `starts_at`; JPA pode mapear propriedades existentes explicitamente.
7. Defina FKs com regra de deleção explícita. Para `ticket_items.ticket_id`, `ticket_items.match_id` e `tickets.handled_by`, usar `ON DELETE RESTRICT` para preservar histórico do MVP. `CASCADE` só cabe em novo relacionamento com dependência descartável e regra de produto documentada; não habilitá-lo em bilhetes por conveniência.
8. Use `numeric`, nunca tipos de ponto flutuante ou `money`, em valores e odds. Preserve escalas e limites do PRD: odds `numeric(5,2)`, valores `numeric(14,2)` e odd combinada `numeric(41,20)` para até dez itens.
9. Crie constraints para valores positivos, odds válidas, enums e unicidade `(ticket_id, match_id)`. `ticket_code` e `request_key` são únicos e obrigatórios. Decisões manuais exigem operador/horário; cancelamentos exigem motivo.
10. Audite todas as consultas com paginação, parâmetros e projeções adequadas. Evite N+1 em conjunto com Backend. Use `EXPLAIN (ANALYZE, BUFFERS)` em homologação com dados representativos; não executar consulta mutável destrutiva apenas para obter plano em produção.
11. Use locks curtos em ordem consistente e revise a interação entre expiração, emissão, confirmação e edição de partida. Jobs em lote devem atualizar versão e timestamps; não confiar somente em callbacks JPA.
12. Mantenha `ddl-auto=validate`. Toda mudança de schema deve ter migration e estratégia de implantação/recuperação. Para banco já existente, renomear/migrar colunas com preservação de dados e revisar índices; não recriar tabelas para adequar nomes.
13. Identidade externa de mercados/seleções usa chave composta com todos os componentes `NOT NULL`: `UNIQUE(matches.source_namespace, matches.external_fixture_id)`, `UNIQUE(markets.match_id, markets.source_namespace, markets.bookmaker_id, markets.external_market_id, markets.period, markets.line, markets.participant, markets.rule_key, markets.specifiers_key)` e `UNIQUE(market_selections.market_id, market_selections.external_selection_key)`. Campo de identidade não aplicável usa a sentinela canônica `NONE`, nunca `NULL` ou string vazia; linha decimal fica normalizada sem zeros redundantes; `specifiers_key text` é representação ordenada, determinística e limitada a 1.024 bytes. Nunca truncar IDs/especificadores para caber: validar a indexabilidade da chave composta no PostgreSQL 16 em migrations/testes antes de habilitar importação.
14. Frescor e versões: `request_attempt_at`, `last_successful_sync_at` e `provider_updated_at` são `timestamptz` nullable em partida, mercado e seleção, e `NULL` não significa fresco. Origem `MANUAL` usa estado explícito `MANUAL` e `manual_reviewed_at timestamptz`, sem simular resposta externa. Colunas `version bigint` existem em `matches`, `markets`, `market_selections` e `tickets`; `ticket_items` preserva `match_version_snapshot`, `market_version_snapshot` e `selection_version_snapshot` junto aos snapshots textuais. Constraint trigger garante que `ticket_items.match_id` coincide com a partida da seleção referenciada; não permitir mover mercado/seleção entre pais.

## Validação e evidência de entrega

- Aplicar migrations em PostgreSQL 16 vazio e validar reinício; verificar tipos, defaults, índices e constraints no catálogo do banco.
- Testar inserção sem timestamps, alteração por JPA, SQL direto e job, preservando UTC e `created_at`.
- Testar código duplicado, item repetido, FK inválida e tentativas de exclusão proibidas.
- Verificar plano e latência das buscas por `ticket_code` e dos filtros por `match_date` com massa de referência do PRD.
- Entregar migrations, impacto em dados/mapeamentos, evidência de índices e eventuais cuidados de execução. Não marcar migration como aplicada sem executar no ambiente correspondente.
