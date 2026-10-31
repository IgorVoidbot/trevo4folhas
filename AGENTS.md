# Repository Guidelines

## Produto e regras globais

O TREVO4FOLHAS.com gerencia pré-bilhetes de futebol. Leia o [PRD](PRD.md) e as seções relevantes antes de implementar. O apostador não tem conta ou carteira: seleciona palpites, informa nome/WhatsApp, gera código e conversa com a banca. Somente operador autenticado confirma após conferir Pix manualmente. Geração e clique no WhatsApp nunca confirmam pagamento.

- Código, identificadores, endpoints, DTOs, schema e logs em inglês; toda interface pública/admin e mensagens visíveis em pt-BR.
- Aspas simples em JS, CSS e atributos HTML quando possível; respeitar strings Java/JSON e formatadores configurados.
- Monólito simples: Java 17+, Spring Boot 3, PostgreSQL 16, HTML/TailwindCSS e Vanilla JS. Não adicionar carteira, SPA, filas ou microsserviços sem requisito.
- Usar `BigDecimal`/`numeric`, snapshots de odds, transações, idempotência e expiração no backend. Não confiar em valores financeiros calculados pelo navegador.
- Nunca versionar segredos, expor dados pessoais em logs ou permitir busca pública de bilhetes por código/telefone.

## Estado e mapa do repositório

- `PRD.md` encaminha ao PRD canônico em `docs/PRD.md`, com requisitos e contratos; `docs/SPRINTS.md` contém os checklists reconciliados. A árvore proposta no PRD não substitui o inventário do código.
- `docs/agents/` contém os cinco perfis obrigatórios e perfis complementares de arquitetura/backend/frontend. Backend, banco, templates, assets, fontes frontend e `deploy/` já contêm implementação; leia seus `AGENTS.md` locais antes de editar.
- `docs/research/` contém o levantamento de mercados pré-jogo e seus dados de evidência; é baseline de homologação, não paridade comprovada de fornecedor. `docs/planning/` contém o plano do quadro, autorizações, preflight e o [registro de sincronização de contratos com o PRD 1.2](docs/planning/prd-1.2-contract-sync.md).
- Há `pom.xml`, Maven Wrapper, `package.json`, lockfile, Dockerfile, Compose, testes e checks de formatação/lint. Setup em `docs/README.md`. Não há workflow de CI remoto nem Makefile; não declarar pipeline remoto aprovado.
- Estado auditado em 31/10/2026: fundação técnica e catálogo/admin locais implementados; Sprint 2 entregue com ofertas fictícias; decisões S0.01 e homologação S1.06 abertas; fluxos S3–S5 não implementados. Consulte [auditoria](docs/planning/project-audit-2026-10-31.md) para evidências e limitações; não inferir emissão/confirmação pela presença de entidades e tabelas.

## Seleção obrigatória dos especialistas

Antes de alterar um arquivo, leia o perfil correspondente e os `AGENTS.md` do caminho, mesmo trabalhando a partir da raiz. Tarefas que atravessam camadas aplicam todos os perfis envolvidos. Os arquivos abaixo contêm instruções obrigatórias; links não substituem sua leitura.

