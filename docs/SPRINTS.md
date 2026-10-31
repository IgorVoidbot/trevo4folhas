# TREVO4FOLHAS — Sprints e plano de entrega

**Versão:** 1.3 · **Data:** 31/10/2026 · **Fonte:** [PRD](PRD.md).\
**Projeto:** continuidade do repositório existente; não é um reinício da implementação.

## 1. Como executar este plano

As seis sprints abaixo organizam dependências e preservam os IDs S0.01–S5.05 da versão anterior. A referência inicial de uma semana por sprint é uma hipótese de capacidade, não compromisso de calendário; integração, paridade e homologação podem exigir incrementos adicionais. Medir o trabalho restante após reconciliar o código e as evidências. As duas semanas de piloto começam somente após preparação e liberação adequadas.

Checklists reconciliados em 31/10/2026 pela [auditoria do projeto](planning/project-audit-2026-10-31.md). Caixas marcadas representam aceite comprovado no escopo local descrito; caixas abertas representam trabalho incompleto ou pendência de aprovação/homologação. Uma caixa pai só é marcada se todos os seus aceites estiverem atendidos. A existência de entidade/schema não conclui os fluxos das sprints posteriores. Não recriar componentes presentes.

Estados de acompanhamento: `TODO`, `IN_PROGRESS`, `REVIEW`, `BLOCKED`, `DONE`. Uma tarefa BLOCKED registra causa, responsável e condição de desbloqueio; DONE exige evidência reproduzível. Este arquivo não altera automaticamente o quadro externo.

## 2. Reconciliação inicial obrigatória

| Área | Evidência observada | Próximo passo |
| --- | --- | --- |
| Fundação S0 | Build/checks e banco local executados; 151 testes Java e 4 de cupom aprovados nesta auditoria | Completar decisões de S0.01; não confundir checks locais com CI remoto |
| Persistência S1.01–02 | Seis entidades, migrations V1–V8 e testes PostgreSQL 16 aprovados | Preservar migrations aplicadas ao implementar emissão/decisão |
| Segurança S1.03 | Bootstrap, sessão, CSRF/rate limiting e testes aprovados | Manter controles e ampliar a validação para os fluxos futuros |
| Catálogo S1.04 | APIs, edição/curadoria, UI administrativa e catálogo público implementados | Concluir homologação S1.06; não publicar dados fictícios em produção |
| S2 e jornadas seguintes | S2 entregue com dados locais; S3–S5 sem fluxos implementados | Iniciar S3 sem apresentar S1.06 ou produção como concluídas |

Registros anteriores em [planning/t4f-007-progress.md](planning/t4f-007-progress.md) e [planning/t4f-007-qa.md](planning/t4f-007-qa.md) são evidência histórica, não teste reexecutado hoje. Os IDs T4F-* do quadro e S* deste plano pertencem a namespaces distintos: registrar correspondência verificada, nunca supor equivalência numérica. A autorização anterior está em [planning/development-authorization.md](planning/development-authorization.md); esta edição não amplia autorizações nem inicia workers.

## 3. Responsabilidades, dependências e aceites

| Sprint | Resultado | Liderança | Dependências / saída |
| --- | --- | --- | --- |
| S0 | Fundação reconciliada e contratos definidos | ARCHITECT_AGENT + BACKEND_AGENT | Setup reproduzível, parâmetros e lacunas documentados |
| S1 | Persistência, admin e catálogo local seguro | BACKEND_AGENT | S0; UI e segurança revisadas; mocks separados de homologação real |
| S2 | Catálogo visual e cupom responsivo | FRONTEND_AGENT | Contrato público de S1; pode usar fixtures rotuladas enquanto a integração real está pendente |
| S3 | Emissão atômica e WhatsApp | BACKEND_AGENT + FRONTEND_AGENT | S1/S2; idempotência e snapshots comprovados |
| S4 | Fila, confirmação e expiração | BACKEND_AGENT + FRONTEND_AGENT | S3; concorrência e prazo comprovados |
| S5 | Métricas e preparação operacional | ARCHITECT_AGENT coordena os perfis | S4; FIN-01–06, qualidade e gates de produção |

Leitura adicional obrigatória conforme o arquivo: perfis existentes [DBA](agents/database.md), [DevOps](agents/devops.md) e [Segurança](agents/security.md), além do AGENTS.md de cada caminho. Os perfis solicitados não removem esses especialistas nem autorizam execução paralela automática.

