# T4F-007 — autenticação e controles administrativos

Estado recuperado pelo coordenador em 28/10/2026. Quadro `trevo4folhas`, cartão `t_9452e15b`.

## Ponto de parada e recuperação

A T4F-006 foi aprovada e publicada no checkpoint `ad58667`. O run 16 da T4F-007 terminou por timeout com código parcial ainda não compilado. O usuário autorizou analisar, corrigir e continuar o desenvolvimento local em 28/10/2026.

O coordenador preservou a baseline e os hashes nos anexos 34/35 do cartão antes de retomar o Backend. O run 17 corrigiu e testou a implementação, mas também atingiu o limite de 1.800 segundos durante a preparação do relatório/handoff. O evento permaneceu `timed_out`, com `elapsed 1808s > limit 1800s`: não foi convertido em uma execução de worker bem-sucedida. A conclusão funcional não foi inferida do status; os logs, XMLs, fontes e uma reexecução independente foram conferidos separadamente. Não houve aumento de prazo, de retries, de privilégios ou de orçamento.

## Correções e entrega

- `AdminLoginRateLimitFilter`: substituída constante HTTP inexistente na API Servlet por `HttpStatus.TOO_MANY_REQUESTS.value()`. O `Retry-After` passou a arredondar a duração restante para cima; regressões com relógio controlado verificam duração e arredondamento. A asserção HTTP deixou de presumir que todas as tentativas BCrypt consomem zero tempo, preservando `429`, intervalo válido e isolamento entre clientes.
- `SecurityConfig`: retirada chamada inexistente a `CsrfConfigurer.accessDeniedHandler`. CSRF permanece habilitado; o handler configurado em `exceptionHandling` trata as recusas. A allowlist passou a incluir a leitura pública de seleções por mercado, sem liberar mutações administrativas.
- `application.yml`: configurações de sessão movidas de `spring.servlet.session` para `server.servlet.session`. A sonda independente reproduziu Secure nulo na baseline e passou após a correção. O perfil base vincula timeout de 30 minutos, HttpOnly, SameSite=Lax e Secure; o perfil local relaxa somente Secure.
- Autenticação administrativa por sessão e BCrypt, operador inativo bloqueado, falhas genéricas pt-BR, APIs privadas com resposta JSON, logout POST com CSRF, rotação de sessão e limite de login configurável.
- Bootstrap controlado por ambiente, sem credencial padrão nem reset de conta existente; `AdminBootstrapService.bootstrap()` recebeu a fronteira `@Transactional`, com regressão permanente correspondente.
- Templates de login, início administrativo e erro em pt-BR, com escape de saída e formulários protegidos. Os checks de formatação foram corrigidos nos arquivos deste escopo.

Nenhuma entidade de apostador, carteira, confirmação de bilhete, controller de catálogo ou integração externa foi criada nesta etapa. Os serviços de catálogo/curadoria pertencem à T4F-008; emissão e operação financeira permanecem nas etapas posteriores e nos gates próprios.

## Verificações reais

### Backend, run 17

- Checkpoint de compilação: `target/verification-logs/t4f007-01-test-compile.log`, exit 0 depois dos dois erros de compilação reproduzidos pelo coordenador.
- Binding RED/GREEN: logs `t4f007-02-binding-probe-red.log` e `t4f007-03-binding-probe-green.log`.
- Suites focadas, antes da última regressão de fronteira transacional: 32 testes sem falhas/erros/skips, em `t4f007-04-focused-suites.log`.
- Build final: `./mvnw -B -Dmaven.repo.local=/home/trevo/.m2/repository spotless:check verify`, log `target/verification-logs/t4f007-08-final-verify.log` e marcador `FINAL_VERIFY_EXIT=0`: **105 testes, zero falhas, erros ou skips**; duração registrada 01:07 min.
- `npm run lint`, `npm run format:check` e `npm run css:build`: exit 0 nos logs finais `t4f007-06-*`. Falhas iniciais de formatação foram preservadas como histórico, não apresentadas como resultados finais.

Ao agregar XMLs no workspace original, havia um relatório histórico de quatro casos `ReviewAcceptanceTest` da T4F-006. Ele não aparece na lista de suites do build final e foi excluído da contagem desta execução. Não somar todos os XMLs antigos de `target/` sem reconciliar com o log da tentativa.

### Reexecução independente do coordenador

Executada após o encerramento do run 17, com os fontes originais somente leitura e cópia descartável, imagem fixada e daemon privado rootless já aprovado. Testcontainers utilizou PostgreSQL 16 real, Ryuk habilitado e não privilegiado; não utilizou app/db para testes.

Saída bruta do controlador:

- `npm-ci_EXIT=0`
- `assets-lint_EXIT=0`
- `assets-format_EXIT=0`
- `assets-css_EXIT=0`
- `full-verify_EXIT=0`
- `independent-probes_EXIT=0`

Evidências em `target/review-007-final/`:

- `full-verify.log` e `project-surefire-reports/`: **105 testes, zero falhas, erros ou skips**, agregados por código em uma cópia sem relatórios antigos.
- `independent-probes.log`: **11 casos separados, zero falhas, erros ou skips**. Uma sonda original de binding e dez casos de um harness de segurança verificam allowlist, CSRF público/admin, autorização e impossibilidade de burlar o limite com headers encaminhados forjados.
- Os controllers desse harness são stubs explicitamente rotulados: comprovam filtros e contratos de segurança, não serviços de catálogo ou emissão. Os onze casos se sobrepõem a cenários do conjunto principal; não são declarados 116 testes únicos.
- `tested-source.tar.gz` e `verified-results.json`: 73 arquivos de fontes/configuração/templates/manifests foram comparados por hash com o workspace; todos coincidiram após a execução.

## Preservações e limitações

- Oito migrations V1–V8 conferidas por SHA-256, sem alterações em relação à baseline preservada.
- Sem commit, staging, push, merge, deploy, leitura de segredos ou chamadas autenticadas à API-Football. Mudanças locais de IDE permaneceram fora do escopo.
- O sandbox ocioso `1f10aed96acb` do run 17 foi identificado por criação, mounts e processos (`docker-init`/`sleep`), parado e relido como `exited`. Nenhum container de teste permaneceu após a reexecução independente; app e db existentes foram relidos `healthy`.
- A imagem de ferramentas permanece com a exceção local de UID 0 dentro do user namespace rootless já aprovada. Não se alega UID interno não-zero, isolamento absoluto ou alteração da política da imagem final da aplicação.
- Há aviso de API Java depreciada; isso não é falha de compilação nem autorização para migrar dependências. Sem benchmark p95, aceite visual, ensaio de inatividade aguardando 30 minutos ou homologação de produção nesta entrega. Expiração de sessão é simulada nos testes de acesso; binding/flags e rotação são verificados separadamente, inclusive por HTTP local real.
- T4F-028/029 continuam bloqueadas por seus requisitos próprios. O timeout é problema da tentativa/fechamento, não evidência de reprovação dos testes nem autorização para reduzir os critérios.

## Continuidade

A implementação e as verificações recuperadas estão prontas para revisão pelo perfil QA existente. O coordenador recupera o handoff sem reiniciar desenvolvimento e sem marcar entrega parcial como concluída. A transição efetiva para review e o início do QA precisam ser conferidos nos eventos do cartão.

T4F-008 deve aguardar a aprovação dessa revisão. Em seguida, a cadeia técnica autorizada pode continuar serialmente até T4F-027; o QA independente da fundação T4F-009 continua obrigatório. Uma falha real bloqueia a fila para coordenação; não existe reparo ilimitado nem promessa de notificação automática nesta sessão CLI.

### Roteamento final do QA

O handoff foi recuperado para `review` preservando a proveniência `trevo-backend → trevo-qa`; o run 18 é o registro administrativo sintético desse handoff, não uma nova execução do modelo Backend. Ao verificar o consumo da fila, foi constatado `kanban.review_dispatch=false` na configuração global existente. A passagem para review, por si só, não iniciaria um revisor automático.

Essa configuração global foi preservada para não alterar o comportamento de outros projetos. O coordenador retornou somente este cartão à fila ordinária pelas transições oficiais e o atribuiu ao QA existente. Corpo e estado foram relidos com fase explícita de validação, proibição de editar implementação e critérios originais preservados. Não houve `force`, bypass de dependências, novo perfil ou aumento de limites.

Nesta tentativa ordinária o QA deve concluir somente com PASS de todos os critérios; uma falha exige relatório reproduzível e bloqueio do próprio cartão, não `kanban_request_changes`, que depende de claim da review lane. T4F-008 continua aguardando T4F-007. `ready` significa elegível; somente um evento `spawned` posterior comprova o início efetivo do QA.

Releitura posterior confirmou início efetivo: cartão `running`, perfil `trevo-qa`, run 19, PID 70709 e eventos `claimed`/`spawned` correspondentes. Não se trata apenas de fila elegível. O resultado desse QA ainda não foi emitido neste registro; T4F-008 permanece em espera pela aprovação. `kanban.review_dispatch` foi relido `false`, sem alteração global.

## Fechamento recuperado e próximo incremento

Após esse checkpoint, o run 19 esgotou 60 interações e passou a `gave_up`. O build completo do QA havia aprovado 105 testes; restou uma falha na sonda de logout, que ainda reutilizava o token CSRF pré-login. Sob novo “Pode continuar” do usuário, o coordenador reproduziu a falha e corrigiu somente o harness, passando a obter o token na página autenticada. A rejeição do token antigo foi preservada como controle negativo e a expiração do cookie foi comprovada. Reexecução: cinco sondas, zero falhas/erros/skips. Os 76 arquivos de `src/` ainda coincidiam com o snapshot aprovado no instante do aceite.

Matriz, causa e provas estão em [aceite do QA](t4f-007-qa.md), com evidências duráveis nos anexos 42/43/44 do cartão. O fechamento administrativo foi registrado no run 20, sem novo processo de modelo e sem apagar o `gave_up` original. A releitura do quadro confirmou **T4F-007 `done`**.

A dependência liberou a T4F-008 — catálogo e curadoria HTTP — e seu início efetivo também foi conferido: perfil `trevo-backend`, run 21, PID 109432, estado `running` e evento `spawned` correspondente. Esse início não é entrega ou aceite da T4F-008. T4F-028/029 permanecem `blocked`; publicação, produção, chamadas reais e ampliação de limites continuam fora da autorização.
