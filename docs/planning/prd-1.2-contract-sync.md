# Sincronização de instruções e contratos com o PRD 1.2

Data: 24/10/2026. Cartão: T4F-001 (quadro `trevo4folhas`). Escopo: registro documental da sincronização entre `PRD.md` v1.2, `AGENTS.md`, `docs/agents/` e o [levantamento de mercados](../research/prematch-market-audit.md). **Implementação não iniciada**: nada aqui é código, migration, teste executado nem integração. Nenhum mercado da matriz foi reduzido; nenhum fornecedor foi homologado.

Fontes lidas integralmente antes desta sincronização: `PRD.md` v1.2 (§1–§13.1), `AGENTS.md` raiz, as cinco personas de `docs/agents/`, os `AGENTS.md` locais de `src/`, `deploy/`, o levantamento `docs/research/prematch-market-audit.md` e os documentos de `docs/planning/`. As decisões abaixo são registradas para serem implementadas nas tarefas seguintes, não aplicadas retroativamente ao PRD.

## 1. Decisões registradas

### 1.1. Precisão decimal

- Valores monetários (`stake_amount`, `potential_return`, `selected_odds`) usam `BigDecimal` em Java e `numeric` no PostgreSQL: odds `numeric(5,2)` (faixa 1,01–100,00, no máximo duas casas decimais na entrada, validadas antes do cast SQL), valores `numeric(14,2)` e `total_odds numeric(41,20)` para suportar o produto de até dez odds de duas casas sem perda.
- `totalOdds` é o produto completo, sem arredondamento intermediário; `HALF_UP` com duas casas incide somente no retorno monetário final. Exibição da odd total aceita até quatro casas, com indicação de arredondamento; o cálculo usa o produto completo.
- Preço com precisão excedente, ausente ou fora da faixa é rejeitado ou quarentenado com motivo seguro; nunca arredondado ou inventado silenciosamente. DTOs transportam decimais como strings (`'10.00'`).
- `potentialReturn` inclui o valor apostado; `potentialProfit` só aparece com rótulo explícito. Nunca apresentar retorno potencial como ganho garantido.

### 1.2. Identidade canônica

- Três namespaces distintos e nunca inferidos por igualdade numérica/textual: referência (Picos da Sorte), fornecedor (API-Football provisório) e interno (UUID). Origem `MANUAL` é separada: IDs locais estáveis e bookmaker `MANUAL`, sem colisão com origem externa e sem simular resposta externa (estado explícito `MANUAL` + `manual_reviewed_at`).
- Identidade de mercado é a chave composta `match_id, source_namespace, bookmaker_id, external_market_id, period, line, participant, rule_key, specifiers_key` (componentes `NOT NULL`); identidade de seleção é `market_id + external_selection_key`. Rótulo de display nunca compõe identidade.
- Campo de identidade não aplicável usa sentinela canônica `NONE`, nunca `NULL` ou string vazia. Linha decimal normalizada sem zeros redundantes, distinguindo ausência de zero. `specifiers_key` é representação ordenada, determinística e limitada a 1.024 bytes; nunca truncar IDs/especificadores para caber — a indexabilidade da chave composta no PostgreSQL 16 deve ser provada em migrations/testes antes de habilitar importação.
- Se o fornecedor não oferecer ID de seleção, o adapter deve produzir chave semântica estável homologada; sem isso, a importação é bloqueada. `HOME`/`DRAW`/`AWAY` são `selection_code` apenas do mercado 1X2, não enum universal. Categoria da referência não é mercado normalizado: uma categoria pode gerar várias instâncias de mercado (período/linha/participante/regra), e nomes repetidos (ex.: IDs 62/316, 110/111 do levantamento) não podem ser fundidos por texto.

### 1.3. Reagendamento e divergência estrutural

