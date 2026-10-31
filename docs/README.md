# Documentação — TREVO4FOLHAS

A implementação continua no repositório Projeto-aposta. O produto é uma plataforma de pré-bilhetes de futebol, sem conta de cliente, com atendimento WhatsApp e confirmação administrativa após conferência manual do Pix.

## Documentos de referência

A página `/` exibe o catálogo público e a montagem/revisão do cupom, com emissão ainda desabilitada. O login administrativo permanece em `/admin/login`. Comportamento, testes e limites estão na [validação da Sprint 2](planning/sprint-2-validation.md); screenshots locais em `target/verification-sprint2/`.

- [PRD](PRD.md): produto, arquitetura, UX, dados, requisitos, aceites, métricas e decisões pendentes.
- [Sprints](SPRINTS.md): backlog preservado, dependências, responsáveis e evidência de conclusão.
- [Auditoria atual](planning/project-audit-2026-10-31.md): implementação conferida, checks executados, lacunas e critérios dos checklists.
- [Arquitetura](agents/ARCHITECT_AGENT.md): coordenação técnica e contratos.
- [Backend](agents/BACKEND_AGENT.md): Java, APIs e integridade transacional.
- [Frontend](agents/FRONTEND_AGENT.md): UI/UX e fluxo sem cadastro.

## Instruções e histórico

### Pré-requisitos e checks

Use Java 17, Node.js 20 ou superior e Docker com Compose. Execute `npm ci` para instalar as ferramentas pelo lockfile; gere os assets com `npm run css:build` antes de empacotar. O formatter atual é incompatível com Java 25; configure `JAVA_HOME` para o JDK 17.

Checks locais: `npm run lint`, `npm run format:check`, `node src/frontend/tests/slip-state.test.mjs` e `./mvnw spotless:check verify`. A suíte Java usa PostgreSQL 16 real via Testcontainers e precisa acessar o Docker. Relatórios: `target/surefire-reports/` e `target/site/jacoco/`. Não há workflow de CI remoto implementado.

### Banco local em container

O serviço `db` do `docker-compose.yml` usa PostgreSQL 16 e o volume persistente `trevo4folhas_postgres_data`. O binding fica em `127.0.0.1`; `POSTGRES_PORT` permite escolher a porta do host e mantém 5432 como padrão quando ausente.

Na configuração local de 31/10/2026 foi escolhida a porta **5433**, pois 5432 já estava ocupada. O `.env` recebeu `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Usuário e senha locais foram definidos conforme solicitado pelo usuário; não foram adicionados a arquivos versionados. As demais entradas do `.env` foram preservadas.

Para iniciar somente o banco:

```sh
docker compose --env-file .env.example --env-file .env up -d --wait db
```

O arquivo de exemplo fornece placeholders para interpolar as variáveis do serviço `app`, mesmo quando só `db` é iniciado; o `.env` prevalece e fornece os valores reais do banco. Esse comando não inicia o serviço `app`. Para executar a aplicação no IntelliJ, mantenha o perfil `local` e a raiz do projeto como diretório de execução. Para executar `app` no Compose, preencha seus parâmetros reais antes; os placeholders não configuram a aplicação.

Reiniciar o container preserva os dados. Não remova o volume para corrigir erros de conexão. Alterar `POSTGRES_PASSWORD` no `.env` não troca a senha de um banco já inicializado.

Validação em 31/10/2026: Compose válido, container `trevo4folhas-db-1` saudável, autenticação TCP por senha aprovada, PostgreSQL 16 em UTC, oito migrations Flyway e seis tabelas de domínio. A aplicação foi iniciada temporariamente com perfil `local` e respondeu HTTP 200 em `/api/config`; depois foi encerrada. O banco permaneceu ativo. Integração esportiva e bootstrap administrativo foram desativados explicitamente nessa verificação. O pacote Maven passou; não foi reexecutada a suíte completa por esta alteração de ambiente.

### Chave da API no desenvolvimento local

Com o perfil `local`, a aplicação carrega o arquivo `.env` do diretório de execução (use a raiz do projeto). Adicione `API_FOOTBALL_API_KEY=sua_chave` nele, sem aspas e sem `export`. O arquivo é lido como propriedades Java, não como script de shell. Não publique esse arquivo. Variáveis de ambiente do processo têm prioridade sobre seus valores.

No IntelliJ, selecione `local` em **Active profiles** na configuração Spring Boot; se esse campo não estiver disponível, adicione `--spring.profiles.active=local` em **Program arguments**. No terminal, use `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` com Java 17. Reinicie a aplicação após alterar o `.env`.

Os demais parâmetros obrigatórios de banco e negócio continuam necessários. O carregamento da chave não habilita importação: integração e agendamento seguem desligados por padrão. A aplicação fora do perfil `local` continua recebendo os segredos pelo ambiente. O Compose mantém seu repasse explícito de variáveis; este carregamento local não altera o ambiente do container. Para usar outro arquivo local, defina `TREVO_ENV_FILE` com seu caminho.

### Primeiro acesso administrativo local

Em 31/10/2026, o primeiro operador local foi criado pelo `AdminBootstrapService`, após verificar que não havia operadores no banco. Uma senha aleatória foi fornecida apenas ao processo temporário de bootstrap; não foram adicionadas credenciais de bootstrap permanentes ao `.env`. O arquivo local `.env.admin-access`, ignorado pelo Git e com permissão `600`, contém os campos `ADMIN_USERNAME` e `ADMIN_PASSWORD` para o usuário consultar. Esse arquivo não é carregado automaticamente pela aplicação. O banco mantém somente o hash BCrypt. A senha não tem expiração automática nesta versão.

Foram verificados cadastro, hash BCrypt persistido, login com CSRF e acesso autenticado a `/admin/matches`. O processo temporário foi encerrado; o acesso já pode ser usado na aplicação iniciada pelo IntelliJ em `/admin/login`, sem reiniciar o banco. Nenhuma chamada esportiva foi realizada nessa operação.

O [AGENTS.md da raiz](../AGENTS.md) permanece no lugar; seu inventário foi atualizado na auditoria de 31/10/2026. Os novos perfis complementam [Backend](agents/backend.md), [Frontend](agents/frontend.md), [DBA](agents/database.md), [DevOps](agents/devops.md) e [Segurança](agents/security.md), que foram preservados.

A descrição antiga de repositório apenas documental foi superada pela existência de código; o inventário observado está no PRD §0.1. Histórico de execução e pesquisa permanecem em planning/ e research/. Não tratar resultados históricos como validação atual nem iniciar agentes/serviços por causa destes arquivos.

## Atualização de 30/10/2026

PRD 1.3 centralizado em docs/, backlog extraído sem renumerar tarefas, perfis solicitados adicionados e PRD.md da raiz convertido em encaminhamento. Nenhuma funcionalidade da aplicação foi modificada por essa organização documental.
