# Sprint 4 — fila, decisão manual e expiração

Entrega local de 31/10/2026 na branch `feat/sprint-4-ticket-operations`, baseada na `main` `7c03ce8`. Escopo S4.01–S4.06 conforme [contrato](sprint-4-contract.md). Métricas, carga, liberação operacional e piloto continuam na Sprint 5; decisões S0.01 e homologação S1.06 permanecem abertas.

## Entrega

- `/admin/tickets` e link na administração: fila paginada, código exato normalizado, situação e datas de emissão no fuso da banca; contagens por estado efetivo na mesma consulta. Detalhe com identificação, valores, snapshots, prazo, versão, operador/horário, motivo/observação e alerta de partida cancelada.
- Confirmação exige Pix conferido, operador autenticado/ativo no banco, versão do bilhete e pendente válido. Locks: Ticket → todos Match → todos Market → todas MarketSelection, ordenados; relógio/frescor/curadoria/estado/início reavaliados após espera. Mudança de preço atual não altera odds, valor, retorno ou snapshots emitidos.
- Cancelamento exige motivo, aceita observação limitada e somente decide pendente ainda válido. Confirmação/cancelamento registram operador da sessão, horário e versão incrementada. Segunda decisão recebe `409` com estado atual; nenhum comando recebe operador, status ou valor financeiro do cliente.
- Expiração ao iniciar e a cada minuto, até vinte lotes de duzentos por execução, com `SKIP LOCKED`, transição condicional e incremento de versão/auditoria. Vencidos já aparecem como expirados antes do job. Decididos não são alterados; pagamento posterior ao prazo exige atendimento manual fora do sistema.
- Eventos após commit em inglês, somente UUIDs técnicos, ação, estado e versão. Sem nome, telefone, código, motivo, observação ou stack trace de falha do job. Respostas administrativas `no-store`; CSRF e autorização permanecem ativos. Nenhuma consulta ao fornecedor durante operação/locks.
- Diálogo explícito, declaração Pix, motivo obrigatório, campos preservados em falhas, atualização do detalhe no conflito e recuperação por consulta em resposta desconhecida. Não há repetição automática de decisão, PII em storage ou HTML bruto.

Nenhuma migration nova foi necessária: V1–V9 já contêm colunas, constraints e índices usados. Migrations anteriores e os seis modelos de domínio foram preservados. O arquivo `src/test/resources/application.properties` desativa somente o scheduler nos testes isolados; o pacote da aplicação mantém o job habilitado por padrão.

## Verificação executada

| Verificação | Resultado |
| --- | --- |
| Java 17 `./mvnw spotless:check verify` | **BUILD SUCCESS; 201 testes, zero falhas, erros ou ignorados**, PostgreSQL 16 via Testcontainers; pacote e JaCoCo gerados |
| Cenários novos Java | 26 casos operacionais e dois do scheduler; concorrência de operadores e expiração, rollback/auditoria, locks e primeira referência versus edição, sync versus confirmação, frescor/regressão/replay/ausência de preço, prazo exato e snapshots |
| JaCoCo dos novos componentes | Serviço operacional 92,5% de linhas; repository, expiração, scheduler e auditoria 100%. O build ainda não impõe o limiar automaticamente |
| Frontend | ESLint, Prettier, CSS compilado e quatro testes de estado/cálculo do cupom aprovados |
| Chrome 360/1440 px | Catálogo/cupom/emissão anteriores e nova operação administrativa aprovados; navegação por teclado, retorno de foco do diálogo, zoom 200%, sem overflow horizontal |
| Decisões no navegador | Busca exata/detalhe, Pix não declarado bloqueado, confirmação, cancelamento/motivo/observação, conflito entre duas abas e estado atual, texto hostil escapado, 400/403/503/conexão perdida com campos preservados |
| Resposta perdida após decisão gravada | Backend confirmou a gravação; navegador perdeu a resposta e recuperou o estado por GET, sem repetir a decisão |
| Reinício real do app | App encerrado; registro de teste tornado vencido no banco descartável; reinício expirou-o para versão 2, preservando `created_at`, responsável nulo e todos os confirmados/cancelados anteriores |
| Privacidade | API key local ausente dos arquivos rastreados/novos; log da aplicação de navegador sem identidade fictícia/telefone/motivo; teste específico garante evento após commit sem dados pessoais e sem evento de decisão após rollback |
| Git | Diff sem erros; `.env`, credenciais e alterações locais do IntelliJ preservados fora do escopo |

Os testes também comprovam: odds atuais alteradas enquanto a confirmação espera não recalculam o histórico; suspensão ou vencimento durante espera bloqueiam a decisão; código parcial não encontra bilhetes; filtro de data usa meia-noite da banca (UTC−03), independentemente do fuso do navegador; consultas/contagens usam expiração efetiva; lotes pulam ticket bloqueado e não incrementam versão novamente; operador desativado com sessão antiga recebe recusa; comando adulterado é rejeitado.

Uma falha inicial do teste de recuperação do scheduler foi corrigida no restubbing do mock; a suíte completa foi reexecutada e passou. O timeout PostgreSQL retornava exceção JDBC não categorizada: a resposta agora reconhece SQLStates transitórios e devolve conflito recuperável sem expor SQL. No navegador, o filtro foi protegido durante carregamento para evitar submissões sobrepostas. O runner eleva apenas no processo descartável o limite de login para executar os vários cenários; o padrão da aplicação permanece inalterado.

## Reprodução e artefatos

```bash
npm run css:build
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw spotless:check verify
PLAYWRIGHT_MODULE=/caminho/para/playwright/index.mjs python3 src/frontend/tests/run-ticket-operations-browser.py
```

Requisitos do runner: Docker, Chrome em `/usr/bin/google-chrome`, Java 17 no caminho indicado e Playwright importável. Se o módulo estiver instalado localmente, a variável pode ser omitida. O runner cria PostgreSQL 16/app/operador/ofertas fictícios, usa contato fictício e intercepta a navegação WhatsApp. Ao terminar, encerra app/banco e remove a credencial temporária. Não usa o banco ou operador local e não envia mensagens reais.

Relatórios locais não versionados: `target/surefire-reports/`, `target/site/jacoco/`, `target/verification-sprint2/`, `target/verification-sprint3/` e `target/verification-sprint4/`. Screenshots da nova interface: `confirmed-360.png`, `confirmed-1440.png`, `cancelled-360.png`, `cancelled-1440.png` e `zoom-200.png` no último diretório.

Para testar pelo IntelliJ: reiniciar com perfil `local`, entrar no admin e abrir **Gerenciar pré-bilhetes**. A expiração de pendentes antigos é comportamento esperado após o reinício. Não há deploy, merge, CI remoto, benchmark p95 ou teste em aparelho físico nesta entrega. Sync e homologação externa continuam separadas; preparação de produção/piloto depende dos gates restantes.