- Depois de existir qualquer item vinculado à partida: proibido editar equipes, competição e início, e proibida exclusão física. Preservar início original, expiração e snapshots.
- Divergência estrutural importada liga `structural_conflict`, suspende a elegibilidade e exige revisão administrativa; sync nunca sobrescreve silenciosamente início nem snapshots.
- Reagendamento aprovado: cancelar a partida e cadastrar outra com identidade de origem distinta e vínculo de revisão explícito; nunca reatribuir o ID externo ao histórico existente. Partida `CANCELLED` não reabre no MVP; sync nunca remove suspensão manual nem reabre contra override/revisão.

### 1.4. Limites de desenvolvimento

- API-Football é fornecedor **provisório de desenvolvimento**; a escolha de produção é do cliente (US-12/T4F-029). O orçamento gratuito é instrumento de desenvolvimento, não garantia de operação. Nenhum teste simulado constitui integração autenticada; paridade com a matriz da Picos da Sorte não foi comprovada e o escopo de mercados não é reduzido a 1X2 (1X2 é apenas o primeiro incremento técnico possível).
- Sync fica desligada por padrão, com allowlists vazias; sem credencial, allowlists, limites, estado de quota e política experimental de frescor válidos, a execução é bloqueada. Chamadas reais exigem a etapa 028 (credencial segura, autorização e orçamento explícitos).
- Limites comerciais do MVP (configuração, aplicados no servidor): R$ 5,00–500,00 por pré-bilhete, 1–10 seleções, retorno potencial ≤ R$ 10.000,00, odds 1,01–100,00, validade até 30 min ou início da primeira partida. Mudanças valem para novas emissões; snapshots e prazos existentes não são recalculados nem estendidos.

## 2. Alinhamento das seis entidades entre schema, API e fluxos

O contrato de quatro entidades da v1.1 está revisto (PRD §8.3). Tabelas `admins`, `matches`, `markets`, `market_selections`, `tickets`, `ticket_items`; caminho `Match → Market → MarketSelection`, com `TicketItem` referenciando seleção e copiando snapshots.

| Entidade | Schema (resumo) | API/DTOs | Fluxos |
| --- | --- | --- | --- |
| `Admin` | `username` único, `password_hash`, `active`, timestamps | Nunca exposto em DTO público; operador vem da sessão | Login/sessão; decisões registram `handled_by`/`handled_at` |
| `Match` | `match_date`, `status` (`OPEN`/`SUSPENDED`/`CLOSED`/`CANCELLED`), `source_namespace` + `external_fixture_id` únicos, `provider_status`, `admin_suspended`, `structural_conflict`, `version`, metadados de frescor | `GET /api/matches` com `matchVersion`; sem odds fixas no payload de partida | Emissão/confirmação exigem `OPEN`, futuro e sem conflito; referenciada fica imutável |
| `Market` | chave composta §1.2, `market_type`, `period`, `line`, `participant`, `rule_key`/`rule_text`, `specifiers_key`, `curated`, `admin_suspended`, `provider_status`, `version` | `GET /api/matches/{id}/markets` com `marketVersion` e semântica (período/linha/participante/regra) | Uma categoria da referência pode gerar várias instâncias; mudança estrutural exige nova oferta |
| `MarketSelection` | `external_selection_key` + `selection_code`, `odds numeric(5,2)`, `curated`, `admin_suspended`, `provider_status`, `version`, frescor (`request_attempt_at`, `last_successful_sync_at`, `provider_updated_at`) | `GET /api/markets/{id}/selections` com `selectionId`, `selectionVersion`, `marketVersion`, `matchVersion`, odds em string | Elegível somente curada, fresca, não suspensa e dentro da faixa; nova oferta entra bloqueada |
| `Ticket` | `ticket_code` único (`TF-` + 10 caracteres), `request_key` único, `request_hash`/`session_hash char(64)`, valores `numeric`, `status`, `expires_at`, decisão, `whatsapp_clicked_at`, `version` | `POST /api/tickets` (`201` criação, `200` reenvio idempotente, `409` conflito); comandos `confirm`/`cancel` com `ticketVersion` | `expiresAt = min(createdAt + validade, earliestStartsAt)`; igualdade já expira |
| `TicketItem` | FKs `ticket_id`, `match_id`, `market_selection_id` coerentes (constraint trigger), `selected_odds`, três `*_version_snapshot`, snapshots textuais completos, `match_date_snapshot` | Devolvido no detalhe/resumo; identifica mercado, seleção, período, linha e participante | Conteúdo imutável; resumo e mensagem de WhatsApp saem do snapshot persistido |

