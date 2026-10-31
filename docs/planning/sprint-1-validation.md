# Sprint 1 — continuidade e validação local

Data: 31/10/2026. Referência: [S1.01–S1.06](../SPRINTS.md), RF-14, RF-17 e RF-21–24 do [PRD](../PRD.md).

## Comportamento e contratos

A retomada preservou o catálogo, a interface administrativa e o adapter que já estavam em andamento. Não recriou entidades, autenticação ou migrations V1–V8. As alterações locais anteriores, inclusive a organização documental e arquivos da IDE, foram preservadas.

O catálogo local oferece criação e edição de partidas, mercados e seleções, paginação, curadoria, suspensão e estados de partida. Edição verifica versões sob locks na hierarquia partida → mercado → seleção. Ofertas externas não aceitam edição manual de preço/identidade. Partidas canceladas não reabrem; referências de bilhetes impedem alteração dos termos históricos. O catálogo público filtra horário, estado, curadoria e frescor, e `/api/config` usa uma whitelist.

O adapter provisório usa host fixo, mapeamentos explícitos, listas permitidas, quota durável protegida, reserva anterior a cada chamada, paginação e limites de execução. HTTP fica fora das transações de importação. Importação parcial preserva registros ausentes; ofertas novas exigem curadoria. Disparo e status são administrativos e erros são sanitizados. Integração e agendamento permanecem desligados por padrão.

Correções desta retomada:

- IDs externos, ligas, temporadas e mercados precisam ser números inteiros positivos. Valores fracionários e coerções de strings não entram em namespaces permitidos.
- Evidência `last_successful_sync_at` no futuro bloqueia o catálogo público em partidas, mercados e seleções, assim como a evidência de atualização do fornecedor.
- Corrigidas formatação Java e referências a globais do navegador no teste de UI.
- Durante salvamento e recarga do catálogo, os três formulários bloqueiam submissão. O foco é aplicado antes das consultas, evitando deslocamento tardio durante o preenchimento do próximo formulário.

Não houve mudança de schema nem de formato dos DTOs nesta rodada. Não foi feita chamada real ao fornecedor.

## Verificação executada

O Java padrão do ambiente era 25, incompatível com o Google Java Format 1.28.0 configurado. O build foi executado com o Java 17 já instalado, sem alterar versões do projeto:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw spotless:check verify
npm run lint
npm run format:check
npm run css:build
git diff --check
```

- Maven: **147 testes, zero falhas, zero erros e zero ignorados**, `BUILD SUCCESS`; JAR gerado em `target/trevo4folhas.jar`.
- PostgreSQL real via Testcontainers: imagem `postgres:16-alpine`, servidor 16.15 observado; migrations, reinício, constraints, auditoria, JPA, concorrência, segurança, catálogo e importação exercitados.
- Novas regressões: identidade externa fracionária ou convertida por coerção e sincronização futura em cada nível do catálogo.
- ESLint, Prettier e build Tailwind: aprovados. O CSS foi gerado a partir da fonte.
- JaCoCo, linhas dos serviços: `CatalogService` 127/146 (87,0%), `ImportService` 111/112 (99,1%) e `CatalogSyncService` 110/118 (93,2%). São medidas desta execução, não garantia de cobertura de todos os cenários ou limiar imposto pelo build.

A primeira tentativa dentro do sandbox falhou por indisponibilidade de Docker; a execução com acesso autorizado ao Docker passou. Log desta execução em `/tmp/trevo-sprint1-verify-escalated.log`; relatórios em `target/surefire-reports/` e `target/site/jacoco/`.

Após a correção de concorrência na UI, lint, formatação e CSS passaram novamente, e o JAR foi reempacotado com `./mvnw -DskipTests package` usando Java 17. Nenhum fonte Java mudou após a suíte completa de 147 testes.

## Validação no navegador

Chrome headless via Playwright, viewports de **360 e 1440 px**, navegador em UTC e banca em `America/Sao_Paulo`. A aplicação e um PostgreSQL 16 descartável foram iniciados para o teste; credenciais aleatórias existiram somente no ambiente dos processos. Ambos foram encerrados ao final. O Playwright foi instalado em `/tmp`, sem mudar dependências do projeto.

O teste `src/frontend/tests/admin-catalog-browser.mjs` passou nos dois tamanhos: login, criação de partida, mercado de total de gols e seleção, curadoria, publicação pública, conversão de 15h da banca para 18h UTC e suspensão removendo a partida do catálogo público. Também verificou ausência de overflow horizontal, Tab com foco visível e ausência de erros JavaScript. Uma primeira execução expôs a corrida de formulário corrigida nesta rodada; a reexecução completa passou.

Screenshots geradas e inspecionadas: `target/verification-sprint1/catalog-360.png` e `target/verification-sprint1/catalog-1440.png`. Log do teste: `/tmp/trevo-sprint1-browser.log`. O teste usa `TEST_BASE_URL` (somente `127.0.0.1`), `TEST_ADMIN_USERNAME`, `TEST_ADMIN_PASSWORD` e, opcionalmente, `PLAYWRIGHT_MODULE`; deve rodar exclusivamente contra uma aplicação descartável com operador de teste. O harness de inicialização desta execução está em `/tmp/trevo-sprint1-browser.py`.

Essas verificações são emulação de viewport, não teste em aparelho físico, auditoria completa de acessibilidade ou teste de todas as falhas de rede.

## Aceite restante e limites

S1.01–S1.05 têm evidência automatizada local nesta execução. A validação visual da tela administrativa é registrada separadamente abaixo. As caixas do plano geral não foram marcadas em bloco: o plano exige reconciliação por aceite e não altera o quadro externo.

S1.06 permanece **parcial em homologação**. A validação automatizada inicial usou fixtures JSON e transporte simulado; posteriormente, o [primeiro diagnóstico autenticado](api-football-authenticated-preflight.md) confirmou chave Free ativa e 380 partidas de 2024, mas não obteve odds. A temporada 2026 foi recusada pelo plano. Ainda faltam execução real do adapter, descoberta de tipos de mercado/bookmakers, amostra não vazia de odds e aprovação dos mapeamentos. Documentação integral, licença, cobertura e paridade continuam sem comprovação completa. A matriz multimercado não foi reduzida ao exemplo 1X2 usado nas fixtures.

O primeiro lote de chamadas reais teve credencial, autorização e orçamento registrados no diagnóstico; novas execuções precisam preservar a política e reconciliar o consumo observado (gate T4F-028, sem alteração automática do quadro). Produção depende de decisão sobre fornecedor, políticas de frescor, condições comerciais e paridade (T4F-029). Não houve benchmark p95, publicação, commit ou mudança de estado no quadro externo.