| Perfil | Escopo e instruções |
| --- | --- |
| 1 — Backend | `src/main/java/com/trevo4folhas/**`, testes Java e dependências Java: [backend.md](docs/agents/backend.md) |
| 2 — Frontend | `src/main/resources/static/**`, `src/main/resources/templates/**`, `src/frontend/**` e ferramentas JS/CSS: [frontend.md](docs/agents/frontend.md) |
| 3 — DBA | `src/main/resources/db/**`, schema, SQL e consultas/repositories afetados: [database.md](docs/agents/database.md) |
| 4 — DevOps | `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `.gitignore`, Maven/build/CI e `deploy/**`: [devops.md](docs/agents/devops.md) |
| 5 — Segurança | Autenticação, autorização, entradas/saídas, HTTP, dados pessoais e segredos, em qualquer camada: [security.md](docs/agents/security.md) |

Backend e Segurança compartilham `SecurityConfig`; Frontend e Segurança compartilham formulários; Backend e DBA compartilham persistência; DevOps e Segurança compartilham infraestrutura. Nenhum perfil autoriza ignorar as regras do outro.

Os perfis são personas e contratos de execução. Não iniciam processos ou subagentes automaticamente. Quando houver delegação, transmitir tarefa, arquivos permitidos, perfis aplicáveis, critérios de aceite e verificações; não delegar escrita simultânea do mesmo arquivo.

## Contratos compartilhados

- Seis entidades de domínio: `Admin`, `Match`, `Market`, `MarketSelection`, `Ticket` e `TicketItem` (PRD §8.3), no caminho `Match → Market → MarketSelection`; o contrato de quatro entidades da v1.1 está revisto e `HOME`/`DRAW`/`AWAY` são codes apenas de 1X2.
- Emissão e APIs usam `matchVersion`/`marketVersion`/`selectionVersion`; `TicketItem` preserva snapshots imutáveis. Identidade canônica distingue namespaces e usa sentinela `NONE` para ausência; contratos e decisões estão no PRD §8.3 e no [registro de sincronização](docs/planning/prd-1.2-contract-sync.md).
- Frescor usa relógios independentes e bloqueio conservador (fail-closed); sync fica desligada por padrão, com política experimental de quota/frescor registrada antes de habilitar importação. API-Football é provisória; mercados da matriz não são reduzidos e paridade não é alegada. Valores de desenvolvimento não autorizam chamadas reais nem definem tolerância de produção.
- Banco de referência: PostgreSQL **16**, inclusive testes e imagens Docker. Flyway é a ferramenta escolhida; não misturar com Liquibase.
- Colunas físicas: `tickets.ticket_code` e `matches.match_date`, com índices adequados. Não criar duplicatas `code`/`starts_at`; propriedades Java/JSON podem manter `code`/`startsAt` com mapeamento explícito.
- Toda entidade de domínio estende `BaseEntity`; `created_at` e `updated_at` usam `TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL`, com atualização automática de `updated_at` e conexões UTC. Datas de negócio mantêm `timestamptz`.
- Compose canônico: `docker-compose.yml`; serviços `app` e `db`, hostname interno `db` e volume nomeado para o banco.
- Desempenho: meta p95 **< 200 ms** para catálogo, detalhe, emissão e confirmação, nas condições do PRD. Medir; não afirmar atendimento sem evidência nem reduzir proteção de senha para atingir latência.

## Fluxo de execução e verificação

1. Identifique requisito, arquivos e perfis aplicáveis; mantenha alterações focadas na tarefa autorizada.
2. Defina contratos entre API, interface e schema antes de mudanças que cruzem camadas.
3. Implemente a menor solução completa; sincronize migrations, testes e documentação quando necessário.
4. Execute verificações apropriadas existentes e relate resultados, limitações e pendências reais.

O projeto é executável. Use os manifests presentes e as verificações pertinentes à alteração. Não inventar resultados de testes, benchmarks ou revisão especializada; distinguir execução atual de evidência histórica.

Testes Java seguem `src/test/java`, JUnit 5, MockMvc e PostgreSQL 16 via Testcontainers. Priorizar cálculos, rollback, idempotência, concorrência, expiração e autorização. JaCoCo gera relatório; o limiar do PRD ainda não é imposto automaticamente pelo build.

## Ambiente e comandos disponíveis

Use Java 17 para os checks atuais: o formatter fixado não suporta a JVM 25 disponível no ambiente. Dependências, PostgreSQL e Testcontainers exigem os acessos locais correspondentes. Configuração e execução estão em `docs/README.md`.

- Banco local: `docker compose --env-file .env.example --env-file .env up -d --wait db`; `POSTGRES_PORT` permite evitar conflito com outro PostgreSQL local.
- Dependências de assets: `npm ci`; CSS em desenvolvimento: `npm run css:watch`.
- Aplicação local: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, em terminal separado do watcher CSS.
- Checks frontend: `npm run lint` e `npm run format:check`; gerar CSS antes do pacote Java: `npm run css:build`.
- Checks Java, testes e cobertura: `./mvnw spotless:check verify`; o contrato inclui integração via Testcontainers e exige Docker disponível.
- Cupom: `node src/frontend/tests/slip-state.test.mjs`; testes de navegador adicionais estão em `src/frontend/tests/`, com seus requisitos documentados.
- Executar o pacote após gerar CSS e empacotar: `java -jar target/trevo4folhas.jar`, com ambiente/perfil configurado. Não versionar `target/`.

## Cuidados na implementação

- CSS é gerado a partir de `src/frontend/styles/` para `src/main/resources/static/css/app.css`; não editar o resultado gerado nem usar Play CDN em produção. Usar classes Tailwind completas.
- Dentro do container `app`, acessar o banco pelo hostname `db`, não `localhost`. O perfil `local` carrega `.env` como propriedades Java; outros ambientes usam variáveis/segredos do runtime. `.env`, `.env.admin-access` e demais credenciais locais são ignorados e nunca devem ser versionados. Sync continua desligada por padrão.

## Commits e pull requests

Usar assuntos concisos e imperativos em inglês, por exemplo `Add ticket confirmation validation`; essa é a convenção prescrita para novas contribuições, não inferida do histórico. PRs descrevem problema, mudança, validação e impacto nos contratos, com issues e screenshots quando aplicáveis. Não incluir refatorações sem relação com a tarefa.

## Manutenção das instruções

Instruções explícitas do usuário prevalecem sobre convenções locais. Os perfis especializam estas regras sem alterar o modelo de negócio; mantenha decisões compartilhadas e PRD sincronizados. Não usar áreas de ambiente `.agents/`, `.codex/`, `.aws/` ou `.git/` para armazenar esses perfis.

A organização por diretório segue o [guia oficial de AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md). A descoberta automática depende do diretório de execução; ao editar subdiretórios desde a raiz, leia explicitamente as instruções locais indicadas acima.