Versões: mudança de preço incrementa `selectionVersion`; mudança comercial/estado/curadoria incrementa a versão do registro afetado; `version` não incrementa em mero registro de tentativa de sync. Na emissão, o cliente envia `selectionId` + `matchVersion`/`marketVersion`/`selectionVersion`; divergência responde `409` e exige nova revisão humana, sem substituir odds nem reenviar automaticamente. Reenvio idempotente já persistido é devolvido sem revalidar contra versões atuais.

## 3. Política experimental de quota (DEV) — obrigatória antes de ligar sync

Valores **experimentais de desenvolvimento**, configurados explicitamente (sem default embutido no código), válidos apenas para o modo Free provisório. Não são tolerância de produção; produção permanece bloqueada até aprovação (US-12).

| Variável | Valor DEV explícito | Fundamento |
| --- | --- | --- |
| `SPORTS_DATA_DAILY_REQUEST_BUDGET` | `100` | Teto do plano Free anunciado no pricing oficial e RF-22 |
| `SPORTS_DATA_REQUEST_RESERVE` | `10` | Reserva de diagnóstico, positiva e menor que o total (RF-22) |
| `SPORTS_DATA_MAX_CONCURRENT_RUNS` | `1` | Uma instância executora (RF-22) |
| `SPORTS_DATA_MAX_PAGES` | `10` | `/odds` pagina a 10 itens/página (guia oficial); limite de páginas por execução |
| `SPORTS_DATA_MAX_RUN_SECONDS` | `120` | Duração máxima de execução; reconciliada em 31/10/2026 com o limite conservador implementado em `SportsProperties` |
| `SPORTS_DATA_CONNECT_TIMEOUT_MS` / `SPORTS_DATA_READ_TIMEOUT_MS` | `10000` / `15000` | Timeouts finitos e positivos; leitura reconciliada em 31/10/2026 com o limite implementado |
| `SPORTS_DATA_MAX_RETRIES` | `2` | Retentativas finitas com backoff/jitter; cada retentativa consome orçamento |

Regras de operação:

- Contabilizar **toda** chamada HTTP à API: descoberta, endpoints de referência (`/odds/bets`, `/odds/bookmakers`), páginas, retentativas e disparos manuais do admin. Chamadas de mídia (logos/bandeiras) não contam no plano, mas o MVP não depende de escudos externos.
- Estado do contador é durável, em `SPORTS_DATA_QUOTA_STATE_PATH` fora do repositório e protegido; sobrevive a reinício e coordena instâncias (inicialmente, uma única executora). Sem estado confiável, bloquear chamadas.
- O guia documenta headers `x-ratelimit-requests-remaining` (diário) e `X-Ratelimit-Remaining` (por minuto) e **não** documenta janela de reset explícita: sem reset conhecido, não liberar orçamento por suposição. O pricing documenta que a API para de processar e retorna erro ao atingir a quota, sem cobrança extra; isso não substitui o contador local.
- `429`: aguardar o prazo informado (Retry-After/headers) e interromper ao esgotar orçamento. O guia adverte que excesso repetido no limite por minuto pode bloquear o acesso temporária ou permanentemente — proibido retry rápido em loop.
- Exaustão, timeout, erro no corpo com HTTP 200, `499`/`500` ou paginação incompleta marcam resultado parcial/erro, nunca catálogo completo; ausência em resposta parcial não fecha nem apaga registros.

