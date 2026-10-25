# TREVO4FOLHAS — decisão de desenvolvimento local

Data: 24/10/2026. Quadro: `trevo4folhas`. Gate: T4F-000 / `t_b3264973`.

## Autorização e escolha do coordenador

Após o relato do bloqueio por identidade do processo, o usuário respondeu: “Pode fazer o que achar melhor para o projeto”. Essa resposta delega ao coordenador a escolha entre as alternativas apresentadas e a continuidade do desenvolvimento já aprovado. Não representa aprovação de produção, contratação ou permissões irrestritas.

Decisão do coordenador: aceitar, exclusivamente para os workers de desenvolvimento local, UID 0 dentro do user namespace RootlessKit, mapeado ao UID 1000 do host. Não declarar que o requisito literal de UID interno não-zero foi cumprido: trata-se de exceção explícita ao aceite anterior, tomada sob autorização delegada. A imagem final da aplicação continua obrigada a executar como usuário não-root.

Manter a topologia já exercitada evita redesenhar o acesso de escrita ao workspace e aos caches sem necessidade demonstrada. A aceitação depende das proteções verificadas abaixo, não apenas do nome “rootless”. Não é certificação de segurança nem afirmação de isolamento absoluto.

## Evidências da revalidação

- Os 30 cartões foram relidos individualmente antes da liberação: todos `blocked`, sem runs; 29 dependências sequenciais; workspace persistente correto; contrato `local-only`; `max_retries=1`; goal mode desativado. Cartões de implementação/QA: limite de 1.800 segundos; perfis: 60 turnos.
- Os quatro perfis TREVO continuam autenticados em `opencode-go`, com backend Docker obrigatório, `degraded_mode=fail`, imagem fixada e mounts esperados. Nenhuma credencial foi lida, copiada ou alterada.
- Gateway existente ativo, perfis elegíveis e diagnostics sem apontamentos. Configuração de concorrência global e por perfil: um worker. Não iniciar outro dispatcher.
- Unidade de usuário `trevo4folhas-test-engine.service`: ativa e habilitada; limite agregado de 1 CPU e 2 GiB. Docker informa `rootless`, seccomp e cgroup namespace; driver cgroup `none` continua sem garantir limites individuais por container.
- Novo smoke executado pelas ferramentas reais do perfil `trevo-backend`: sessão `20261024_195105_ee88bd`; resultado bruto da ferramenta terminal, mensagem 117, relido no banco de sessões. Exit code 0. `read_file` também confirmado pelo resultado bruto da mensagem 119.
- CWD `/workspace`; UID map `0 1000 1` e `1 100000 65536`; `NoNewPrivs=1`; `CapEff=0xCB`. Escrita temporária no workspace passou; escrita em `.git` foi recusada. Home/configuração Hermes, Spatial e socket Docker principal permaneceram inacessíveis nos probes.
- Testcontainers iniciou PostgreSQL 16 real e obteve `16|42`; nenhuma porta do banco publicada; fechamento por `try-with-resources` passou. Ryuk desabilitado somente no smoke. Isso não valida JDBC por porta publicada, Ryuk ou a aplicação futura.
- Doze arquivos de PRD, instruções e pesquisa mantiveram os hashes da baseline capturada nesta retomada. `git diff --check` passou antes da liberação. Alterações pré-existentes preservadas.

## Escopo liberado e gates preservados

Liberar T4F-001 a T4F-027 para avançarem automaticamente por dependências, um worker por vez, após concluir o gate administrativo T4F-000. A primeira entrega é sincronizar instruções e contratos com o PRD 1.2; depois vem a fundação executável.

T4F-028 permanece `blocked`: chamadas reais à API-Football exigem credencial injetada com segurança, autorização e orçamento de chamadas explícito. T4F-029 permanece `blocked`: fornecedor, condições comerciais, paridade e produção dependem de decisão humana posterior. Essa separação não invalida testes simulados rotulados nem autoriza apresentar dados simulados como prova de fornecedor real.

Permanecem proibidos: root no host, `--privileged`, socket Docker principal, leitura de segredos, alterações em perfis/projetos Spatial, commits automáticos, push, merge, deploy, contratação, operação financeira e exclusão de dados reais. Não há autorização para ampliar permissões ou gastos adicionais.

## Limitações e interrupção segura

Egress sem allowlist de domínios; Browser/vision desabilitados; reboot não exercitado; limites de recursos somente agregados; aviso SLF4J NOP no harness. Gates visuais precisam de capacidade comprovada antes de concluir e devem bloquear se ela estiver ausente. O teste atual não substitui os QA gates da aplicação.

O caminho de sucesso avança pelo dispatcher já existente. Uma falha real bloqueia a tarefa e exige retomada da coordenação; não foi instalado observador LLM permanente, reparo ilimitado ou canal de notificação para esta sessão CLI. Não alterar documentos de planejamento em paralelo com um worker que os esteja editando.

O estado de execução após a liberação deve ser consultado nos cartões e seus runs; este documento registra a autorização e as evidências anteriores ao início, não a conclusão da aplicação.

## Retomada após o checkpoint T4F-006 — 25/10/2026

Houve uma pausa posterior para concluir/revisar T4F-006 e publicar somente após os testes e o aceite completos. Depois da primeira revisão, o Backend corrigiu os achados R1/R2 no run 13. A reexecução independente do coordenador aprovou 72 testes do build completo e, separadamente, as quatro sondas originais sem alteração, além de lint e formatação de assets. Critérios, evidências e limites estão em [revisão da T4F-006](t4f-006-review.md).

O coordenador perguntou explicitamente se, após aprovar a T4F-006, poderia liberar as próximas etapas; o usuário respondeu “Sim”. Essa autorização satisfaz o consentimento que mantinha o gate de pausa `t_75733c77` fechado e permite retomar T4F-007 a T4F-027 pela cadeia serial existente. Não é autorização de execução simultânea, novos perfis, alterações globais ou expansão de escopo. O estado efetivo e o primeiro spawn devem ser verificados no quadro após a transição.

A exceção anterior de commit/push é restrita ao coordenador para publicar o checkpoint aprovado da T4F-006; não concede commits ou pushes automáticos aos workers nem às próximas etapas. T4F-028/029 continuam `blocked`, com seus requisitos próprios de credencial segura, orçamento e decisão humana. As demais proibições e limitações acima permanecem válidas.