**Caminho técnico:** S0 → núcleo local S1 → S2 → S3 → S4 → S5. O adapter pode ser validado com fixtures sem credencial real; a falta de amostra externa bloqueia homologação/produção, sem bloquear indevidamente toda a implementação local. Paridade de mercados não pode ser reduzida silenciosamente a 1X2.

## 4. Definições de entrada e conclusão

**Ready:** requisito e aceite identificados, contrato de API/schema conhecido, dependências verificadas, arquivos de atuação definidos e dados de teste disponíveis. Mudanças comerciais pendentes ficam explícitas.

**Done:** comportamento completo, testes adequados, mensagens pt-BR, segurança/privacidade verificadas, documentação consistente e evidência da execução. Uma tela deve ter validação visual/teclado; um serviço financeiro deve ter casos de cálculo, prazo e concorrência. Teste não executado é pendência declarada, não aprovação implícita.

Handoff de cada tarefa:

```text
ID / requisito:
Comportamento entregue:
Arquivos alterados:
Contrato afetado:
Comandos executados e resultados:
Evidências e limitações:
Dependências liberadas ou bloqueadas:
```

## 5. Backlog preservado e critérios por sprint

### Sprint 0 — Fundação e contratos

**Objetivo:** estabelecer projeto executável, decisões comerciais e ambiente reproduzível. Dependência: nenhuma.

**Estado: IN_PROGRESS.** S0.02–S0.04 concluídas no ambiente local. S0.01 continua aberta por DEC-01–DEC-07: WhatsApp/horários, aprovações comerciais, regras definitivas, privacidade/retenção e decisões operacionais. Há timezone e parâmetros DEV; isso não aprova valores de produção. Não existe workflow de CI remoto implementado; os checks locais foram executados.

- [ ] S0.01 — Registrar parâmetros do MVP.
  - [ ] Aprovar baseline multimercado e regras pendentes; registrar 1X2 apenas como possível primeiro incremento e preservar uma seleção por partida.
  - [x] Sincronizar instruções técnicas dos especialistas com o contrato v1.2 antes de implementar as camadas afetadas.
  - [x] Registrar valores mínimo/máximo, teto de retorno e validade de desenvolvimento (5/500/10.000 reais, 30 minutos; produção depende de DEC-02).
  - [ ] Definir WhatsApp, timezone e horários de atendimento da banca.
  - [ ] Registrar política de cancelamento e tratamento manual de Pix atrasado.
  - [ ] Registrar texto das regras, aviso de privacidade e política de retenção.
- [x] S0.02 — Criar a base Java.
  - [x] Criar `pom.xml` e Maven Wrapper com Java 17 e Spring Boot 3.x fixado.
  - [x] Adicionar Web, Thymeleaf, JPA, Security e Validation.
  - [x] Adicionar PostgreSQL JDBC, Flyway e módulo PostgreSQL compatível.
  - [x] Criar `TrevoApplication` e pacotes por funcionalidade.
  - [x] Configurar nome final `trevo4folhas.jar` e perfil `local`.
- [x] S0.03 — Preparar banco local e configuração.
  - [x] Criar `docker-compose.yml` com serviços `app`/`db`, volume nomeado e health check.
  - [x] Criar `.env.example` somente com placeholders.
  - [x] Configurar datasource por variáveis de ambiente.
  - [x] Configurar `ddl-auto=validate` e timezone UTC.
  - [x] Criar `BettingProperties` com validação de faixas e limite máximo de dez seleções.
  - [x] Disponibilizar `Clock` injetável para testar expiração.
- [x] S0.04 — Preparar frontend e qualidade.
  - [x] Criar `package.json` e lockfile com Tailwind CLI.
  - [x] Criar `input.css` e scripts `css:watch`/`css:build`.
  - [x] Configurar ESLint e Prettier com aspas simples em JS/CSS e HTML conforme o formatador.
  - [x] Configurar Spotless, JUnit, JaCoCo e Testcontainers.
  - [x] Criar `.gitignore` para segredos, `target/` e `node_modules/`.
  - [x] Documentar setup, requisitos Docker e comandos no README.

**Entrega/aceite da sprint:** aplicação inicia com PostgreSQL local, CSS é compilado e o pipeline inicial consegue executar os checks configurados. Nenhuma credencial real está versionada.