## 4. Política experimental de frescor (DEV) — obrigatória antes de ligar sync

Relógios independentes por recurso importado (`Match`, `Market`, `MarketSelection`): `request_attempt_at` (tentativa), `last_successful_sync_at` (último sucesso) e `provider_updated_at` (evidência de atualização do preço), todos `timestamptz` nullable; `NULL` não significa fresco. HTTP 200, replay ou timestamp externo inalterado **não** renovam frescor de preço. Timestamp ausente/ambíguo/regressivo bloqueia a oferta até política de evidência aprovada. Auditar `created_at`/`updated_at` nunca serve como evidência de preço atual.

Valores experimentais DEV (explicitamente configurados; não constituem tolerância de produção):

| Variável | Valor DEV | Fundamento |
| --- | --- | --- |
| `SPORTS_DATA_MAX_PRICE_AGE_SECONDS` | `10800` (3 h) | Cadência documentada de atualização de odds pré-jogo no guia oficial (a cada 3 h); preço mais velho que a cadência é vencido |
| `SPORTS_DATA_MAX_SCHEDULE_AGE_SECONDS` | `86400` (24 h) | Guia documenta que fixtures futuras bastam com polling diário |
| `SPORTS_DATA_MAX_STATUS_AGE_SECONDS` | `86400` (24 h) | Estado pré-jogo; polling pré-jogo não garante suspensão em tempo real (RF-23) |
| `MANUAL_PRICE_MAX_AGE_SECONDS` | `86400` (24 h) | Validade uniforme experimental de revisão manual; revisar com a operação real |
| `MANUAL_SCHEDULE_MAX_AGE_SECONDS` | `86400` (24 h) | Idem; renovada somente por revisão administrativa explícita |
| `MANUAL_STATUS_MAX_AGE_SECONDS` | `86400` (24 h) | Idem; sync ou acesso público não renovam |

Regras de bloqueio (fail-closed):

- Emissão e confirmação falham fechadas se agenda/estado ou preço estiverem vencidos, indisponíveis ou sem evidência. Elegibilidade é a conjunção conservadora de `providerStatus`, `adminSuspended`, curadoria, conflito estrutural, horário e frescor.
- Novas ofertas importadas entram sem curadoria e bloqueadas. Sync não reabre suspensão manual, não altera início/snapshots de partida referenciada e não renova prazo/preço de bilhetes pendentes; confirmados não são recalculados nem cancelados automaticamente.
- Origem `MANUAL` também falha fechada após vencer sua validade de revisão; `provider_updated_at` permanece nulo nesse caso.
- Produção permanece bloqueada sem limiares aprovados, prova de cobertura, licença/fonte, atualidade e disponibilidade (US-12).

## 5. Proveniência das referências técnicas do PRD

Verificação em 24/10/2026 por leitura das fontes oficiais listadas no PRD e nos perfis. "Recuperado" = a página oficial respondeu e o trecho alegado foi conferido; "parcial" = a página oficial respondeu, mas o trecho específico não foi lido integralmente nesta rodada; "não recuperado" = a extração falhou e a alegação permanece sem revalidação nesta sincronização.

