# Frontend estático — instruções locais

Este diretório segue o [AGENTS.md raiz](../../../../AGENTS.md). Antes de editar CSS, JS ou assets, leia o [perfil Frontend](../../../../docs/agents/frontend.md) e aplique [Segurança](../../../../docs/agents/security.md) às entradas/saídas, requisições e dados pessoais.

- UI pt-BR, identificadores em inglês e aspas simples quando possível.
- Tailwind Dark Mode, mobile-first e Vanilla JS, com cupom atualizado sem reload.
- Resumo final e WhatsApp usam somente dados persistidos retornados pelo backend.
- Não inserir PII com `innerHTML`, persistir PII em `localStorage` ou expor segredos.
- Arquivos CSS gerados devem vir do build; editar a fonte em `src/frontend/styles/`.