### Sprint 1 — Persistência, segurança e catálogo administrativo

**Objetivo:** permitir catálogo local multimercado, curadoria e adapter provisório seguro. Dependência: Sprint 0 e contratos técnicos sincronizados.

**Estado: IN_PROGRESS.** S1.01–S1.05 concluídas localmente. S1.06 permanece parcial: amostra autenticada histórica existe, mas faltam odds reais, documentação integral, fluxo de reagendamento aprovado e prova de US-11 com emissão/confirmação. Os subitens técnicos marcados do adapter foram comprovados com fixtures e PostgreSQL, sem declarar homologação externa. Contador de preflight deve ser reconciliado com o de runtime antes de habilitar importação.

**Evidência de continuidade (31/10/2026):** [validação local da Sprint 1](planning/sprint-1-validation.md), com 147 testes aprovados em PostgreSQL 16, checks de assets e limites de homologação do adapter. Esta evidência não encerra os gates de fornecedor/produção nem atualiza o quadro externo.

**Primeiro diagnóstico autenticado:** [cinco consultas reais controladas](planning/api-football-authenticated-preflight.md) confirmaram o plano Free e partidas históricas, mas não retornaram odds e recusaram a temporada 2026 por restrição do plano. S1.06 continua parcial.

- [x] S1.01 — Criar migrations de domínio.
  - [x] Criar `admins` com username único, hash e timestamps.
  - [x] Criar `matches` com `match_date`, origem, estado, suspensão, versão e timestamps, sem odds fixas.
  - [x] Criar `markets` e `market_selections` com identidade composta, semântica, curadoria, odds e metadados de frescor conforme §8.3.
  - [x] Criar `tickets` com `ticket_code`, idempotência, sessão, valores, decisão e timestamps.
  - [x] Criar `ticket_items` com snapshots e unicidade por bilhete/partida.
  - [x] Adicionar FKs restritas, checks e índices definidos no schema.
  - [x] Adicionar `TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL` e trigger de `updated_at` às seis tabelas, com conexões UTC.
  - [x] Validar migrations em banco vazio e reinício sem alterações indevidas.
- [x] S1.02 — Implementar mapeamento JPA.
  - [x] Criar `BaseEntity` com timestamps gerenciados pelo banco.
  - [x] Criar entidades, enums em inglês e repositories.
  - [x] Mapear `@Version` em partidas, mercados, seleções e bilhetes; separar metadados operacionais de alterações comerciais.
  - [x] Validar precisão e escala de `BigDecimal` contra o schema.
  - [x] Impedir exposição direta de entidades nos controllers.
- [x] S1.03 — Implementar acesso administrativo.
  - [x] Criar procedimento controlado para cadastrar o primeiro operador com BCrypt.
  - [x] Implementar `AdminUserDetailsService` com bloqueio de operador inativo.
  - [x] Configurar rotas públicas e proteção de `/admin/**` e `/api/admin/**`.
  - [x] Implementar tela de login pt-BR e mensagens de falha genéricas.
  - [x] Configurar CSRF, logout POST, cookies e timeout de sessão.
  - [x] Configurar limite de tentativas no proxy/filtro, confiando apenas em proxies conhecidos.
  - [x] Implementar resposta 401 JSON para API e redirecionamento de páginas para login.
- [x] S1.04 — Implementar gestão de partidas.
  - [x] Criar DTOs de criação/edição com Bean Validation.
  - [x] Validar equipes distintas, horário futuro e ofertas por mercado/seleção, com regra e precisão/limites de preço.
  - [x] Implementar listagem paginada e formulário de cadastro.
  - [x] Implementar edição com verificação de versão e bloqueio da linha.
  - [x] Implementar estados aberta, suspensa, fechada e cancelada.
  - [x] Bloquear alterações estruturais de partidas referenciadas e reabertura de canceladas.
  - [x] Implementar catálogo/detalhe paginados de partidas, mercados e seleções, com versões e filtros públicos.
  - [x] Implementar `GET /api/config` com whitelist de configurações públicas.
- [x] S1.05 — Validar persistência e segurança.
  - [x] Testar unicidade, checks, FKs e atualização de timestamps inclusive por SQL.
  - [x] Testar usuário anônimo, login inválido, operador inativo, CSRF e logout.
  - [x] Testar rejeição de odds inválidas e partidas iniciadas/canceladas.
  - [x] Testar conversão de filtros de data de `America/Sao_Paulo` para UTC.

