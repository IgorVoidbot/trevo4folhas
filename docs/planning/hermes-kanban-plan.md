# TREVO4FOLHAS — plano Hermes Kanban v0.1

Data da proposta: 24/10/2026. Estado atual: o usuário delegou ao coordenador a escolha de segurança e a continuidade do desenvolvimento. Foi aceita, somente para workers locais, a exceção de UID 0 dentro do user namespace mapeado ao UID 1000 do host. Novo preflight real passou; T4F-000 pode concluir e T4F-001–027 podem avançar serialmente. T4F-028/029 permanecem bloqueados por credencial/orçamento e decisão humana de produção. Execução corrente: consultar cartões e runs. Evidências: [preflight](infrastructure-preflight.md) e [autorização de desenvolvimento](development-authorization.md).

## Estado verificado e preservação

- PRD.md v1.2 revisado: API-Football provisória; escolha de produção do cliente; paridade multimercado não foi reduzida a 1X2. Requisitos, modelo/ERD, versões/locks, HTTP, UX, aceites e backlog foram inspecionados.
- Branch atual main; HEAD 603ccc7. Alterações anteriores: AGENTS.md e PRD.md modificados e docs/research/ não rastreado. Sem aplicação/manifests executáveis. Nenhum reset, stash, checkout, commit, push ou merge realizado.
- Pendência anterior: sincronizar contratos dos especialistas com PRD 1.2 antes de implementar. Essa é a tarefa 001. Detalhes indexáveis da identidade, política de reagendamento, relógios/frescor e documentação do fornecedor exigem confirmação/testes, não alteração silenciosa de requisitos.
- Antes da execução autorizada, guardar backup e manifesto de hashes dos arquivos existentes, incluindo documentação não rastreada, fora do repositório e sem copiar segredos. Um worktree novo de HEAD perderia PRD/AGENTS não commitados e pesquisa não rastreada; por isso não será usado automaticamente.

## Quadro e workspace

- Nome: TREVO4FOLHAS; slug: trevo4folhas.
- Banco exclusivo: /home/igor/.hermes/kanban/boards/trevo4folhas/kanban.db.
- Workspace persistente: /home/igor/Área de trabalho/Projeto-aposta. Cada cartão fixa workspace_kind=dir e esse caminho absoluto, inclusive QA; não usar scratch para entregas nem o workspace espacial.
- Execução sequencial no mesmo diretório evita merges entre tarefas. A branch permanece main, sem commits automáticos. Isto não protege contra edições humanas simultâneas: mudança inesperada de baseline exige parar e reconciliar sem apagar trabalho.
- O ponteiro de quadro atual do CLI permanece spatial-os para não modificar a configuração de outro trabalho. Todas as operações TREVO usam --board trevo4folhas explícito. Nenhum cartão/volume/configuração spatial-* será alterado.
- Antes da liberação, os 30 cartões foram relidos como blocked e sem runs. A autorização delegada permite passar T4F-001–027 para espera por dependências e concluir T4F-000; T4F-028/029 ficam blocked. A allowlist carregada preserva spatial-builder/spatial-qa e os quatro perfis TREVO; gateway ativo e diagnostics sem apontamentos. Não presumir que esta fotografia anterior à liberação seja o estado corrente.

## Equipe configurada — validação de acesso concluída

Jarvis/default coordena e aprova evidências no plano de controle; modelo configurado gpt-6.1-sol, provider openai-codex. Não criar mais um arquiteto: contratos ficam com Backend/DBA e revisão QA. Os cinco documentos de personas do repositório continuam obrigatórios; não precisam corresponder a cinco processos.

Os quatro perfis novos foram criados limpos, pois os especialistas anteriores eram spatial-architect, spatial-builder e spatial-qa, com SOUL e volumes próprios de outro projeto. Não houve reutilização ou clone desses perfis, nem cópia de chaves, tokens de mensageria, memórias ou cron.

Modelos abaixo foram configurados nos perfis novos. `auth status opencode-go` retornou logged in nos quatro perfis, e one-shots independentes completaram inferência. Isso não é comparação de preços nem garantia de saldo. Cada perfil e volume permanece separado; não houve fallback pago automático.

### trevo-devops

Papel: Fundação Maven/Node, Docker, ferramentas e operação local.
Modelo proposto: mimo-v2.6-pro; provider: opencode-go.
Ferramentas habilitadas: terminal, file, skills, web e ciclo Kanban do próprio cartão.
Volumes de cache exclusivos: trevo4folhas_devops_m2, trevo4folhas_devops_npm.
Conteúdo obrigatório do SOUL.md: Executar somente infraestrutura local aprovada; aplicar DevOps e Segurança; fixar versões após verificar manutenção; provar builds e restauração sem deploy. Não obter socket do daemon Docker principal nem elevar privilégios.

### trevo-backend

