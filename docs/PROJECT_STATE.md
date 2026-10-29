# TREVO4FOLHAS — estado comprovado do projeto

## Referência desta inspeção

- Verificação em **29/10/2026 (UTC)**, na VPS, no projeto `/home/hermes/projects/trevo4folhas`.
- Repositório confirmado pela API: `IgorVoidbot/trevo4folhas`; branch padrão: `main`.
- Antes desta tarefa, a cópia estava limpa na `main`. Após `git fetch --no-tags origin main`, `HEAD` e `origin/main` apontavam para `ad58667f4627f2d23b4b88e79e26a8f2b57764ed`, sem commits exclusivos em nenhum lado.
- A branch `hermes/docs-development-workflow` foi criada a partir dessa referência. Este incremento altera apenas documentação; não recupera nem implementa T4F-007/T4F-008.
- Este documento é uma fotografia, não um monitor. Antes de retomar, repetir a inspeção local/remota descrita no [fluxo de desenvolvimento](DEVELOPMENT_WORKFLOW.md).

## O que existe no código versionado

A inspeção de `git ls-files`, dos manifests e dos fontes confirmou:

- Fundação Maven Wrapper/Java 17/Spring Boot **3.5.16**, com configuração comercial validada e `Clock` injetável.
- Seis entidades de domínio e seus repositories/DTOs: `Admin`, `Match`, `Market`, `MarketSelection`, `Ticket` e `TicketItem`, com `BaseEntity`, auditoria, versões e precisão decimal.
- Oito migrations Flyway **V1–V8**, schema de histórico separado e PostgreSQL **16** como banco de referência.
- Testes Java de configuração, migrations, persistência, round-trip, regressão de metadados operacionais e serialização financeira como strings JSON. São **22 arquivos Java de produção e 11 arquivos Java na árvore de testes**, incluindo a fixture `TestDb`; isso não é contagem de casos de teste.
- `package.json`/lockfile, entrada Tailwind, CSS gerado e fragmentos Thymeleaf de layout/CSRF. Existem scripts `lint`, `format:check`, `css:build` e `css:watch`.
- Dockerfile e Compose canônico com serviços `app`/`db`, PostgreSQL 16 e volume nomeado. A presença desses arquivos não comprova aplicação em execução ou pronta para produção.
- `.env` local ignorado pelo Git; entre arquivos `.env*`, somente o template `.env.example` está versionado. Nenhum conteúdo do `.env` foi lido para esta documentação.
- Não há README, Makefile ou workflows de CI versionados nesta base. JaCoCo possui geração de relatório, mas o POM não define gate automático de cobertura mínima.

Não estão nesta árvore de fontes a implementação de autenticação administrativa da T4F-007 (`SecurityConfig`, login, bootstrap e respectivos testes), nem os controllers/serviços HTTP de catálogo da T4F-008. Dependências Spring Security e entidades de catálogo não equivalem a essas funcionalidades.

## Últimas alterações publicadas e evidências históricas

1. `603ccc7` — `docs: add comprehensive PRD.md and multi-agent architecture guidelines`: PRD, instruções e perfis de especialistas.
2. `ad58667` — `Add verified foundation and catalog persistence`: checkpoint de fundação e persistência disponível em `origin/main`.
3. Neste incremento documental: atualização do `AGENTS.md`, este estado comprovado e procedimento de colaboração VPS/local. Publicação prevista exclusivamente na branch `hermes/docs-development-workflow`, via Draft PR para `main`, sem merge.

O [aceite da T4F-006](planning/t4f-006-review.md), datado de 25/10/2026, registra build completo com **72 testes, zero falhas/erros/skips**, lint/formatação aprovados e quatro sondas independentes separadas, sobrepostas ao escopo principal. Esses resultados são históricos; não foram reexecutados na VPS nesta tarefa de documentação.

## Git e Kanban não estão no mesmo checkpoint

O quadro `trevo4folhas` foi consultado explicitamente, lendo cartões, dependências, comentários e runs:

- **T4F-006** (`t_e9a5dbdb`): `done`. Seu checkpoint de publicação corresponde ao commit `ad58667` disponível no GitHub.
- **T4F-007** (`t_9452e15b`): `done`. O run 20 registra aprovação de 105 testes e cinco sondas QA; o run 19 permanece `gave_up` no histórico. Isso comprova o aceite histórico registrado, não presença dos fontes no Git atual nem execução nesta VPS.
- **T4F-008** (`t_f6b950d5`): `ready`, atribuída a `default`, sem run corrente. O run 21 do Backend está `reclaimed`: a implementação foi assumida pelo coordenador. Não há entrega concluída desta etapa. A retomada técnica continua impedida pelos bloqueios abaixo; o status do cartão não foi alterado por este PR.
- Os cartões ainda apontam para o workspace antigo `/home/igor/Área de trabalho/Projeto-aposta`, inexistente nesta VPS. Não é correto interpretar `ready` como worker ativo ou ambiente funcional.

Os anexos da T4F-007 foram localizados no armazenamento local do quadro. Dois bundles tiveram seus SHA-256 recalculados e comparados aos registros de aceite:

- `t4f007-backend-final-evidence.tar.gz`: `409ed34fe8b6fad2b52f3a09d7a6cc60041f1c06e067e47f145e584faeca9b41`. Contém o membro `target/review-007-final/tested-source.tar.gz`.
- `t4f007-qa-final-evidence.tar.gz`: `75723757a5daad3895c62138dc9f85fff6f70347d64805ae43ff72a8cb61fa09`.

Conferir o digest de um bundle não reexecuta testes nem valida uma restauração. Os anexos e o banco Kanban **não fazem parte do checkout Git**; o Hermes local não os recebe por `git pull`. Nenhum bundle foi aplicado sobre os fontes nesta tarefa. A localização de um checkpoint completo da T4F-008 não foi comprovada nesta inspeção; verificar também a cópia do Hermes local antes de reconstruir trabalho.

## Bloqueios e pendências

1. **Base de código incompleta para retomar T4F-008:** o Git publicado termina na T4F-006, embora o quadro registre a T4F-007 aprovada. Recuperar e revisar sua base antes de implementar catálogo dependente de autenticação; preservar qualquer WIP que exista no computador local.
2. **Ambiente de testes não revalidado na VPS:** `java` e `mvn` não estão no PATH da sessão; o socket Docker privado do quadro não existe. A consulta à imagem antiga de worker por digest falhou, portanto sua disponibilidade não foi comprovada. Não substituir PostgreSQL/Testcontainers por H2 nem usar o banco da aplicação para testes destrutivos.
3. **Caminhos antigos:** quadro e perfis de execução ainda fazem referência ao host anterior. Qualquer migração de runtime/perfis exige escopo explícito, preservação das restrições e novo preflight; este PR não os altera.
4. **Escopo funcional:** autenticação, catálogo HTTP e demais fluxos públicos/admin ainda precisam estar disponíveis e comprovados nesta base antes de se declarar MVP. README, CI e gate de cobertura continuam pendências técnicas, sem implementação neste PR.
5. **Fornecedor e operação:** API-Football continua provisória. Os gates 028/029 descritos no plano exigem autorização/credenciais/orçamento específicos, homologação e decisão humana; não houve chamada real, contratação, merge ou deploy nesta tarefa. A baseline de mercados não comprova paridade operacional.

## Validação deste incremento documental

A entrega exige revisão do diff, consistência com arquivos reais, preservação dos cinco perfis e das regras de negócio, links locais válidos, sintaxe dos exemplos shell, ausência de credenciais no patch e `git diff --check`/`git diff --cached --check`.

Não houve execução de suites Java/PostgreSQL, lint frontend ou build da aplicação neste incremento, que não altera código nem manifests. Os números de testes acima pertencem às evidências históricas identificadas, não são resultados atuais. Resultados efetivos das verificações documentais devem acompanhar o commit/PR.

## Próxima ação concreta

Após publicar o Draft PR documental, **comparar o snapshot de fontes aprovado da T4F-007 com o Git atual e com o WIP preservado no Hermes local**, em recuperação isolada e sem sobrescrever o checkout. Identificar o checkpoint real da T4F-008 e seu teste pendente. Só então preparar uma branch técnica exclusiva e revalidar o ambiente isolado para um primeiro ciclo de teste RED → GREEN da T4F-008.

A continuidade da T4F-008 foi solicitada pelo usuário, mas não autoriza ignorar esses bloqueios, misturar código neste PR documental, ampliar privilégios, fazer merge ou deploy. A publicação autorizada nesta entrega restringe-se à branch de documentação.
