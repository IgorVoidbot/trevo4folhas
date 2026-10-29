# TREVO4FOLHAS — estado comprovado do projeto

## Referência desta inspeção

- Inspeção inicial em **29/10/2026 (UTC)**, na VPS, no projeto `/home/hermes/projects/trevo4folhas`; estado Docker complementado por leitura direta em **29/10/2026 às 14:16 UTC**.
- Repositório confirmado pela API: `IgorVoidbot/trevo4folhas`; branch padrão: `main`.
- Na inspeção inicial do PR, a cópia estava limpa na `main`. Após `git fetch --no-tags origin main`, `HEAD` e `origin/main` apontavam para `ad58667f4627f2d23b4b88e79e26a8f2b57764ed`, sem commits exclusivos em nenhum lado.
- A branch `hermes/docs-development-workflow` foi criada a partir dessa referência. Este incremento altera apenas documentação; não recupera nem implementa T4F-007/T4F-008.
- Este documento é uma fotografia, não um monitor. Antes de retomar, repetir a inspeção local/remota descrita no [fluxo de desenvolvimento](DEVELOPMENT_WORKFLOW.md).

## O que existe no código versionado

A inspeção de `git ls-files`, dos manifests e dos fontes confirmou:

- Fundação Maven Wrapper/Java 17/Spring Boot **3.5.16**, com configuração comercial validada e `Clock` injetável.
- Seis entidades de domínio e seus repositories/DTOs: `Admin`, `Match`, `Market`, `MarketSelection`, `Ticket` e `TicketItem`, com `BaseEntity`, auditoria, versões e precisão decimal.
- Oito migrations Flyway **V1–V8**, schema de histórico separado e PostgreSQL **16** como banco de referência.
- Testes Java de configuração, migrations, persistência, round-trip, regressão de metadados operacionais e serialização financeira como strings JSON. São **22 arquivos Java de produção e 11 arquivos Java na árvore de testes**, incluindo a fixture `TestDb`; isso não é contagem de casos de teste.
- `package.json`/lockfile, entrada Tailwind, CSS gerado e fragmentos Thymeleaf de layout/CSRF. Existem scripts `lint`, `format:check`, `css:build` e `css:watch`.
- Dockerfile e Compose canônico com serviços `app`/`db`, PostgreSQL 16 e volume nomeado. Além da presença dos manifests, a revisão abaixo comprova containers existentes `running`/`healthy`, startup Spring Boot e migrations aplicadas; isso não comprova prontidão para produção nem aprovação da suíte automatizada.
- `.env` local ignorado pelo Git; entre arquivos `.env*`, somente o template `.env.example` está versionado. Nenhum conteúdo ou valor do `.env` foi exposto ou copiado para esta documentação; as consultas Compose utilizaram a configuração existente sem imprimir o ambiente resolvido.
- Não há README, Makefile ou workflows de CI versionados nesta base. JaCoCo possui geração de relatório, mas o POM não define gate automático de cobertura mínima.

Não estão nesta árvore de fontes a implementação de autenticação administrativa da T4F-007 (`SecurityConfig`, login, bootstrap e respectivos testes), nem os controllers/serviços HTTP de catálogo da T4F-008. Dependências Spring Security e entidades de catálogo não equivalem a essas funcionalidades.

## Docker e execução da aplicação na VPS — verificação atual

Em **29/10/2026 às 14:16 UTC**, foram feitas somente consultas sobre o runtime já existente:

- Serviço `docker`: `ActiveState=active`, `SubState=running`; Docker Engine **29.9.2** respondeu à API pelo socket atual `/var/run/docker.sock`.
- `docker compose ps --all --format json` confirmou `app` (`trevo4folhas-app-1`) e `db` (`trevo4folhas-db-1`), ambos **running / healthy**. Eles já estavam em execução; esta revisão não iniciou, recriou nem reiniciou containers.
- A leitura dos logs existentes de `app` encontrou **`Started TrevoApplication in 5.332 seconds`**, confirmando startup da aplicação Spring Boot.
- Os mesmos logs registram **`Successfully applied 8 migrations`**, com versão final **v8**. Uma consulta SQL em transação **READ ONLY** a `t4f_flyway.flyway_schema_history`, dentro do container `db`, confirmou exatamente as versões **V1–V8**, todas com `success=true`. Nenhuma migration foi executada novamente por esta revisão.

A primeira consulta desta sessão ao socket atual retornou `permission denied`: `hermes` já constava como membro do grupo `docker`, mas o grupo não estava carregado no processo da sessão. As leituras acima foram executadas com `sg docker`, usando essa associação existente, sem sudo, mudança de grupos/permissões ou reinício de serviços. A falha inicial de acesso desta sessão não era evidência de daemon indisponível.