Papel: Java/Spring, PostgreSQL/DBA, domínio e integrações.
Modelo proposto: mimo-v2.6-pro; provider: opencode-go.
Ferramentas habilitadas: terminal, file, skills, web e ciclo Kanban do próprio cartão.
Volumes de cache exclusivos: trevo4folhas_backend_m2.
Conteúdo obrigatório do SOUL.md: Aplicar Backend, DBA e Segurança; proteger BigDecimal, snapshots, idempotência e locks. Preservar todos os requisitos de mercados; implementar SportsDataProvider e adapter provisório; não confundir mocks com integração autenticada.

### trevo-frontend

Papel: HTML/Thymeleaf, Tailwind e Vanilla JS, UX pt-BR.
Modelo proposto: mimo-v2.6-pro; provider: opencode-go.
Ferramentas habilitadas: terminal, file, skills, web e ciclo Kanban do próprio cartão. Browser/vision não foram habilitados; este perfil não recebe o socket Docker.
Volumes de cache exclusivos: trevo4folhas_frontend_npm.
Conteúdo obrigatório do SOUL.md: Aplicar Frontend e Segurança; respeitar contratos do backend, responsividade e acessibilidade. Cálculos no browser são prévia. Não criar carteira/login de apostador, consulta pública de bilhetes ou confirmação automática.

### trevo-qa

Papel: QA independente, integração, segurança e validação visual.
Modelo proposto: minimax-m3; provider: opencode-go.
Ferramentas habilitadas: terminal, file, skills, web e ciclo Kanban do próprio cartão. Browser/vision não foram habilitados; tarefas visuais precisam de validação/capacidade própria.
Volumes de cache exclusivos: trevo4folhas_qa_m2, trevo4folhas_qa_npm.
Conteúdo obrigatório do SOUL.md: Verificar por execução independente; produzir matriz PASS/FAIL/BLOCKED e evidências. Pode adicionar testes/fixtures e relatórios, não corrigir implementação nem enfraquecer testes. Falha no gate bloqueia a própria tarefa; não aprovar por relato do implementador.

Todos os SOUL.md devem ainda limitar atuação ao cartão/quadro/workspace, proibir comandos Git/publicação não autorizados e exigir provas antes de completar. Perfis são configuração de identidade, não barreira de segurança; ferramentas e mounts precisam ser exercitados no gate 000.

## Infraestrutura e permissões aprovadas — contrato e verificações

1. Os quatro perfis foram criados via CLI oficial e configurados com SOUL.md próprios. Nenhuma alteração nos perfis spatial-*. `opencode-go` está autenticado nos quatro perfis; a chave fica no armazenamento Hermes e não é montada no sandbox.
2. Imagem de ferramentas exclusiva `trevo4folhas-worker` fixada nos perfis por digest, com Java 17/Maven, Node e Docker CLI. Maven Wrapper/Tailwind pertencem às futuras tarefas da aplicação. Browser/vision continuam desabilitados, sem claim de automação visual. O runtime final da aplicação não recebe ferramentas de desenvolvimento.
3. `terminal.backend=docker`, `docker_run_as_host_user=false`, `docker_mount_cwd_to_workspace=false`; workspace do host montado em `/workspace` e `.git` sobreposto read-only em `/workspace/.git`. O processo aparece como UID 0 dentro do user namespace RootlessKit, mapeado ao UID 1000 do host, com `NoNewPrivs=1` e capacidades limitadas. A exceção ao aceite literal de UID interno não-zero foi aceita pelo coordenador sob decisão delegada, exclusivamente para workers locais; não equivale a root no host nem altera o requisito não-root da imagem final da aplicação. Não montar home inteiro, `.ssh`, perfis Hermes, credenciais, spatial-os ou socket principal `/var/run/docker.sock`.
4. Parâmetros pedidos ao terminal: CPU=1 e memória=2048 MiB. Rootless Docker reporta `Cgroup Driver: none`, então esses limites por container não são garantidos. O serviço systemd exclusivo fornece limite agregado verificado de 1 CPU/2 GiB. `kanban.max_in_progress=1` limita a um worker no host, entre quadros; `max_in_progress_per_profile=1`. Não manter containers Hermes ociosos; seis caches são exclusivos TREVO. Volumes de dados da aplicação, quota do provedor e PostgreSQL serão criados em tarefas futuras, não agora.
5. Testcontainers/Compose: daemon Docker rootless exclusivo, com socket privado. Somente DevOps/Backend/QA recebem o socket; Frontend não. Rootless usa namespace user/network/mount/PID privado e root isolado com `pivot_root`; o daemon não vê o home inteiro. Após autorização específica na recuperação de T4F-005, os três perfis com socket usam `--network=host` do daemon privado (não a rede do computador), `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`, Ryuk habilitado e privilégio de Ryuk explicitamente falso. O daemon usa bind padrão `127.0.0.1`; a mudança foi revalidada com Ryuk/PostgreSQL 16/JDBC reais e volumes preservados. Sucesso de transporte não aprova automaticamente reinício ou migrations: a revisão final do run 9 conferiu separadamente `spotless:check verify` completo (exit 0, 48 testes aprovados), reinício real, hashes V1–V8 preservados e cleanup; ver [recuperação e evidências](testcontainers-network-recovery.md). Não há `--privileged`, usuário novo ou socket principal. O serviço user-level está persistente/enabled e com `Linger=yes`; reboot ainda não foi testado. Não substituir Testcontainers/PostgreSQL 16 por H2/mocks.
6. Egress de rede do sandbox está habilitado para dependências/documentação e não tem allowlist de domínios; registrar isso como limitação, não como filtragem. Browser/vision estão desabilitados. Terminal/file foram exercitados; não há GitHub token/SSH montados. `local-only` não impede rede por si só.
7. Chave API-Football fica fora do repositório, perfis versionados, logs, browser e contexto do modelo. Mocks não recebem chave real. Etapa autenticada 028 exige injeção segura somente no processo necessário, orçamento e autorização; não criar conta, comprar plano ou procurar segredos existentes.
8. A allowlist acrescenta trevo-devops, trevo-backend, trevo-frontend e trevo-qa, preservando spatial-builder/spatial-qa. `kanban.max_in_progress=1` é global entre quadros (confirmado na implementação); `max_in_progress_per_profile=1`. A chave `max_spawn` não é reconhecida pelo schema CLI e foi removida. O gateway foi reiniciado/revalidado com a cadeia ainda blocked. Não alterar `default_workdir` global (continua apontando para Spatial).
9. Trabalhadores não recebem delegation, cron, gestão de conexões, GitHub/publicação ou acesso de administração global. Toolsets serão mínimos por papel; ciclo Kanban do próprio cartão é injetado pelo dispatcher. QA pode adicionar testes/fixtures/relatórios, não corrigir implementação. Esta separação de funções é também regra de execução a revisar, não promessa de ACL perfeita.
10. Permissões concedidas pelo plano após aprovação: editar código/testes/docs TREVO previstos no cartão, criar os volumes/imagem aprovados, executar comandos locais e testes com dados descartáveis e registrar evidências. Não concede push, deploy, merge, commit automático, operação financeira, contratação, exclusão de dados, credenciais pessoais ou mudanças fora do escopo.

