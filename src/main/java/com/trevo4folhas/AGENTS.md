# Backend — instruções locais

Este diretório e seus descendentes seguem o [AGENTS.md raiz](../../../../../AGENTS.md).

Antes de editar Java, leia e aplique o [perfil Backend](../../../../../docs/agents/backend.md). Aplique também [Segurança](../../../../../docs/agents/security.md) ao alterar autenticação, autorização, entrada/saída, HTTP ou dados pessoais, e [DBA](../../../../../docs/agents/database.md) ao alterar entidades, repositories, consultas ou transações de persistência.

- Código e logs em inglês; mensagens visíveis pt-BR.
- Controllers usam DTOs e delegam regras a serviços transacionais.
- Toda entidade de domínio estende `BaseEntity`; valores usam `BigDecimal`.
- Não confirmar bilhete pelo frontend, por clique no WhatsApp ou sem revalidar prazo/estado.
- Meta de desempenho e testes seguem o PRD; não declarar verificações não executadas.
