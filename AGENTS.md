# Repository Guidelines

## Produto e regras globais

O TREVO4FOLHAS.com gerencia pré-bilhetes de futebol. Leia o [PRD](PRD.md) e as seções relevantes antes de implementar. O apostador não tem conta ou carteira: seleciona palpites, informa nome/WhatsApp, gera código e conversa com a banca. Somente operador autenticado confirma após conferir Pix manualmente. Geração e clique no WhatsApp nunca confirmam pagamento.

- Código, identificadores, endpoints, DTOs, schema e logs em inglês; toda interface pública/admin e mensagens visíveis em pt-BR.
- Aspas simples em JS, CSS e atributos HTML quando possível; respeitar strings Java/JSON e formatadores configurados.
- Monólito simples: Java 17+, Spring Boot 3, PostgreSQL 16, HTML/TailwindCSS e Vanilla JS. Não adicionar carteira, SPA, filas ou microsserviços sem requisito.
- Usar `BigDecimal`/`numeric`, snapshots de odds, transações, idempotência e expiração no backend. Não confiar em valores financeiros calculados pelo navegador.
- Nunca versionar segredos, expor dados pessoais em logs ou permitir busca pública de bilhetes por código/telefone.

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

O repositório ainda contém documentação e instruções; não há aplicação ou build executável. Comandos previstos no PRD, como `./mvnw verify` e `npm run lint`, só podem ser executados após os arquivos correspondentes existirem. Não inventar resultados de testes, benchmarks ou revisão especializada.

Testes Java seguem `src/test/java`, JUnit 5, MockMvc e PostgreSQL via Testcontainers. Priorizar cálculos, rollback, idempotência, concorrência, expiração e autorização. Adotar cobertura e checks definidos no PRD quando o build for criado.

## Commits e pull requests

Usar assuntos concisos e imperativos em inglês, por exemplo `Add ticket confirmation validation`; não há histórico disponível para inferir outra convenção. PRs descrevem problema, mudança, validação e impacto nos contratos, com issues e screenshots quando aplicáveis. Não incluir refatorações sem relação com a tarefa.

## Manutenção das instruções

Instruções explícitas do usuário prevalecem sobre convenções locais. Os perfis especializam estas regras sem alterar o modelo de negócio; mantenha decisões compartilhadas e PRD sincronizados. Não usar áreas de ambiente `.agents/`, `.codex/`, `.aws/` ou `.git/` para armazenar esses perfis.

A organização por diretório segue o [guia oficial de AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md). A descoberta automática depende do diretório de execução; ao editar subdiretórios desde a raiz, leia explicitamente as instruções locais indicadas acima.