## Controle de créditos, execução e coordenação

- Todos os cartões de implementação/QA definem agent.max_turns=60 e limite de 1.800 segundos; sem goal loops, subagentes ou criação automática irrestrita de tarefas.
- Cada cartão será criado com max_retries=1, cujo CLI define bloqueio na primeira falha ordinária, não uma tentativa adicional. Falhas de infraestrutura podem ter semântica de cooldown diferente; o preflight precisa validar esse caminho e bloquear o cartão ao primeiro erro estrutural. Não prometer teto monetário sem tarifação/limite financeiro do provedor.
- As verificações técnicas foram revalidadas; a decisão delegada resolveu o bloqueio de UID por exceção explícita e local. Atualizar os corpos com a autorização vigente, manter 000 fechado enquanto 001–027 passam a espera por dependências e reler toda a cadeia antes de concluir 000. Manter 028/029 blocked. Não rodar segundo daemon, cron de polling ou worker solto concorrente.
- Os QA gates 009, 014, 024, 025 e 027 só completam com evidência. Falha bloqueia a própria tarefa e impede o próximo worker. Não marcar um gate parcial done.
- Jarvis acompanha eventos/evidências para resolver roteamento e decompor correções quando retomado. Não há observador LLM permanente nem mecanismo de reparo ilimitado instalado. Caminho feliz avança automaticamente por dependências; um bloqueio real para, preserva dados e exige coordenação/decisão em vez de gastar créditos em loop. Não é necessário o usuário executar cada tarefa.
- Sessão CLI não tem entrega automática de notificações. O gateway está ativo, mas sem plataforma de mensagens habilitada nesta inspeção. Resultados ficam no Kanban/logs; notificação externa só com canal autorizado. Não prometer mensagens nesta sessão quando ela estiver fechada.
- 028 (credencial/amostra real) e 029 (decisão de produção) são gates posteriores à entrega técnica 027. Falta de chave não bloqueia a implementação e testes simulados anteriores. Produção continua indisponível sem cobertura/licença/frescor/fornecedor aprovados.

## Regras comuns dos cartões

Leia primeiro kanban_show do próprio cartão, PRD.md, AGENTS.md raiz, personas docs/agents e instruções locais aplicáveis. Trabalhe apenas no workspace e escopo deste cartão; preserve alterações pré-existentes. API-Football é provisória para desenvolvimento; não remover mercados ausentes da matriz nem alegar paridade. Sem push, deploy, merge, commit, reset/clean/rebase/checkout, operações destrutivas, contratação, leitura/registro de segredos ou mudanças de perfis/configuração global. Sem delegate_task/subworkers, cron, autoexpansão de escopo ou loops de correção. Não modificar outro projeto. Testes simulados devem ser rotulados; Testcontainers exige PostgreSQL 16 real. Não executar chamadas reais sem autorização/credencial/orçamento da etapa 028. Antes de concluir, executar verificações aplicáveis e registrar comandos/resultados/arquivos e pendências no cartão. Um gate incompleto/falho deve bloquear a própria tarefa, não completar para liberar sucessor. Em tarefa de implementação, escrever testes e executar o escopo; QA independente não altera implementação. Limite inicial por tentativa: 30 min, agent.max_turns proposto 60, max-retries=1 (bloqueio na primeira falha ordinária), goal desativado. Não insistir diante de falha estrutural de acesso/ambiente; registrar evidência e bloquear.