| Referência (onde é citada) | Status | Verificado nesta rodada |
| --- | --- | --- |
| [API-Football — pricing](https://www.api-football.com/pricing) (PRD §8.1) | Recuperado | Free US$ 0, 100 requisições/dia, todos os endpoints incluídos com temporadas restritas; quota esgotada retorna erro sem cobrança extra. Confere com o PRD |
| [API-Football — beginner guide, 13/03/2026](https://www.api-football.com/news/post/how-to-get-started-with-api-football-the-complete-beginners-guide) (PRD §8.1) | Recuperado | Base `https://v3.football.api-sports.io/` GET-only, header `x-apisports-key`, envelope `errors`/`paging`/`results`/`response` a conferir mesmo com HTTP 200; `/odds` paginado a 10/página; odds pré-jogo disponíveis 1–14 dias antes, atualizam ~a cada 3 h e retêm 7 dias; `/odds/bets`, `/odds/bookmakers`, `/odds/live`; IDs de bet pré-jogo e live não intercambiáveis (namespaces distintos). Confere com o PRD |
| [API-Football — documentation-v3](https://www.api-football.com/documentation-v3) (PRD §8.1) | **Não recuperado** | Extração retornou conteúdo vazio; a referência completa segue sem validação, como já declarava o PRD. Não afirmar paridade nem detalhes não confirmados |
| [Spring Boot 3.5 — system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) (PRD §8.1) | Recuperado | "Spring Boot 3.5.16 requires at least Java 17"; confere com o baseline Java 17. A página também anuncia a linha estável atual (Boot 4.x): reforça a proibição de migrar para Boot 4 sem autorização |
| [Tailwind — CLI installation](https://tailwindcss.com/docs/installation/tailwind-cli) (PRD §8.1) | Recuperado | Instalação oficial via npm `tailwindcss` + `@tailwindcss/cli` e build por CLI; compatível com Tailwind 4 e com a proibição de Play CDN em produção |
| [PostgreSQL 16 — Explicit Locking](https://www.postgresql.org/docs/16/explicit-locking.html) (PRD §8.4) | Parcial | Página oficial 16 recuperada; o trecho central (deadlocks/ordem consistente) não foi lido integralmente na extração. Alegação corroborada por fontes derivadas; revalidar o trecho ao implementar locks |
| [PostgreSQL 16 — Numeric Types](https://www.postgresql.org/docs/16/datatype-numeric.html) (PRD §8.4) | Parcial | Página oficial 16 recuperada; `numeric` como tipo exato de precisão selecionável corroborado em fontes derivadas; revalidar o trecho ao implementar o schema |
| [PostgreSQL 16 — Date/Time Types](https://www.postgresql.org/docs/16/datatype-datetime.html) (perfil DBA) | Parcial | Página oficial 16 recuperada; semântica de `timestamp without time zone` sem fuso corroborada em fontes derivadas; revalidar o trecho nas migrations |
| [Spring Security 6.5 — namespace http](https://docs.spring.io/spring-security/reference/6.5/servlet/appendix/namespace/http.html) (PRD §8.5) | Recuperado | Apêndice oficial da linha 6.5 existe e está acessível |
| [Spring Security 6.5 — password storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html) (perfil Segurança) | Recuperado | Página oficial com `BCryptPasswordEncoder`/`DelegatingPasswordEncoder` |
| [Docker Compose — startup order](https://docs.docker.com/compose/how-tos/startup-order) (perfil DevOps) | Recuperado | `depends_on` e `healthcheck` documentados |
| [WhatsApp — click to chat](https://faq.whatsapp.com/5913398998672934) (PRD RF-10) | **Não recuperado** | O extrator retornou HTTP 403 (site não suportado). O formato `wa.me` segue o PRD sem revalidação nesta rodada; validar manualmente antes de afirmar conformidade |
| [AGENTS.md guide](https://learn.chatgpt.com/docs/agent-configuration/agents-md) (AGENTS.md raiz) | Recuperado | Guia de instruções por diretório confirmado |

## 6. Checklist de contratos (consistência PRD ↔ instruções)

| Contrato | PRD | Instruções | Situação |
| --- | --- | --- | --- |
| Seis entidades/tabelas, sem apostador/carteira/pagamento | §8.3 | `docs/agents/database.md` item 1 (corrigido) | Consistente |
| `tickets.ticket_code` e `matches.match_date`, sem duplicatas | §8.3 | `AGENTS.md`, `database.md` item 6 | Consistente |
| Auditoria `TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL` + trigger `updated_at`, UTC | §8.3 | `AGENTS.md`, `database.md` itens 3–5 | Consistente |
| Precisão `numeric(5,2)`/`numeric(14,2)`/`numeric(41,20)`, `HALF_UP` só no final | §6.1, §8.3 | `backend.md` item 7, `database.md` item 8 | Consistente |
| Versões `matchVersion`/`marketVersion`/`selectionVersion` + `409` em conflito | RF-06, §8.5 | `backend.md` item 8, `frontend.md` item 6 (corrigidos) | Consistente |
| Snapshots imutáveis completos em `TicketItem` | §8.3 | `backend.md` item 8 (corrigido) | Consistente |
| Identidade canônica, sentinela `NONE`, `specifiers_key` ≤ 1.024 bytes | §8.3 | `database.md` item 13 (novo) e §1.2 deste registro | Consistente |
| Chaves únicas compostas de `matches`/`markets`/`market_selections` | §8.3 | `database.md` item 13 (novo) | Consistente |
| Frescor com três relógios independentes e fail-closed | RF-23 | `database.md` item 14 (novo), §4 deste registro | Consistente |
| Reagendamento: cancelar + nova identidade + vínculo de revisão | RF-14 | §1.3 deste registro | Registrado |
| Confirmação revalida elegibilidade (estado/curadoria/frescor) sob locks | §8.4 | `backend.md` item 9 (corrigido) | Consistente |
| Sync desligada por padrão, allowlists, orçamento, sem fallback silencioso | RF-21/22 | `backend.md` item 13 (novo), §3 deste registro | Consistente |
| Idempotência `request_key` + `request_hash` + `session_hash` | §8.3 | `backend.md` item 8 | Consistente |
| HTTP: `201`/`200` idempotente, `409` versão, `429` abuso, erros pt-BR | §8.5 | `backend.md` item 11 | Consistente |
| Matriz de mercados preservada; 1X2 só como incremento | §2.2, US-12 | Levantamento + §1.4 deste registro | Preservado |
| p95 < 200 ms como meta medida, sem afirmativa sem evidência | RNF-04 | `AGENTS.md`, `backend.md` item 12 | Consistente |

Bloqueio resolvido e limitações preservadas para as próximas etapas:

1. `AGENTS.md` raiz: bloqueio de escrita resolvido pelo coordenador após autorização pontual do usuário (“Pode autorizar”). Atualizados o mapa do repositório e os contratos de seis entidades, versões, snapshots, identidade e frescor. A edição passou pela ferramenta de patch desta sessão; nenhuma proteção geral foi desativada. O restante dos arquivos de instrução já havia sido sincronizado pelo worker.
2. `documentation-v3` da API-Football e FAQ do WhatsApp não recuperados (§5); trechos centrais das três páginas PostgreSQL 16 só parcialmente lidos.
3. Valores experimentais de quota/frescor (§3–§4) exigem configuração explícita e continuam sujeitos a revisão; produção bloqueada (US-12/T4F-029). Chamadas reais seguem bloqueadas (T4F-028).
4. Validações de indexabilidade da chave composta e dos limiares são tarefas de implementação (T4F-005/012/013), não deste registro.

## 7. Diff documental desta sincronização

- `docs/agents/database.md`: item 1 corrigido para seis tabelas + revisão do contrato antigo; itens 13–14 novos (identidade composta/sentinelas; frescor/versões).
- `docs/agents/backend.md`: itens 8–9 corrigidos (snapshots completos e três versões; confirmação revalida elegibilidade); item 13 novo (integração `SportsDataProvider`).
- `docs/agents/frontend.md`: item 6 corrigido (envio das três versões).
- `docs/planning/prd-1.2-contract-sync.md`: este registro (decisões, alinhamento, políticas experimentais, proveniência, checklist).
- `AGENTS.md` raiz: sincronização pontual aplicada pelo coordenador após autorização, complementando a entrega do worker; referência da seção 10 explicitada como histórias de usuário e critérios de aceite. Alterações pré-existentes de `AGENTS.md` e `PRD.md` foram preservadas. Na retomada do coordenador, somente `AGENTS.md` e este registro foram editados.
