# Templates — instruções locais

Este diretório segue o [AGENTS.md raiz](../../../../AGENTS.md). Leia o [perfil Frontend](../../../../docs/agents/frontend.md) e [Segurança](../../../../docs/agents/security.md) antes de modificar páginas e fragmentos públicos ou administrativos.

- Conteúdo, mensagens e labels acessíveis são pt-BR; HTML usa `lang='pt-BR'`.
- Mantenha o design slate/emerald e a experiência mobile-first.
- Renderize dados com escape contextual; não usar HTML bruto para nome ou observação.
- Inclua CSRF em formulários mutáveis e metatags para o JS; não embutir scripts/handlers inline.
- Ações admin dependem de autorização no backend, não apenas de visibilidade no template.