## Plano serial com critérios de aceite

### T4F-000 — Aprovar e verificar infraestrutura exclusiva

Responsável proposto: default. Dependência: nenhuma. Estado inicial: blocked.

- Aprovação explícita do usuário para o plano e permissões foi recebida; registrar esse fato e não voltar a solicitar a mesma aprovação. Cartão administrativo, não destinado a worker.
- Antes de liberar qualquer cartão, criar os quatro perfis limpos sem clonar spatial-*, configurar SOUL/modelos/ferramentas/mounts e verificar credenciais do provedor sem revelar segredos.
- Comprovar workspace host montado em `/workspace`, `.git` somente leitura, ausência de root no host, limite agregado efetivo, ausência de perfis spatial/home/socket principal e isolamento de terminal/read_file. Registrar separadamente UID 0 dentro do namespace e a exceção local aceita sob decisão delegada; não afirmar cumprimento de UID interno não-zero. Browser/vision ficam fora do escopo validado.
- Provar runtime de PostgreSQL 16/Testcontainers usando daemon rootless exclusivo do projeto sem privilégios ou socket principal; se impossível, bloquear e pedir autorização alternativa, nunca substituir por mock/H2.
- Conferir allowlist de dispatch (preservando os dois spatial existentes), limite global de um worker, max-retries=1, goal desativado e zero execução antes do consentimento; backup e hashes da baseline fora do repo.
- Manter todos os sucessores bloqueados até o preflight passar; depois liberar em ordem de dependência. Nenhum push/deploy/merge/commit automático.

### T4F-001 — Sincronizar instruções e contratos com PRD 1.2

Responsável proposto: trevo-backend. Dependência: T4F-000. Estado inicial: blocked.

- Ler PRD, levantamento e cinco personas obrigatórias; atualizar instruções conflitantes em AGENTS e docs/agents sem apagar alterações pré-existentes.
- Alinhar seis entidades, mercados/seleções, snapshots e versões entre schema/API/fluxos; registrar decisões de precisão, identidade canônica, reagendamento e limites de desenvolvimento sem reduzir mercados.
- Verificar proveniência das referências técnicas do PRD em fontes oficiais; marcar o que não foi recuperado. Documentar política de quota e frescor experimental explícita antes de ligar sync.
- Entregar diff documental, links locais válidos e checklist de contratos consistente; implementação ainda não iniciada.

### T4F-002 — Criar fundação Java e build verificável

Responsável proposto: trevo-devops. Dependência: T4F-001. Estado inicial: blocked.

- Criar pom/Maven Wrapper, Java 17 baseline e versão mantida Boot 3 compatível, dependências previstas e plugins de qualidade/testes; não migrar para Boot 4 sem autorização.
- Adicionar TrevoApplication, Clock injetável, configuração por ambiente validada, testes unitários iniciais e nome trevo4folhas.jar.
- Provar Wrapper, compilação e teste inicial com saída real; registrar exceções transitórias sem ocultar falha nem declarar cobertura final.

### T4F-003 — Preparar assets e verificações frontend

Responsável proposto: trevo-frontend. Dependência: T4F-002. Estado inicial: blocked.

- Criar package.json/lockfile e Tailwind compilado, ESLint e Prettier conforme PRD; sem SPA/Play CDN em produção.
- Disponibilizar css:build/css:watch/lint/format:check e template base pt-BR com CSRF; CSS gerado não editado manualmente.
- Executar npm ci, lint, format:check e css:build; incluir CSS no empacotamento sem exigir Node no runtime Java.

### T4F-004 — Preparar PostgreSQL e execução Docker local

Responsável proposto: trevo-devops. Dependência: T4F-003. Estado inicial: blocked.

- Criar docker-compose.yml canônico app/db com PostgreSQL 16, volume exclusivo trevo4folhas_postgres_data, health e UTC; backend usa hostname db.
- Criar imagem Java multi-stage reproduzível com assets, usuário não root, .gitignore/.dockerignore e .env.example só placeholders; segredos reais fora do repo.
- Provar compose config sem imprimir segredos, imagem/health e persistência em reinício no ambiente de teste exclusivo; não remover volumes existentes.

### T4F-005 — Criar migrations multimercado e invariantes

Responsável proposto: trevo-backend. Dependência: T4F-004. Estado inicial: blocked.

- Implementar seis tabelas do PRD com Flyway separado, auditoria/triggers UTC, ticket_code/match_date e FKs RESTRICT.
- Validar identidades externas compostas com ausência canônica, limites indexáveis, numeric, versões, snapshots e correspondência seleção→partida; impedir movimentação entre pais.
- Testar migrations em PostgreSQL 16 via Testcontainers, constraints/índices/timestamps por SQL e reinício; nunca usar H2 como substituto.

