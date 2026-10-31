# TREVO4FOLHAS — desenvolvimento colaborativo VPS/local

> **Atualização de 31/10/2026:** o PR documental #1 já foi integrado. As branches `hermes/docs-development-workflow` e `wip/t4f-008-cloud-handoff` são referências históricas, não bases para novas tarefas. Os exemplos com esses nomes e com a antiga T4F-008 devem ser lidos nesse contexto. Use a `origin/main` atualizada como base após o preflight; confira a [auditoria atual](planning/project-audit-2026-10-31.md) e os [checklists](SPRINTS.md). O [PROJECT_STATE.md](PROJECT_STATE.md) preserva a fotografia da VPS de 29/10, sem substituir o estado local atual. A consolidação em andamento está registrada [aqui](planning/branch-consolidation-2026-10-31.md).

## Regras permanentes

- Nunca implementar ou criar commits diretamente na `main`.
- Uma tarefa por vez; uma branch por tarefa e um responsável de escrita por branch. VPS e Hermes local não editam a mesma tarefa simultaneamente.
- Conferir alterações locais e remotas antes de retomar. Preservar trabalho existente; não descartar, fazer stash, reset, clean, rebase ou sobrescrever arquivos automaticamente.
- Ler [AGENTS.md](../AGENTS.md), [PRD](../PRD.md), [PROJECT_STATE.md](PROJECT_STATE.md), perfis em `docs/agents/` e instruções locais aplicáveis.
- Executar testes/checks pertinentes antes de enviar alterações e registrar comandos, resultados, limitações e pendências reais.
- Revisar e publicar via Pull Request para `main`; usar Draft quando houver trabalho ou validação pendente.
- Nunca fazer merge ou deploy sem autorização explícita. Commit, push, PR ou testes aprovados não concedem essa autorização.
- Nunca versionar credenciais, tokens ou `.env` reais. `.env.example` contém somente placeholders. Não publicar logs/diffs com segredos ou dados pessoais.

## Ambientes e autenticação

- Projeto na VPS: `/home/hermes/projects/trevo4folhas`.
- GitHub: `IgorVoidbot/trevo4folhas`.
- Na VPS, operações do GitHub CLI usam **sempre** `/home/hermes/.local/bin/trevo-gh`. O wrapper é local, não está versionado e não deve ser copiado com suas credenciais.
- No computador local, usar autenticação própria do usuário e o CLI disponível ali; não copiar tokens, `.env`, banco Kanban, sessões ou configurações privadas da VPS. Se necessário, preparar um wrapper local equivalente sem versionar credenciais.
- Git (fetch/push) e GitHub CLI (API/PR) são verificações distintas. O alias SSH `github-trevo` da VPS não é configuração portátil; não copiar esse remote cegamente para outro host.

Escolher o comando no ambiente correspondente; os exemplos a seguir usam a variável `GH`:

```bash
# Somente na VPS:
GH=/home/hermes/.local/bin/trevo-gh
# Somente no computador local, se o wrapper da VPS não existir:
# GH=gh

"$GH" api user --jq .login
"$GH" api repos/IgorVoidbot/trevo4folhas --jq .full_name
```

Essas consultas não exibem tokens. Não usar `gh auth token`, `--show-token`, depuração HTTP, `set -x`, leitura de arquivos de segredos ou URLs com credenciais.

## 1. Preflight antes de retomar

Em um clone existente, conferir primeiro o caminho e o trabalho local:

```bash
pwd
git status --short --branch
git diff --stat
git diff --cached --stat
git branch --show-current
git log -5 --oneline --decorate
```

Se houver alterações staged, não rastreadas ou modificadas, **parar antes de trocar de branch ou integrar commits**. Relatar quais arquivos precisam ser preservados e combinar checkpoint/isolamento. Não fazer commit de trabalho alheio nem incluir arquivos ignorados. Um working tree limpo não prova que dois clones possuem os mesmos commits.

Após a conferência, buscar referências sem alterar os arquivos locais:

```bash
git fetch --no-tags origin
"$GH" pr list --repo IgorVoidbot/trevo4folhas --state open \
  --json number,title,headRefName,baseRefName,isDraft,url
```

Ler o estado comprovado e, quando disponível naquele ambiente, o cartão do quadro `trevo4folhas` com seus comentários/runs/evidências. O Kanban não é sincronizado pelo Git. Não disparar agentes ou liberar dependências apenas para consultar o estado.

## 2. Obter o projeto no computador local

Se ainda não houver clone, usar Git/SSH já autenticado no próprio computador:

```bash
git clone git@github.com:IgorVoidbot/trevo4folhas.git trevo4folhas
cd trevo4folhas
```

Essa URL não contém credenciais. Caso o acesso SSH local não esteja preparado, resolver a autenticação local antes de continuar; não alterar a configuração da VPS nem inserir token no remote. Em clone já existente, não executar outro clone sobre arquivos locais.

## 3. Continuar uma branch publicada

Para continuar este primeiro trabalho documental, usar `hermes/docs-development-workflow`. Para outra tarefa, substituir `BRANCH` pela branch exata acordada no handoff, sem reutilizar a branch documental para código.

Somente com working tree limpo, preflight concluído e posse de escrita transferida:

```bash
BRANCH=hermes/docs-development-workflow
git fetch --no-tags origin

# Se a branch já existir localmente:
git switch "$BRANCH"
# Se ainda não existir, usar esta alternativa em vez do switch acima:
# git switch --create "$BRANCH" --track "origin/$BRANCH"

git rev-list --left-right --count "HEAD...origin/$BRANCH"
git log -5 --oneline --decorate
```

Interpretar a contagem `local-only remote-only`:

- `0 0`: referências alinhadas.
- Somente commits remotos: solicitar/confirmar autorização de sincronização fast-forward antes de atualizar a branch local; não trabalhar sobre uma base atrasada.
- Somente commits locais: inspecionar antes de publicar; não sobrescrever o remoto.
- Commits dos dois lados: parar, relatar divergência e combinar reconciliação. Nunca usar push forçado ou rebase automático.

Quando a atualização fast-forward tiver sido autorizada e o working tree continuar limpo, pode-se usar:

```bash
git pull --ff-only origin "$BRANCH"
```

Isso não aprova merge de PR nem integração de uma tarefa à `main`. Se houver conflito/divergência ou recusa do fast-forward, parar; não trocar para uma operação mais permissiva.

Antes de uma nova tarefa, conferir `origin/main` após fetch e criar a branch própria sem editar a `main`. Não executar este exemplo para T4F-008 enquanto os bloqueios em [PROJECT_STATE.md](PROJECT_STATE.md) persistirem:

```bash
git switch --create hermes/t4f-008-catalog origin/main
```

Se houver dependência de outra branch/PR ainda não integrado, combinar a base explicitamente; não fazer merge, cherry-pick ou recuperação de fontes por suposição.

## 4. Implementar, validar e publicar

1. Confirmar tarefa, responsável, branch/base, arquivos permitidos e critérios de aceite. Aplicar os perfis de Backend, Frontend, DBA, DevOps e Segurança conforme a mudança.
2. Implementar uma fatia verificável por vez. Para código, manter teste RED → GREEN e as regressões existentes; PostgreSQL 16 real nos testes que o exigem. Não trocar integração por H2 nem desativar controles para obter verde.
3. Conferir manifests reais e pré-requisitos antes de rodar comandos. Checks da aplicação disponíveis: `npm ci`, `npm run lint`, `npm run format:check`, `npm run css:build` e `./mvnw spotless:check verify`. Build CSS pode alterar o arquivo gerado: revisar o diff correspondente. Testcontainers depende do ambiente Docker isolado aprovado, sem usar dados da aplicação.
4. Para documentação isolada: revisar links relativos, exemplos shell, fatos versus evidências e `git diff --check`. Não executar build/deploy por rotina nem apresentar verificações documentais como testes funcionais.
5. Inspecionar o diff com cuidado para não exibir segredos. Adicionar somente arquivos revisados e pertencentes à tarefa; nunca incluir `.env`, credenciais, dumps ou WIP alheio.

Exemplo exclusivo para o PR documental autorizado:

```bash
git diff --check
git add -- AGENTS.md docs/PROJECT_STATE.md docs/DEVELOPMENT_WORKFLOW.md
git diff --cached --stat
git diff --cached --check
git commit -m "docs: establish collaborative development workflow"
git push --set-upstream origin \
  hermes/docs-development-workflow:hermes/docs-development-workflow
```

Na VPS, criar o Draft PR com base/head explícitos, depois de conferir que não existe outro PR para a mesma branch:

```bash
/home/hermes/.local/bin/trevo-gh pr create \
  --repo IgorVoidbot/trevo4folhas \
  --base main \
  --head hermes/docs-development-workflow \
  --draft \
  --title 'docs: establish collaborative development workflow' \
  --body 'Documentation-only workflow and verified project state. No production code, merge or deployment. See the documented validation and recovery blockers.'
```

A autorização desta entrega cobre somente a publicação da branch documental. Os exemplos não autorizam novos commits/push de outras tarefas. Nunca usar push genérico de todas as branches, `--mirror`, `--force` ou integração automática à `main`.

## 5. Handoff entre VPS e Hermes local

Antes de transferir a escrita, registrar no PR ou em atualização de `PROJECT_STATE.md`:

- Tarefa e estado real: concluída, parcial ou bloqueada; arquivos alterados e escopo que falta.
- Branch e SHA exato do último checkpoint publicado, link do PR e base utilizada.
- Comandos/testes executados, resultado, ambiente e local de evidências portáteis sem segredos. Identificar resultados históricos e testes não executados.
- Pendências, dependências, próxima ação concreta e responsável seguinte.
- Arquivos não publicados/ignorados que o Git não transporta, sem copiar credenciais ou dados reais.

O recebedor repete o preflight, busca a branch, confere o SHA e lê o diff antes de continuar. O responsável anterior para de escrever. Se não houver checkpoint publicável autorizado, combinar outro mecanismo seguro de preservação; não presumir que alterações não commitadas ou anexos Kanban chegaram pelo GitHub.

## 6. Fechamento e revisão

Após push e criação do PR, reler no GitHub: branch, SHA remoto, arquivos alterados, `baseRefName`, `headRefName`, `isDraft` e URL. Informar checks efetivamente executados; ausência de CI configurada não é CI aprovada.

Manter o PR aberto para revisão. Não executar merge, deploy, ativação de auto-merge ou remoção de branches por iniciativa própria. A próxima tarefa fica em branch distinta e só avança se a base e o ambiente estiverem comprovados.