**O socket Docker privado antigo do quadro continua ausente, mas isso não significa que o Docker atual esteja indisponível.** A inspeção inicial da imagem antiga de worker por digest também só tratava daquele artefato, não da disponibilidade global do daemon. Os registros históricos de sandbox/rootless permanecem preservados e não são substituídos por esta constatação.

**A suíte automatizada com Testcontainers ainda precisa ser revalidada neste ambiente.** Compose saudável, startup e Flyway aplicados comprovam execução da stack, não testes unitários/integração, cobertura ou aceite funcional/de segurança. O health de `app` no manifesto atual é uma sonda TCP, não uma prova do fluxo de negócio. Nenhuma suíte automatizada da aplicação foi executada nesta revisão.

Revalidar separadamente o executor Java/Maven, o acesso autorizado ao daemon de testes, PostgreSQL 16 descartável, Ryuk e cleanup. A consulta de coordenação ao Docker atual não autoriza montar seu socket nos workers, ampliar privilégios nem usar o banco `db` da aplicação para testes destrutivos. Ausência de Java/Maven no PATH do host não implica ausência do runtime Java no container.

## Últimas alterações publicadas e evidências históricas

1. `603ccc7` — `docs: add comprehensive PRD.md and multi-agent architecture guidelines`: PRD, instruções e perfis de especialistas.
2. `ad58667` — `Add verified foundation and catalog persistence`: checkpoint de fundação e persistência disponível em `origin/main`.
3. `638d87c` — `docs: establish collaborative development workflow`: atualização do `AGENTS.md`, este estado comprovado e procedimento de colaboração VPS/local, publicados na branch `hermes/docs-development-workflow`. O [Draft PR #1](https://github.com/IgorVoidbot/trevo4folhas/pull/1) está aberto para `main`, sem merge.
4. Nesta revisão documental do PR: confirmação do Docker/Compose e do startup/Flyway existentes, distinguindo-os da suíte Testcontainers ainda não revalidada. Não houve alteração de código ou operação de implantação.

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
2. **Suíte automatizada ainda não revalidada na VPS:** o Docker atual está operacional, `app`/`db` estão `healthy` e startup/Flyway estão comprovados. O que continua pendente é revalidar o executor e a suíte com Testcontainers no ambiente autorizado; o socket privado e a imagem antiga são recursos distintos do Compose atual. Não substituir PostgreSQL/Testcontainers por H2 nem usar o banco da aplicação para testes destrutivos.
3. **Caminhos antigos:** quadro e perfis de execução ainda fazem referência ao host anterior. Qualquer migração de runtime/perfis exige escopo explícito, preservação das restrições e novo preflight; este PR não os altera.
4. **Escopo funcional:** autenticação, catálogo HTTP e demais fluxos públicos/admin ainda precisam estar disponíveis e comprovados nesta base antes de se declarar MVP. README, CI e gate de cobertura continuam pendências técnicas, sem implementação neste PR.
5. **Fornecedor e operação:** API-Football continua provisória. Os gates 028/029 descritos no plano exigem autorização/credenciais/orçamento específicos, homologação e decisão humana; não houve chamada real, contratação, merge ou deploy nesta tarefa. A baseline de mercados não comprova paridade operacional.

## Validação deste incremento documental

A entrega exige revisão do diff, consistência com arquivos reais, preservação dos cinco perfis e das regras de negócio, links locais válidos, sintaxe dos exemplos shell, ausência de credenciais no patch e `git diff --check`/`git diff --cached --check`.

Não houve execução de suites Java/PostgreSQL, lint frontend ou build da aplicação neste incremento, que não altera código nem manifests. A revisão do Docker fez apenas leituras de serviço, API/Compose, logs selecionados e histórico Flyway em transação SQL READ ONLY; não executou deploy, restart ou aplicação de migrations. Os números de testes acima pertencem às evidências históricas identificadas, não são resultados atuais. Resultados efetivos das verificações documentais devem acompanhar o commit/PR.

## Próxima ação concreta

Com o Draft PR #1 aberto, **comparar o snapshot de fontes aprovado da T4F-007 com o Git atual e com o WIP preservado no Hermes local**, em recuperação isolada e sem sobrescrever o checkout. Identificar o checkpoint real da T4F-008 e seu teste pendente. Só então preparar uma branch técnica exclusiva e revalidar o executor e a suíte Testcontainers no ambiente autorizado para um primeiro ciclo de teste RED → GREEN da T4F-008; não recriar a stack Compose saudável apenas por faltar o socket antigo.

A continuidade da T4F-008 foi solicitada pelo usuário, mas não autoriza ignorar esses bloqueios, misturar código neste PR documental, ampliar privilégios, fazer merge ou deploy. A publicação autorizada nesta entrega restringe-se à branch de documentação.