### T4F-006 — Mapear entidades e persistência do catálogo

Responsável proposto: trevo-backend. Dependência: T4F-005. Estado inicial: blocked.

- Criar entidades BaseEntity, repositories/DTOs sem expor JPA, Match→Market→MarketSelection e Ticket/TicketItem conforme schema.
- Mapear @Version, BigDecimal e auditoria pelo banco, separar metadados operacionais de mudanças comerciais.
- Provar round-trip e identidade sem duplicação, uma seleção por partida e snapshots imutáveis; testes de integração reais no banco de teste.

### T4F-007 — Implementar autenticação e controles administrativos

Responsável proposto: trevo-backend. Dependência: T4F-006. Estado inicial: blocked.

- Criar login admin por sessão/BCrypt, bootstrap controlado sem senha padrão e operador inativo bloqueado.
- Proteger /admin e /api/admin, CSRF também público, cookies por perfil, logout POST e rate limit de login; erros pt-BR.
- Testar anônimo, sessão expirada, CSRF inválido/válido, salt e rate limit; nenhuma credencial ou PII em logs/respostas.

### T4F-008 — Implementar catálogo e curadoria via HTTP

Responsável proposto: trevo-backend. Dependência: T4F-007. Estado inicial: blocked.

- Entregar CRUD permitido, origem MANUAL separada, mercados/seleções, suspensão/curadoria, filtros e detalhe paginados; config pública por allowlist.
- Contratos expõem IDs UUID e versões de partida/mercado/seleção; estados/horários/frescor governam elegibilidade e erros pt-BR.
- Testar autorização, validação, versões, suspensão e imutabilidade de eventos referenciados; nenhum request externo em endpoints públicos.

### T4F-009 — QA independente da fundação, schema e segurança

Responsável proposto: trevo-qa. Dependência: T4F-008. Estado inicial: blocked.

- Executar build/checks e testes PostgreSQL 16; revisar todos os critérios dos cartões 001–008 com evidências e falhas reproduzíveis.
- Verificar isolamento real de arquivos/mounts, bootstrap, autorização, CSRF, precisão, chaves e privacidade.
- Somente completar com PASS de todos os critérios aplicáveis; falha/ausência de capacidade bloqueia este gate e os sucessores, sem modificar implementação.

### T4F-010 — Definir provedor substituível e contrato de testes

Responsável proposto: trevo-backend. Dependência: T4F-009. Estado inicial: blocked.

- Implementar SportsDataProvider/DTOs neutros e serviço dependente da interface, sem vazar JSON/IDs API-Football ao domínio público.
- Definir regras de identidade, períodos/linhas/participantes/bookmaker, cobertura e ofertas indisponíveis; manter todos os mercados exigidos na matriz mesmo quando sem mapeamento.
- Testar contrato com provedor falso rotulado e troca por outra implementação de teste sem mudar serviços; isso não constitui prova de fornecedor real.

### T4F-011 — Implementar adapter HTTP API-Football

Responsável proposto: trevo-backend. Dependência: T4F-010. Estado inicial: blocked.

- Implementar ApiFootballAdapter usando documentação oficial atual, base HTTPS fixa/paths tipados, API key só no servidor e chamadas fora de transações/locks.
- Mapear agenda/bookmakers/mercados/odds para DTOs neutros; bloquear desconhecidos, precisão excessiva, preço ausente e timestamp ambíguo, sem fabricar ofertas.
- Usar servidor HTTP simulado rotulado nos testes para sucesso, JSON inválido, 401/403, erros no corpo HTTP 200, SSRF/redirect e credencial ausente/inválida; não usar chave real nesta etapa.

### T4F-012 — Implementar quota durável e resiliência do adapter

Responsável proposto: trevo-backend. Dependência: T4F-011. Estado inicial: blocked.

- Manter sync automática desligada por padrão, allowlists explícitas, reserva e orçamento global Free<=100/dia; contabilizar descoberta, páginas, retentativas e chamadas manuais.
- Implementar estado durável protegido, reset seguro, limites de páginas/tempo/conexão/leitura, concorrência 1 e retries finitos com backoff/jitter.
- Testar 429/Retry-After, 5xx/timeouts, restart, orçamento esgotado, headers ausentes e paginação parcial; nenhuma resposta parcial é tratada como catálogo completo.

### T4F-013 — Persistir sync, frescor e bloqueio conservador

Responsável proposto: trevo-backend. Dependência: T4F-012. Estado inicial: blocked.

- Upsert idempotente por origem/mapeamento com locks ordenados; separar tentativa/sucesso/providerUpdatedAt e relógios de estado/agenda/preço.
- Replay não renova frescor; sync não reabre suspensão manual nem altera início/snapshots referenciados; divergência e reagendamento exigem revisão.
- Ofertas novas/indisponíveis/desconhecidas ficam bloqueadas e visíveis no admin; fornecer status/disparo protegido por CSRF, sem host/segredo no request.
- Testar corridas e rollback no PostgreSQL 16; estado parcial, preço vencido e mercados ausentes falham fechados na elegibilidade.

