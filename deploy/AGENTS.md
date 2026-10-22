# Implantação — instruções locais

Este diretório segue o [AGENTS.md raiz](../AGENTS.md). Leia o [perfil DevOps](../docs/agents/devops.md), [Segurança](../docs/agents/security.md) para rede/segredos e [DBA](../docs/agents/database.md) para migrations/backups.

- Scripts e variáveis em inglês; credenciais somente por ambiente/secret manager.
- Use a mesma imagem Java e PostgreSQL 16 definidos no projeto.
- Preserve volumes, documente recuperação e nunca remova dados como rotina de implantação.
- Não misture criação de artefatos com publicação externa não solicitada.
- Registre comandos e resultados reais sem expor valores de segredos.