- [ ] S1.06 — Implementar adapter API-Football de desenvolvimento.
  - [ ] Validar documentação e amostra autenticada autorizada antes de alegar integração; não criar conta/plano automaticamente.
  - [x] Implementar HTTP backend fora de transações/locks de domínio, mapeamento explícito e upsert idempotente local com locks ordenados.
  - [x] Implementar allowlists, quota global durável, reserva, paginação, limites, backoff/429 e estado parcial seguro.
  - [ ] Implementar relógios independentes, fail-closed, curadoria e preservação de override/reagendamento.
  - [ ] Proteger status/disparo admin, host fixo e segredos; testar US-11 com fixtures simuladas rotuladas e, separadamente, registrar evidência real permitida.

**Entrega/aceite da sprint:** operador gerencia catálogo local e curadoria; adapter é exercitado com evidência distinguindo testes simulados de amostra real. Catálogo público só expõe ofertas elegíveis. Falta de credencial/amostra é blocker declarado, não integração validada; este incremento não prova paridade de produção.

### Sprint 2 — Catálogo visual e cupom flutuante

**Objetivo:** permitir seleção e revisão acessível em desktop e celular. Dependência: Sprint 1.

**Estado: DONE no incremento local (31/10/2026).** [Evidências e limitações](planning/sprint-2-validation.md). Usa ofertas manuais fictícias; S1.06 continua parcial. Emissão permanece desabilitada até a Sprint 3. Sem alteração automática do quadro externo.

- [x] S2.01 — Construir layout público.
  - [x] Criar template base com `lang='pt-BR'`, viewport e CSS compilado.
  - [x] Aplicar `bg-slate-950`, superfícies `bg-slate-900/80` e bordas slate.
  - [x] Criar cabeçalho, filtros e estados vazio/carregando/erro.
  - [x] Criar cards e detalhe de mercados/seleções com regra, período, linha e participante; três botões somente no incremento 1X2.
  - [x] Formatar datas e odds em pt-BR sem alterar identificadores internos.
- [x] S2.02 — Implementar estado do cupom em Vanilla JS.
  - [x] Criar mapa de seleções por `matchId`.
  - [x] Adicionar/remover item e substituir opção do mesmo jogo.
  - [x] Implementar limpeza e limite de dez seleções.
  - [x] Armazenar `selectionId` e versões de partida/mercado/seleção no cupom indexado por `matchId`.
  - [x] Calcular prévia, identificar limites e sinalizar que o cálculo definitivo é do servidor.
  - [x] Normalizar valor com vírgula para string decimal preparada para a API; envio depende da Sprint 3.
- [x] S2.03 — Construir cupom responsivo.
  - [x] Criar painel lateral sticky em telas grandes.
  - [x] Criar barra inferior móvel com contagem e expansão.
  - [x] Reservar espaço para a barra e safe area.
  - [x] Implementar foco, fechamento por Escape e retorno ao disparador.
  - [x] Implementar labels, `aria-pressed`, avisos e foco visível.
- [x] S2.04 — Construir revisão final.
  - [x] Criar campos Nome e Telefone/WhatsApp com máscara apenas visual.
  - [x] Criar resumo de regras, aviso de privacidade e declarações.
  - [x] Criar estados de erro por campo e botão “Gerar pré-bilhete”.
  - [x] Validar seleção vazia, limites e informações obrigatórias antes do envio.
  - [x] Evitar persistir dados pessoais em armazenamento local.
- [x] S2.05 — Validar usabilidade.
  - [x] Verificar adição/substituição/remoção e retorno do exemplo comercial.
  - [x] Verificar 360 px, desktop, zoom, teclado e contraste.
  - [x] Verificar que o cupom não cobre o formulário e a ação principal.
  - [x] Registrar evidências visuais para revisão do fluxo.

**Entrega/aceite da sprint:** uma pessoa monta e revisa um cupom em tela pequena ou grande; a emissão só fica disponível ao conectar o backend na Sprint 3.

### Sprint 3 — Emissão transacional e WhatsApp

**Estado: TODO.** Entidades, colunas e DTOs de leitura existem; faltam cálculo financeiro autoritativo, emissão, idempotência de serviço, integração do formulário e WhatsApp. Nenhum endpoint de emissão foi entregue.

