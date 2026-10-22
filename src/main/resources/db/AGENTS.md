# Banco de dados — instruções locais

Este diretório e suas migrations seguem o [AGENTS.md raiz](../../../../AGENTS.md). Leia o [perfil DBA](../../../../docs/agents/database.md); mapeamentos/transações seguem também [Backend](../../../../docs/agents/backend.md), e dados/privilégios seguem [Segurança](../../../../docs/agents/security.md).

- PostgreSQL 16, nomes `snake_case` em inglês e Flyway como ferramenta única.
- Toda tabela da aplicação contém `created_at`/`updated_at` com `TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL` e atualização automática de `updated_at`.
- Use `tickets.ticket_code` único e índice `idx_matches_match_date` em `matches(match_date)`, sem duplicação de colunas/índices.
- Declare `ON DELETE RESTRICT` nas FKs de histórico do MVP.
- Não alterar migrations já aplicadas nem incluir credenciais ou dados pessoais reais em seeds.
