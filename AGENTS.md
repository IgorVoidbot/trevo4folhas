# Repository Guidelines

## Produto e regras globais

O TREVO4FOLHAS.com gerencia pré-bilhetes de futebol. Leia o [PRD](PRD.md) e as seções relevantes antes de implementar. O apostador não tem conta ou carteira: seleciona palpites, informa nome/WhatsApp, gera código e conversa com a banca. Somente operador autenticado confirma após conferir Pix manualmente. Geração e clique no WhatsApp nunca confirmam pagamento.

- Código, identificadores, endpoints, DTOs, schema e logs em inglês; toda interface pública/admin e mensagens visíveis em pt-BR.
- Aspas simples em JS, CSS e atributos HTML quando possível; respeitar strings Java/JSON e formatadores configurados.
- Monólito simples: Java 17+, Spring Boot 3, PostgreSQL 16, HTML/TailwindCSS e Vanilla JS. Não adicionar carteira, SPA, filas ou microsserviços sem requisito.
- Usar `BigDecimal`/`numeric`, snapshots de odds, transações, idempotência e expiração no backend. Não confiar em valores financeiros calculados pelo navegador.
- Nunca versionar segredos, expor dados pessoais em logs ou permitir busca pública de bilhetes por código/telefone.

## Estado e mapa do repositório

- `PRD.md` contém requisitos, arquitetura (§8), histórias e aceites (§10) e backlog (§13). Sua árvore proposta não prova implementação; descrições antigas de ausência de aplicação/manifests são fotografias da revisão documental, não o estado corrente. Regras de negócio permanecem vigentes.
- Existem `pom.xml`, Maven Wrapper, `package.json`, `package-lock.json`, Dockerfile, `docker-compose.yml` e `.env.example`. O POM fixa Spring Boot 3.5.16 e Java 17; configura Spotless, JUnit, Testcontainers e relatório JaCoCo. Scripts npm fornecem lint, formatação e compilação Tailwind. Não há README, Makefile ou workflows de CI versionados nesta base.
- `src/main/java/com/trevo4folhas/` contém a aplicação, configuração comercial/relógio, seis entidades, repositories e DTOs. `src/main/resources/db/migration/` contém Flyway V1–V8; templates contêm fragmentos base/CSRF e `src/frontend/styles/` contém a entrada Tailwind. Há testes em `src/test/java/`. A presença de Spring Security como dependência não comprova login administrativo implementado.
- `docs/agents/` preserva os cinco perfis obrigatórios; os `AGENTS.md` locais continuam aplicáveis. `docs/research/` é baseline de homologação, não paridade comprovada. `docs/planning/` reúne contratos, decisões e evidências históricas, incluindo o [registro do PRD 1.2](docs/planning/prd-1.2-contract-sync.md).
- Leia o [estado comprovado](docs/PROJECT_STATE.md) e o [fluxo VPS/local](docs/DEVELOPMENT_WORKFLOW.md) antes de retomar. Código no Git, status do Kanban e arquivos não publicados são fontes distintas: uma tarefa `done` no quadro não comprova que seus fontes estão nesta branch.

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

1. Antes de retomar, verifique branch, alterações locais/staged/não rastreadas, histórico, PRs e alterações remotas após `git fetch`. Preserve arquivos locais e não use stash, reset, clean, rebase ou resolução automática de conflitos sem autorização específica.
2. Nunca implementar ou criar commits diretamente na `main`. Trabalhe em uma branch de tarefa baseada em referência remota conferida. Não troque de branch sobre trabalho pendente sem um plano de preservação aprovado.
3. Uma tarefa por vez e um responsável de escrita por branch. Identifique requisito, arquivos, perfis, critérios de aceite e ponto de passagem VPS/local; não editar simultaneamente os mesmos arquivos em clones diferentes.
4. Defina contratos entre API, interface e schema antes de mudanças que cruzem camadas. Implemente a menor solução completa; sincronize migrations, testes e documentação quando necessário.
5. Execute os testes e checks pertinentes antes de enviar alterações; registre comandos, resultados e limitações. Para documentação isolada, revise links, comandos, consistência e `git diff --check`; não apresente isso como validação funcional da aplicação.
6. Revise o diff e os arquivos staged, faça commit em inglês e envie somente a branch autorizada. Crie Pull Request destinado à `main` para revisão; use Draft enquanto houver trabalho ou validação pendente. Não misture documentação com uma implementação posterior na mesma branch.
7. Nunca fazer merge ou deploy sem autorização explícita para a operação. Aprovação de implementação, commit, push ou criação de PR não autoriza integração à `main`, publicação de outra branch ou implantação.
8. Nunca versionar credenciais, tokens ou arquivos `.env` reais, nem incluí-los em logs, diffs ou PRs. Somente `.env.example` com placeholders pode ser versionado. Adicione arquivos por caminhos explícitos, nunca com `git add .` ou `git add -A` sem revisão individual.

