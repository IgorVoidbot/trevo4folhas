# Consolidação das branches — 31/10/2026

Objetivo autorizado pelo usuário: organizar as branches e preservar qualquer conteúdo importante dos checkpoints anteriores. A reconciliação foi preparada na branch de tarefa `chore/consolidate-project-history`, em worktree isolada, preservando os arquivos locais de IDE e sem implementar na `main`.

## Referências de origem

| Referência | Commit antes da consolidação | Conteúdo preservado |
| --- | --- | --- |
| `origin/main` | `2db8bde` | Fundação e PR #1 de documentação; inclui `638d87c` e `e321014` |
| `origin/hermes/docs-development-workflow` | `e321014` | Fluxo VPS/local, inspeção histórica da VPS e regras de colaboração no AGENTS |
| `origin/wip/t4f-008-cloud-handoff` | `8f1eecc` | Checkpoint de autenticação e catálogo incompleto; ancestral da implementação atual |
| `origin/feat/sprint-1-catalog` | `68288f9` | Catálogo administrativo/público, cupom, adapter provisório, testes e auditoria local |

As verificações de ancestralidade confirmaram que a documentação já estava contida na `main` e o WIP já estava contido na branch funcional. A integração preserva as duas linhas de história com merge, sem squash, rebase ou descarte de commits. O conflito em `AGENTS.md` foi reconciliado preservando o inventário atualizado e as regras de colaboração da `main`.

`docs/DEVELOPMENT_WORKFLOW.md` e `docs/PROJECT_STATE.md` foram mantidos. Acrescentou-se contexto temporal para evitar que a inspeção antiga da VPS, que ainda dizia faltar autenticação, fosse interpretada como estado atual. As evidências de execução da VPS continuam históricas; testes locais não foram apresentados como teste da VPS. A auditoria de 31/10 e os checklists continuam sendo o acompanhamento da implementação local.

## Preservação e conferência

- Bundle completo de segurança criado e verificado antes da integração: `/tmp/trevo4folhas-before-branch-organization.bundle`, com permissão 600. É cópia local temporária, não política de backup permanente.
- As alterações locais de `.idea/misc.xml` e a exclusão de `.idea/modules.xml` foram registradas separadamente e preservadas no checkout original; não foram commitadas, guardadas em stash ou descartadas.
- Nenhum arquivo `.env`, senha, estado de quota, banco, dependência ou artefato gerado de teste foi copiado para a nova worktree ou incluído na consolidação.
- A diferença contra `68288f9` restringe-se a documentação/instruções. A árvore de implementação, migrations, build e testes deve permanecer idêntica.
- Validação desta reconciliação: ausência de marcadores de conflito, links locais, diff, ancestralidade, integridade do bundle e igualdade das árvores de código. A suíte funcional foi aprovada na auditoria anterior (151 testes Java, quatro do cupom e navegador); não foi reexecutada apenas por esta reconciliação documental.

## Publicação e limpeza

Publicar a consolidação por PR para `main`. Antes de encerrar branches antigas, conferir no remoto que a `main` consolidada contém integralmente `68288f9`, `8f1eecc` e `e321014`, usando `git merge-base --is-ancestor`. A remoção de uma referência de branch não deve eliminar seu histórico, que permanecerá alcançável pela `main` e pelos PRs.

Manter `feat/sprint-1-catalog` como referência funcional identificável pelo usuário. As duas branches históricas podem ser removidas somente depois da conferência de preservação. O resultado efetivo de PR/merge/limpeza será informado após a execução; este registro não declara operação remota concluída por antecipação. Não há deploy nesta tarefa. Novas implementações devem usar outra branch de tarefa baseada na `origin/main` conferida.
