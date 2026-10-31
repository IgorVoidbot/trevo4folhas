# Sprint 3 — emissão e WhatsApp

Implementação local de 31/10/2026, branch `feat/sprint-3-ticket-issuance`, baseada na `main` consolidada em `0dd26e3`. Escopo: S3.01–S3.06, conforme [contrato](sprint-3-contract.md). Fila, decisão administrativa e job de expiração pertencem à Sprint 4.

## Comportamento entregue

A página pública revisa identificação/aceites e envia uma intenção com CSRF e UUID idempotente. O servidor verifica versões, curadoria, estado, frescor, início e limites sob locks ordenados; calcula com `BigDecimal` e grava ticket/itens atomicamente com snapshots completos. Odds e retorno fornecidos pelo cliente são rejeitados. Uma seleção por partida, nome de 2–100 caracteres e valores compatíveis com `numeric(14,2)`.

Reenvio idêntico na mesma sessão retorna o registro existente antes de validar o catálogo atual. Outra sessão ou conteúdo divergente recebe conflito sem PII. Em resultado desconhecido, o navegador preserva chave/corpo e exige clique explícito para tentar novamente. Mudança de catálogo exige nova seleção/revisão. Rate limiting local por IP/sessão e CSRF permanecem ativos.

O resumo final deriva exclusivamente da persistência, exibe código, identificação, status pendente, datas, valores, regras e prazo. WhatsApp abre por clique explícito, com mensagem codificada e número público configurável. Mensagens longas têm alternativa curta explícita e resumo integral copiável. A cópia tem seleção manual como fallback. Telemetria registra somente o primeiro clique, vinculada à sessão emissora, sem confirmar pagamento ou alterar versão financeira.

V9 adiciona `rules_version` sem alterar migrations anteriores; registros existentes recebem `LEGACY_PRE_S3`. Novas emissões registram `PREVIEW-2026-10-31-V1`; página antiga durante mudança de termos deve ser rejeitada. Timestamps externos de preços foram normalizados à precisão do PostgreSQL 16 na fronteira de importação, com regressão específica para replay submicrossegundo. A sync permanece desligada.

## Verificações

- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw spotless:check verify`: **173 testes, zero falhas/erros/skips**, pacote gerado, PostgreSQL 16 via Testcontainers. Inclui aplicação em banco vazio, reinício e atualização V8→V9 com registro preservado.
- Casos da emissão: exemplo 10 × 1,80 × 2,50 = 45; meia unidade de centavo com `HALF_UP`; produto de dez odds; limites; rollback integral; colisão de código com tentativas limitadas; emissão simultânea com mesma chave; normalização e ordem dos itens; conflito de conteúdo/sessão; replay após mudança de catálogo/limites; snapshots; prazo pelo início e pelo instante de criação; suspensão, preço alterado e início durante espera de lock; falta de evidência, curadoria e regras antigas; CSRF, `no-store`, ausência de consulta pública; 429 e recuperação de janela; clique repetido e sem efeito financeiro.
- JaCoCo desta suíte: cálculo 100%, serviço de emissão 95%, writer transacional 91,5%, resumo 98,5%, store 100% e limitador 100% de linhas. Os sete componentes centrais novos (incluindo normalização) somam 316/331 linhas, 95,5%; isso não equivale à cobertura integral do domínio legado. O build ainda não impõe o limiar automaticamente.
- `npm run lint`, `npm run format:check`, `npm run css:build` e `node src/frontend/tests/slip-state.test.mjs`: aprovados; quatro testes de estado/cálculo do cupom.
- `PLAYWRIGHT_MODULE=/tmp/trevo-browser/node_modules/playwright/index.mjs python3 src/frontend/tests/run-ticket-issuance-browser.py`: aprovado em Chrome a 360/1440 px. Catálogo e revisão, teclado/zoom, ausência de overflow, emissão real e replay após perda da resposta já confirmada no banco, duplo clique, corpo/chave preservados, resumo persistido, texto malicioso escapado, URL WhatsApp com acentos/quebras, cópia manual, falha de telemetria e recuperação explícita de 400/403/409/429/503. Navegação externa interceptada; nenhuma mensagem real enviada.
- `git diff --check`: aprovado. `.env` e credenciais administrativas continuam fora do Git; alterações locais do IDE foram preservadas.

## Reproduzir o navegador

Após gerar CSS e passar o build Java, execute:

```bash
PLAYWRIGHT_MODULE=/caminho/para/playwright/index.mjs python3 src/frontend/tests/run-ticket-issuance-browser.py
```

O módulo pode ser omitido quando `playwright` estiver importável pelo Node. O runner requer Docker, Chrome em `/usr/bin/google-chrome` e Java 17 em `/usr/lib/jvm/java-17-openjdk-amd64`. Cria PostgreSQL 16 e app descartáveis, credenciais aleatórias e catálogo fictício pela API administrativa; remove credenciais temporárias e encerra app/banco ao terminar. Não usa o banco ou operador local. Contato WhatsApp fictício e navegação interceptada, sem mensagem externa.

Evidências locais não versionadas: `target/surefire-reports/`, `target/site/jacoco/`, `target/verification-sprint2/` e `target/verification-sprint3/`. Dados pessoais dos cenários são fictícios. Nenhuma chamada real ao fornecedor foi necessária.

## Limites da entrega

A validação é local e automatizada em Chrome; aplicativo nativo do WhatsApp, dispositivo físico, envio/recebimento real e piloto operacional não foram homologados. Meta p95 não foi medida nesta sprint. Confirmação/cancelamento administrativos, fila e atualização persistida dos vencidos ainda dependem da Sprint 4; a emissão/replay já apresenta o status efetivo expirado quando o prazo encerra. Decisões comerciais/S0.01, homologação de fornecedor/S1.06, segurança/carga/piloto da Sprint 5 e CI remoto permanecem separados. Não há merge ou deploy nesta entrega.

Para testar na instância do IntelliJ, reinicie a aplicação com perfil `local` após atualizar o código; V9 será aplicada pelo Flyway. O número informado pelo usuário foi normalizado com prefixo 55 e configurado somente no `.env` local. Preserve a aba durante recuperação de falha de emissão; não existe recuperação pública por código/telefone.