### T4F-014 — QA independente da integração simulada e segurança

Responsável proposto: trevo-qa. Dependência: T4F-013. Estado inicial: blocked.

- Executar matriz do adapter: contrato neutro, credenciais, 200 com errors, 401/403/429/5xx, timeout, paginação, quota/reset/restart, SSRF, sanitização e ausência de segredo.
- Testar indisponibilidade de mercados, cobertura não comprovada, timestamp ausente/regressivo, origem/bookmaker, replay, suspensão admin e conflito estrutural.
- Separar PASS de simulação de integração autenticada NÃO EXECUTADA; não remover requisito nem aprovar produção por cobertura parcial. Bloquear o gate se qualquer critério exigido falhar.

### T4F-015 — Construir catálogo visual multimercado

Responsável proposto: trevo-frontend. Dependência: T4F-014. Estado inicial: blocked.

- Criar cards/filtros/detalhes paginados de mercados e seleções com semântica/rótulos pt-BR e design slate/emerald mobile-first.
- Mostrar loading/vazio/erro/indisponível sem oferta fictícia; 1X2 é só incremento e mercado não suportado permanece pendência explícita.
- Exercitar navegador local a 360px e desktop, paginação e teclado; registrar capturas/ações reais sem declarar aceite visual do usuário.

### T4F-016 — Construir cupom e revisão acessíveis

Responsável proposto: trevo-frontend. Dependência: T4F-015. Estado inicial: blocked.

- Implementar estado em memória indexado por partida, substituição/remover/limpar, versões e limite de seleções.
- Cupom sticky desktop/barra mobile sem cobrir formulário, expansão com foco/Escape, prévia de valores e normalização pt-BR.
- Revisão com nome/telefone, aceite/maioridade e erros acessíveis; nenhum dado pessoal no localStorage. Testar comportamento e limites sem inferir confirmação.

### T4F-017 — Implementar cálculo financeiro e validações

Responsável proposto: trevo-backend. Dependência: T4F-016. Estado inicial: blocked.

- MoneyCalculator com BigDecimal, produto completo e HALF_UP só no retorno final; validar limites comerciais/precisão.
- Testar produto de dez odds, limiares e meia unidade de centavo; rejeitar excesso sem redução ou arredondamento silencioso.
- Demonstrar que valores definitivos do navegador não são aceitos como autoridade.

### T4F-018 — Implementar emissão transacional e snapshots

Responsável proposto: trevo-backend. Dependência: T4F-017. Estado inicial: blocked.

- Receber selectionId e versões, validar relações/duplicidade/declarações/telefone; bloquear catálogo em ordem e revalidar início/frescor após locks.
- Persistir Ticket e TicketItem atomicamente com código único e snapshots completos; validade não pode ser estendida e seleção por partida é única.
- Testar rollback, colisão limitada, versões alteradas, partida iniciada, oferta vencida, retorno adulterado e concorrência com sync; sem chamada externa.

### T4F-019 — Implementar idempotência e privacidade da emissão

Responsável proposto: trevo-backend. Dependência: T4F-018. Estado inicial: blocked.

- Vincular Idempotency-Key a conteúdo normalizado e sessão anônima; reenvio válido retorna o persistido antes de revalidar preços atuais.
- Tratar concorrência/collision após rollback em nova transação, sem recuperar outra sessão; rate limit/CSRF/no-store e erros pt-BR.
- Testar dupla emissão, chave com outro corpo/sessão, sessão expirada e logs sem PII/segredos; não criar busca pública de bilhetes.

### T4F-020 — Conectar emissão, resumo e WhatsApp

Responsável proposto: trevo-frontend. Dependência: T4F-019. Estado inicial: blocked.

- Conectar formulário real com CSRF/Idempotency-Key, estados de envio e revisão obrigatória de 409, preservando entrada em falha.
- Exibir somente resposta persistida e aviso Pendente, código, prazo, snapshots semânticos e valores; copiar código/resumo e abrir WhatsApp só por clique.
- Testar link codificado com acentos/quebras e fallback; clique não comprova envio/pagamento/confirmação e telemetria não bloqueia o fluxo.

### T4F-021 — Implementar consulta e decisões administrativas

Responsável proposto: trevo-backend. Dependência: T4F-020. Estado inicial: blocked.

- Entregar busca exata paginada privada e detalhe de snapshots, confirmação/cancelamento por comandos com ticketVersion e operador da sessão.
- Exigir declaração Pix e motivo de cancelamento; validar PENDING/prazo/curadoria/estado/frescor sob locks, mantendo cotação histórica.
- Testar confirmar vs cancelar, suspensão/sync vs confirmar e limite exato expiresAt; sem reversão de confirmado nem confirmação forçada.

### T4F-022 — Implementar expiração e indicadores operacionais

Responsável proposto: trevo-backend. Dependência: T4F-021. Estado inicial: blocked.