Na VPS, todas as operações do GitHub CLI deste projeto usam `/home/hermes/.local/bin/trevo-gh`, nunca um `gh` sem o wrapper. Git e GitHub CLI têm transportes/autenticações distintos; validar a API não prova autorização de push. Nunca imprimir credenciais ou copiar arquivos de autenticação entre máquinas.

Testes Java seguem `src/test/java`, JUnit 5 e PostgreSQL 16 real via Testcontainers; MockMvc atende testes HTTP quando implementados. Priorizar cálculos, rollback, idempotência, concorrência, expiração e autorização. JaCoCo gera relatório, mas o POM atual não configura um gate de cobertura mínima; o requisito do PRD continua pendente de comprovação. Não inventar resultados de testes, benchmarks ou revisão especializada.

## Ambiente e comandos existentes

Os manifests declaram os comandos abaixo. Sua existência não garante execução em toda máquina: conferir Java 17 compatível, Node compatível com `package.json`, dependências, variáveis e Docker/Testcontainers isolado. Falha estrutural exige diagnóstico, não H2, testes ignorados ou ampliação silenciosa de privilégios.

- Banco local: `docker compose up -d db`.
- Dependências de assets: `npm ci`; CSS em desenvolvimento: `npm run css:watch`.
- Aplicação local: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, em terminal separado do watcher CSS.
- Checks frontend: `npm run lint` e `npm run format:check`; gerar CSS antes do pacote Java: `npm run css:build`.
- Checks Java, testes e cobertura: `./mvnw spotless:check verify`; o contrato inclui integração via Testcontainers e exige Docker disponível.
- Executar o pacote após build bem-sucedido: `java -jar target/trevo4folhas.jar`. Não afirmar que o artefato existe antes de conferir.

## Cuidados na implementação

- CSS é gerado a partir de `src/frontend/styles/` para `src/main/resources/static/css/app.css`; não editar o resultado gerado nem usar Play CDN em produção. Usar classes Tailwind completas.
- Dentro do container `app`, o banco é acessado pelo hostname `db`, não `localhost`. Compose recebe variáveis de ambiente/`.env`; o processo Java exige suas variáveis exportadas e não lê automaticamente o `.env` do Compose. Consulte o template `.env.example` sem imprimir valores reais. Não iniciar a aplicação ou tratar seu estado como seguro apenas por existir um manifesto.

## Commits e pull requests

Usar assuntos concisos e imperativos em inglês, por exemplo `Add ticket confirmation validation`; essa é a convenção prescrita para novas contribuições, não inferida do histórico. PRs descrevem problema, mudança, validação e impacto nos contratos, com issues e screenshots quando aplicáveis. Não incluir refatorações sem relação com a tarefa.

## Manutenção das instruções

Instruções explícitas do usuário prevalecem sobre convenções locais. Os perfis especializam estas regras sem alterar o modelo de negócio; mantenha decisões compartilhadas e PRD sincronizados. Não usar áreas de ambiente `.agents/`, `.codex/`, `.aws/` ou `.git/` para armazenar esses perfis.

A organização por diretório segue o [guia oficial de AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md). A descoberta automática depende do diretório de execução; ao editar subdiretórios desde a raiz, leia explicitamente as instruções locais indicadas acima.