**Objetivo:** persistir um pré-bilhete íntegro e encaminhar seu resumo manualmente. Dependência: Sprints 1 e 2.

- [ ] S3.01 — Implementar cálculo no backend.
  - [ ] Criar `MoneyCalculator` usando `BigDecimal`.
  - [ ] Multiplicar odds sem arredondamentos intermediários.
  - [ ] Aplicar `HALF_UP` apenas no retorno monetário final.
  - [ ] Validar limites de valor, itens e retorno potencial.
  - [ ] Testar casos de meia unidade de centavo, limites e produto de dez odds.
- [ ] S3.02 — Implementar emissão.
  - [ ] Criar `CreateTicketRequest` e resposta sem exposição JPA.
  - [ ] Normalizar nome/telefone e validar declarações obrigatórias.
  - [ ] Validar IDs internos, relações, itens repetidos e versões de partida/mercado/seleção.
  - [ ] Implementar hierarquia compartilhada de locks da §8.4.
  - [ ] Revalidar curadoria, estado, frescor e início dentro da transação, sem chamada externa.
  - [ ] Calcular validade pelo menor prazo e capturar snapshots.
  - [ ] Gerar código aleatório com constraint única e retentativa limitada.
  - [ ] Persistir bilhete/itens com rollback integral em falha.
- [ ] S3.03 — Implementar idempotência e proteção do fluxo público.
  - [ ] Gerar nonce de sessão e UUID de tentativa lógica no frontend.
  - [ ] Persistir hash de sessão e hash do conteúdo normalizado.
  - [ ] Retornar emissão existente somente se sessão e conteúdo coincidirem.
  - [ ] Tratar colisão de chave concorrente após rollback em nova transação.
  - [ ] Configurar limite de emissões e resposta 429 em pt-BR.
  - [ ] Implementar `ApiExceptionHandler` com códigos em inglês e mensagens pt-BR.
  - [ ] Adicionar `no-store` às respostas com dados pessoais e impedir body logging.
- [ ] S3.04 — Conectar formulário e confirmação visual.
  - [ ] Enviar CSRF, UUID de idempotência e DTO com `fetch`.
  - [ ] Desabilitar envio durante processamento e preservar formulário em falhas.
  - [ ] Tratar 409 atualizando revisão e exigindo nova ação do usuário.
  - [ ] Mostrar resumo somente após confirmação da persistência pelo servidor.
  - [ ] Exibir status pendente, prazo, código e valores devolvidos pelo servidor.
  - [ ] Implementar copiar código e copiar resumo com fallback visível.
- [ ] S3.05 — Implementar link WhatsApp e medição de clique.
  - [ ] Validar número de destino da banca na configuração.
  - [ ] Gerar mensagem a partir do bilhete persistido e codificar texto.
  - [ ] Implementar abertura por ação explícita do usuário.
  - [ ] Exibir número e cópia do resumo em caso de falha.
  - [ ] Criar endpoint que verifica sessão/chave e registra primeiro clique.
  - [ ] Garantir que falha de telemetria não bloqueie o link nem altere status.
- [ ] S3.06 — Validar casos críticos da emissão.
  - [ ] Testar rollback, código único, mesma partida repetida e preço adulterado no cliente.
  - [ ] Testar duas emissões simultâneas com a mesma chave.
  - [ ] Testar chave com outro conteúdo e tentativa de recuperação em outra sessão.
  - [ ] Testar mudança de odds/estado concorrente e jogo iniciado.
  - [ ] Testar acentos, quebras de linha e ausência de efeito financeiro do clique.

**Entrega/aceite da sprint:** fluxo público gera exatamente um pré-bilhete válido, mostra resumo fiel e abre conversa; não confirma pagamento nem bilhete.

### Sprint 4 — Fila, confirmação manual e expiração

**Estado: TODO.** Existem enums, colunas e repositories, mas não há fila, comandos de decisão, job de expiração ou jornadas/testes operacionais completos.

**Objetivo:** permitir operação completa pelo cambista e fechar o ciclo de estados. Dependência: Sprint 3.

- [ ] S4.01 — Implementar consulta administrativa.
  - [ ] Criar listagem paginada por criação decrescente.
  - [ ] Criar busca exata por código com normalização de caixa/espaços.
  - [ ] Criar filtros por status e período com timezone correto.
  - [ ] Criar detalhe com snapshots, telefone, prazo e valores.
  - [ ] Mostrar operador/horário e motivos de decisões anteriores.
  - [ ] Destacar jogos cancelados em bilhetes confirmados.