- Expirar condicionalmente pendentes por relógio injetável, atualizar versões/auditoria e impedir confirmação mesmo com job atrasado ou reinício.
- Entregar contagens por estado efetivo, volume confirmado, coortes/cliques e tempo de atendimento com timezone correto; sem confundir volume com lucro.
- Testar expiração concorrente, filtros/coortes e confirmados com partidas canceladas; logs e métricas sem PII/tags de alta cardinalidade.

### T4F-023 — Construir painel de operação e integração

Responsável proposto: trevo-frontend. Dependência: T4F-022. Estado inicial: blocked.

- Entregar login/painel, catálogo/curadoria, status sync/quota/bloqueios e fila privada com busca por código e filtros.
- Confirmar/cancelar com declaração/diálogo e mensagens pt-BR, exibir snapshots e conflitos com atualização sem sobrescrever decisão.
- Testar acesso/CSRF, navegação 360px/desktop e estados reais usando dados de teste; não expor raw payload/chave ou controles irrestritos.

### T4F-024 — QA ponta a ponta, concorrência e segurança

Responsável proposto: trevo-qa. Dependência: T4F-023. Estado inicial: blocked.

- Executar todos os cenários críticos do PRD e testes de integração/serviço/MockMvc; comprovar cobertura de domínio/serviços>=80% sem substituir casos críticos por percentual.
- Exercitar emissão→resumo/WhatsApp→admin→decisão e expiração com dados de teste; verificar isolamento de sessão, rollback, concorrência, snapshots, CSRF, XSS, rate limit e ausência de PII/segredo.
- Produzir matriz de aceites PASS/FAIL/BLOCKED por requisito; não corrigir implementação e não concluir gate com falhas.

### T4F-025 — QA visual, acessibilidade e dispositivos

Responsável proposto: trevo-qa. Dependência: T4F-024. Estado inicial: blocked.

- Revisar fluxos públicos/admin em navegador 360px/desktop, teclado/foco/Escape/zoom, contraste e cupom sem sobreposição.
- Registrar capturas e passos reais, distinguir teste emulado de Android/iOS/WhatsApp Web reais; ausência de dispositivo fica pendência explícita.
- Separar validação funcional/visual técnica do aceite visual do usuário; não alegar aceite humano automático nem interromper desenvolvimento para cliques de rotina.

### T4F-026 — Preparar operação local e privacidade

Responsável proposto: trevo-devops. Dependência: T4F-025. Estado inicial: blocked.

- Atualizar README/setup, imagens/JAR/assets e execução local sem credenciais padrão; nenhuma publicação/deploy remoto.
- Documentar e executar backup/restauração em banco isolado, reinício e persistência sem apagar volumes existentes; documentar RPO/RTO e política de retenção.
- Implementar procedimento controlado de anonimização e revisão de logs/headers com Segurança/DBA; não atingir dados reais nem criar cron/hospedagem sem autorização.

### T4F-027 — QA de pacote, restauração e desempenho

Responsável proposto: trevo-qa. Dependência: T4F-026. Estado inicial: blocked.

- Executar pacote real, health e app→db, migrations reinício, assets/usuário não root e restauração isolada conforme roteiro.
- Medir p95 nas condições do PRD e LCP no cenário acordado; registrar massa, ambiente e resultados, sem alegar meta se teste não ocorreu ou falhou.
- Concluir prontidão técnica de desenvolvimento com lista real de limitações; produção e paridade permanecem gates separados.

### T4F-028 — Validar integração autenticada de desenvolvimento

Responsável proposto: trevo-backend. Dependência: T4F-027. Estado inicial: blocked.

- Gate independente: executar somente com credencial segura disponibilizada, permissão e orçamento de chamadas explicitamente definido, sem criar conta/comprar plano.
- Consultar amostra real permitida de agenda/odds, verificar cobertura/temporadas/bookmaker/atualização e comparar com mocks/mapeamentos, sem aceitar apostas ou operar dinheiro real.
- Registrar códigos de resposta/contagens/horários e lacunas sanitizados; chave nunca entra no repo ou contexto. Se faltar acesso, bloquear este gate sem invalidar os testes simulados nem inventar sucesso.

### T4F-029 — Consolidar cobertura e decisão de produção

Responsável proposto: default. Dependência: T4F-028. Estado inicial: blocked.

- Gate humano final, não destinado a worker automático: apresentar matriz preservando mercados sem cobertura e evidências reais/pendências.
- Solicitar escolha do cliente sobre fornecedor/pacote, regras, licença, fonte, quotas e thresholds de produção; registrar proposta sem assumir contratação.
- Operação real/push/deploy/merge continuam proibidos até autorização específica; paridade do MVP e validação técnica de desenvolvimento são conclusões distintas.

## Referências e evidências desta preparação

Inspeção local: git status/branch/log/diff --check; PRD completo; arquivos de instrução e levantamento; profile list, SOUL dos perfis existentes, config get filtrado, kanban assignees/diagnostics, CLI help de criação/dispatch; Docker version/ps/volumes, memória/disco; fonte instalada de dispatcher e operações de arquivo. Não foram lidos .env/auth.json, nem executados testes de aplicação.