- [ ] S4.02 — Implementar confirmação.
  - [ ] Criar comando específico com versão esperada e declaração de Pix conferido.
  - [ ] Bloquear bilhete e validar status/prazo na transação.
  - [ ] Bloquear partidas/mercados/seleções na hierarquia comum e validar curadoria/estado/frescor/início, sem HTTP externo.
  - [ ] Manter odds do snapshot, sem usar cotação atual para recalcular.
  - [ ] Registrar operador autenticado, horário e status `CONFIRMED`.
  - [ ] Criar diálogo de confirmação e feedback pt-BR.
- [ ] S4.03 — Implementar cancelamento.
  - [ ] Criar comando específico para pendentes.
  - [ ] Validar motivo e limitar observação operacional.
  - [ ] Registrar operador, horário e status `CANCELLED`.
  - [ ] Bloquear cancelamento de confirmados/cancelados/expirados.
  - [ ] Mostrar orientação de tratamento manual para pagamento após vencimento.
- [ ] S4.04 — Implementar expiração.
  - [ ] Criar job de atualização condicional a cada minuto.
  - [ ] Atualizar versão e timestamps nas alterações em lote.
  - [ ] Tratar bilhetes vencidos como expirados nas consultas e comandos antes do job.
  - [ ] Recuperar pendências vencidas após reinício sem alterar bilhetes decididos.
  - [ ] Garantir que as contagens usem status efetivo quando o job estiver atrasado.
- [ ] S4.05 — Implementar auditoria e conflitos.
  - [ ] Emitir eventos estruturados em inglês sem dados pessoais.
  - [ ] Retornar conflito e estado atual ao segundo operador.
  - [ ] Configurar timeout de lock e mensagem recuperável.
  - [ ] Evitar transações abertas durante etapas humanas de atendimento.
- [ ] S4.06 — Testar o ciclo operacional.
  - [ ] Testar confirmação com Pix declarado e recusa sem declaração.
  - [ ] Testar confirmação no limite exato de expiração e antes/depois do início.
  - [ ] Testar confirmação versus cancelamento e confirmação versus expiração concorrentes.
  - [ ] Testar edição/importação versus emissão e suspensão/curadoria/sync versus confirmação, incluindo timestamp regressivo, reenvio e ausência parcial.
  - [ ] Testar cancelamento de jogo com pendentes e confirmados vinculados.
  - [ ] Testar relógio injetável e recuperação após reinício.

**Entrega/aceite da sprint:** cambista localiza, confere e decide um bilhete; decisões e expirações são consistentes, sem reversão ou confirmação forçada.

### Sprint 5 — Indicadores, validação e operação piloto

**Estado: TODO.** Dockerfile e controles de segurança de base já existem; faltam métricas, auditoria de segurança final, fluxo completo, carga, implantação, restauração e piloto.

**Objetivo:** tornar o MVP mensurável e operável, com validação do fluxo completo. Dependência: Sprint 4.

- [ ] S5.01 — Implementar indicadores básicos.
  - [ ] Agregar contagens por status efetivo e período de emissão.
  - [ ] Calcular volume confirmado por horário de confirmação.
  - [ ] Calcular conversão emissão/clique/confirmação por coorte encerrada.
  - [ ] Calcular mediana/p90 de tempo até confirmação.
  - [ ] Exibir rótulos que distingam clique, confirmação, volume e retorno potencial.
  - [ ] Instrumentar duração/erros com Micrometer sem tags pessoais.
- [ ] S5.02 — Concluir privacidade e segurança.
  - [ ] Revisar acesso a todas as rotas e respostas administrativas.
  - [ ] Revisar mensagens de validação/login para garantir pt-BR.
  - [ ] Verificar escape de nomes/notas contra XSS e ausência de `innerHTML` com dados não confiáveis.
  - [ ] Configurar HTTPS, CSP, cookies e headers de privacidade.
  - [ ] Revisar logs e impedir credenciais, PII e corpos sensíveis.
  - [ ] Documentar desativação/troca de senha de operadores.
  - [ ] Documentar e ensaiar procedimento de anonimização e rotação de backups.