Documentação oficial recuperada em 24/10/2026 (fonte instalada/CLI prevalecem para opções disponíveis nesta instalação):

- [Índice oficial](https://hermes-agent.nousresearch.com/docs/llms.txt).
- [Kanban](https://hermes-agent.nousresearch.com/docs/user-guide/features/kanban): quadros, dependências, workspaces e dispatcher persistente.
- [Perfis](https://hermes-agent.nousresearch.com/docs/user-guide/profiles): identidade e SOUL por perfil; clone pode carregar segredos e não será usado.
- [Docker](https://hermes-agent.nousresearch.com/docs/user-guide/docker): terminal backend configurado separadamente do gateway.

## Manifesto de cartões

30 cartões criados no quadro trevo4folhas e relidos blocked antes da liberação. Cada sucessor tem dependência no anterior, workspace persistente explícito, max_retries=1, contrato local-only e goal_mode desativado. Os perfis trevo-* estão na allowlist carregada; liberar somente 001–027 por dependências após concluir 000. Os gates 028/029 continuam blocked. Consultar o Kanban para estado corrente.

| Código | ID persistido | Responsável |
| --- | --- | --- |
| 000 | t_b3264973 | default — aprovação/preflight |
| 001 | t_1ff5d3e2 | trevo-backend |
| 002 | t_5bdfb904 | trevo-devops |
| 003 | t_6f805488 | trevo-frontend |
| 004 | t_ba219778 | trevo-devops |
| 005 | t_39f2d62f | trevo-backend |
| 006 | t_e9a5dbdb | trevo-backend |
| 007 | t_9452e15b | trevo-backend |
| 008 | t_f6b950d5 | trevo-backend |
| 009 | t_7a9ea4fb | trevo-qa |
| 010 | t_c117d900 | trevo-backend |
| 011 | t_0798b5fd | trevo-backend |
| 012 | t_542e6368 | trevo-backend |
| 013 | t_30764cae | trevo-backend |
| 014 | t_28cff816 | trevo-qa |
| 015 | t_b96fd914 | trevo-frontend |
| 016 | t_b8722b00 | trevo-frontend |
| 017 | t_a03d84cf | trevo-backend |
| 018 | t_3b6e2748 | trevo-backend |
| 019 | t_38798e7f | trevo-backend |
| 020 | t_c873e95e | trevo-frontend |
| 021 | t_08601790 | trevo-backend |
| 022 | t_f2e39766 | trevo-backend |
| 023 | t_b6de2580 | trevo-frontend |
| 024 | t_08b16cc7 | trevo-qa |
| 025 | t_7a9c5ecd | trevo-qa |
| 026 | t_778594ab | trevo-devops |
| 027 | t_fd11b5bc | trevo-qa |
| 028 | t_227176c1 | trevo-backend — integração autenticada |
| 029 | t_65460a73 | default — decisão do cliente |

## Verificação da preparação

Releitura individual dos 30 cartões confirmou status blocked, 29 dependências na cadeia, responsável e workspace corretos, max_retries=1, contrato local-only e ausência de runs/session_id/started_at. Zero workers iniciados. Os 14 arquivos inspecionados de PRD/AGENTS/personas/pesquisa mantiveram hashes SHA-256 idênticos à baseline desta preparação. JSON e links locais do PRD passaram na validação. git diff --check passou; não há build de aplicação disponível para testar. A única adição no repositório nesta preparação é docs/planning/.

## Aprovação recebida e limite adicional

O usuário respondeu explicitamente “Eu aprovo” ao plano apresentado: provisionamento, quatro perfis/modelos, mounts/volumes exclusivos, ampliação restrita da allowlist e limites de execução estão aprovados dentro das restrições descritas. A cadeia só pode iniciar depois do PASS do gate 000; privilégios de host e publicação continuam excluídos.

O usuário também autorizou instalar uidmap/slirp4netns e suas dependências. A instalação por autenticação segura do sistema foi concluída e verificada, sem mudar AppArmor/kernel, parar o Docker principal ou liberar workers. Os probes de daemon/sandbox e o smoke PostgreSQL16/Testcontainers passaram nos recortes descritos no relatório; isso não substitui o restante do gate.

Autorização posterior: “Pode fazer o que achar melhor para o projeto”. O coordenador escolheu manter o runtime rootless para desenvolvimento local, aceitando explicitamente a exceção de UID 0 no namespace mapeado ao host UID 1000. Novo smoke real e releitura de resultados passaram; configurações, autenticação, serviço, cadeia e limites foram revalidados. Liberar T4F-001–027 em série e concluir o gate 000 somente após conferir a espera por dependências; manter T4F-028/029 blocked. Permanecem cgroups individuais indisponíveis, egress sem allowlist, Browser/vision desabilitados e reboot não testado. Sem chamada real à API-Football, operação financeira, commit, push, merge ou deploy. Ver [decisão de desenvolvimento](development-authorization.md) e cartões/runs para o estado corrente; autorização não é aplicação concluída.