- [ ] S5.03 — Executar validação funcional e de qualidade.
  - [ ] Executar testes de serviço e integração com PostgreSQL real via Testcontainers.
  - [ ] Atingir cobertura de domínio/serviços e validar todos os cenários críticos.
  - [ ] Executar os checks de Java, JS e formatação definidos no projeto.
  - [ ] Verificar fluxo completo público → WhatsApp → admin usando dados de teste.
  - [ ] Verificar Android/iOS e WhatsApp Web com fallback de cópia.
  - [ ] Verificar teclado, contraste, labels, foco e mensagens de erro.
  - [ ] Executar carga inicial de 50 usuários simultâneos e registrar p95.
  - [ ] Medir LCP no cenário móvel definido e corrigir gargalos identificados.
- [ ] S5.04 — Preparar implantação simples.
  - [ ] Gerar CSS minificado e JAR/imagem reproduzível.
  - [ ] Executar migrations em homologação e documentar compatibilidade de rollback.
  - [ ] Configurar segredos da hospedagem fora do repositório.
  - [ ] Expor health público mínimo, sem detalhes do banco ou métricas privadas.
  - [ ] Agendar backup diário e testar restauração em banco isolado.
  - [ ] Documentar reinício, recuperação, RPO/RTO e contatos operacionais.
  - [ ] Validar criação inicial de operador sem credenciais padrão.
- [ ] S5.05 — Conduzir piloto e consolidar pendências.
  - [ ] Antes de operação real, cumprir US-12: homologar cobertura/regras/fonte/licença e thresholds, obter proposta e decisão do cliente sobre fornecedor de produção.
  - [ ] Realizar teste de emissão com pelo menos dez participantes.
  - [ ] Treinar cambista em Pix manual, expiração e cancelamento de partidas.
  - [ ] Acompanhar duas semanas de piloto com metas tratadas como hipóteses.
  - [ ] Registrar problemas, prioridades e ajustes de parâmetros baseados em evidência.
  - [ ] Atualizar README, documentação de operação e este PRD se houver mudanças de regra.

**Entrega/aceite da sprint:** aplicação tecnicamente pronta para piloto com checks concluídos, restauração demonstrada, indicadores disponíveis e fluxo documentado. Piloto com dinheiro/operação real fica condicionado ao gate US-12 e aos demais requisitos aplicáveis; sem isso, somente testes de desenvolvimento rotulados. As duas semanas de acompanhamento ocorrem após a preparação técnica e não estão incluídas na duração sugerida.


## 6. Complementos de aceite da versão 1.3

| Tarefa existente | Complemento obrigatório |
| --- | --- |
| S0.01 | Reconciliar DEC-01–DEC-07, registrar decisões adotadas e reconhecer valores comerciais como propostas |
| S1.06 | “API oficial” é endpoint autorizado do fornecedor; fixtures não provam licença, cobertura ou frescor |
| S2.04 / S3.04 | Aplicar inventário de telas e recuperação de falhas do PRD §15, inclusive timeout com resultado desconhecido |
| S3.02 / S3.03 | Resolver replay persistido antes de revalidar catálogo; chave não pode recuperar dados de outra sessão |
| S3.05 | Link WhatsApp usa resumo persistido, clique explícito, fallback e limite de mensagem homologado |
| S4.02 / S4.06 | Conferência Pix não contorna prazo, estado, curadoria ou frescor; mensagem adulterada não altera registro |
| S5.01 | Implementar PRD §14 e comprovar FIN-01–FIN-06; sem lucro/saldo fictícios |
| S5.03 | Testar troca de regras com página antiga, dados de outra sessão e ausência de PII em logs |
| S5.05 | Separar teste local, homologação externa e liberação operacional; não marcar produção pronta sem US-12 |

## 7. Validação e publicação

Comandos existentes a executar quando a tarefa exigir validação de código: `npm run lint`, `npm run format:check`, `npm run css:build`, `./mvnw spotless:check verify`. Conferir ambiente e manifests antes de usar. Testcontainers requer Docker acessível; indisponibilidade não autoriza substituir PostgreSQL por H2 nem declarar o teste aprovado.

Nesta atualização documental, validar links, IDs, contratos e diff; não iniciar banco, disparar sync ou executar testes de integração sem necessidade. Concluir o PRD não significa concluir as sprints. Commit, push, deploy e contratação não são etapas automáticas deste plano.
