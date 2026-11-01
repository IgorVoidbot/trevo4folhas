# Sprint 5 — incremento técnico e gates abertos

Data: 01/11/2026. Branch `feat/sprint-5-metrics-readiness`, baseada na main `c656cc4` (Sprint 4 integrada). **Sprint 5 permanece IN_PROGRESS**: indicadores e preparação técnica foram implementados; desempenho alvo, homologação externa, hospedagem e piloto não estão concluídos. [Contrato](sprint-5-contract.md) e [runbook](operations-runbook.md).

## Implementado e validado localmente

- Painel `/admin/metrics`, link na administração e `GET /api/admin/metrics`: coorte por emissão, estados efetivos, conversões somente após encerramento, volume/média/retornos por horário de confirmação, mediana/p90 com amostra e alerta de datas inconsistentes ou partidas canceladas. FIN-01–06 são cobertos pelos testes de métricas e pelos cenários anteriores de idempotência/decisão. Não calcula lucro, saldo ou risco agregado.
- Micrometer/Actuator: duração, tentativas e resultados com tags fixas por operação; nenhum identificador pessoal/código/URI bruta nos medidores próprios. Métricas técnicas exigem admin; health público retorna somente `status`. Env, heapdump e configurações não são expostos.
- Sessões administrativas são revogadas na próxima requisição privada após desativação ou troca do hash. Manutenção explícita de uma execução (`--operator-maintenance`), sem endpoint HTTP, usa BCrypt e não envia senha em SQL/argv. Bootstrap e senhas de teste continuam aleatórios. Operadores não recebem poderes para decidir por telefone/código público.
- Scripts privados para backup, restauração somente em destino vazio e anonimização por lista revisada de UUIDs, com dry-run e guarda de 90 dias/estado. Preservam financeiro, snapshots e rastreabilidade. Templates de cron, TLS e papel restrito, sem aplicação em hospedagem. Política definitiva e exceções de retenção permanecem sujeitas à operação.
- CSS minificado, JAR com timestamp técnico fixo, imagem normalizada com ferramentas/imagens base fixadas. `.env`, dumps, IDE e ambientes de agentes/cloud ficam fora do contexto apropriado. O pool mantém padrão de dez conexões, agora configurável por `DB_POOL_MAX_SIZE`; trinta foi usado somente em diagnóstico descartável.

## Evidências de qualidade e recuperação

| Verificação | Resultado |
| --- | --- |
| Java 17 `./mvnw spotless:check verify` | **209 testes, zero falhas/erros/ignorados; BUILD SUCCESS**, PostgreSQL 16 real via Testcontainers |
| Cobertura exigida no build | JaCoCo exige ≥80% de linhas por classe `*Service`, `TicketWriter`, `TicketIntent` e `MoneyCalculator`; todos passaram. São serviços/regras de negócio, não uma alegação de 80% para todos os getters, DTOs, adapters ou configurações |
| Classes novas | `MetricsService`, `MetricsRepository` e `OperatorSessionFilter`: 100% de linhas; `OperationTimingFilter`: 88,5%; `TicketIntent` ampliado para 89,5% |
| Frontend | ESLint, Prettier, CSS minificado e quatro testes do estado/cálculo do cupom aprovados |
| Navegador | Regressões S2–S4 e painel de indicadores em Chrome 360/1440, sem overflow, com valores vazios e eixos temporais distintos |
| Mesmo bilhete de ponta a ponta | Revisão/emissão pela UI pública a 360 px → clique explícito WhatsApp interceptado com status ainda pendente → busca, declaração Pix e confirmação pela UI administrativa a 1440 px; retorno preservado |
| Senhas/sessões | Rotação BCrypt e desativação executadas em processo real contra banco descartável; sessões antigas recusadas em catálogo, bilhetes, métricas e páginas nos testes HTTP |
| Anonimização | Exige lista privada/revisão; recusa UUID inválido e lista com registro recente; dry-run reverte, apply é idempotente e checksum financeiro preservado |
| Backup/restauração | Dump de 1.645.246 bytes, backup 0,624 s, restauração 1,371 s no ensaio registrado; seis tabelas conferidas por contagem/checksum, UTC, recusa de destino não vazio, rotação local de cópia antiga própria |
| Docker | Imagem construída; runtime UID 10001 sem Node/Maven/javac/`.env`; health mínimo; emissão e confirmação com papel PostgreSQL não superusuário; dados e operador preservados ao recriar banco sobre volume nomeado exclusivo do teste, sem porta pública de DB |
| Empacotamento repetido | JAR SHA-256 `bd99cd50bf83d2c54f48b17f5c823973d04891c806b2526c291c5f6a23a45baa` idêntico após novo package; entradas ZIP em 01/01/2026. Imagem normalizada repetiu ID `sha256:5e259948da231c2a21c0ddfaed979b2ae9f58f58b1e33111b791fd8e042b1e48` com runtime reconstruído sem cache, no mesmo host/toolchain |

A igualdade binária acima tem escopo local/controlado, sem certificação entre hosts, épocas ou ferramentas diferentes. O timestamp de empacotamento não é data de emissão/aceite. O papel de runtime exige SELECT em admins e UPDATE apenas em `updated_at` dessa tabela para permitir o row-share lock do operador; não recebe alteração de senha/ativação. Migrations/primeiro operador usam owner separado e depois o runtime inicia com Flyway desativado e Hibernate validate. Nenhuma migration nova foi necessária.

## Desempenho: alvo não atendido

Cenário: PostgreSQL 16 descartável, cem partidas/uma oferta manual elegível por partida, dez mil tickets históricos e seus snapshots, cinquenta clientes virtuais, cinco ciclos de catálogo → detalhe → emissão de um item → confirmação técnica por sessão de operador de teste. Credenciais/BCrypt inicial e warmup ficam fora da medição; HTTP inclui o banco, sem chamada ao fornecedor. Não são cinquenta participantes humanos.

O aquecimento final usou cem requisições sequenciais e 250 concorrentes por rota; as linhas fictícias de aquecimento foram removidas para recuperar exatamente dez mil tickets antes das 250 amostras medidas por operação. Host reportado: Linux x86_64, vinte CPUs. Esses dados não representam a futura hospedagem nem o máximo de dez itens por ticket.

| Operação | p95 no ensaio com pool experimental de 30 | Erros / amostras |
| --- | --- | --- |
| Catálogo | 1.467,465 ms | 0 / 250 |
| Detalhe | 272,067 ms | 0 / 250 |
| Emissão | 258,638 ms | 0 / 250 |
| Confirmação | 433,617 ms | 0 / 250 |

**RNF-04 p95 <200 ms continua aberto.** O ensaio inicial com pool dez mostrou espera média por conexão de 212,078 ms; em diagnóstico com trinta e aquecimento sequencial essa espera caiu para 1,483 ms, mas o cenário concorrente aquecido voltou a apresentar contenção e p95 acima do alvo. Não houve redução de BCrypt, retirada de locks/frescor ou cache de preço para disfarçar a meta. Uma alternativa de consulta não trouxe ganho e foi removida; o SQL do catálogo entregue permanece o original. Próximo trabalho de desempenho deve perfilar tempo por consulta/planejamento, transações e saturação no cenário fixado antes de decidir índices/pool/cache. Não aumentar pool em produção sem orçamento de conexões e medição.

LCP experimental: dez navegações Chrome em 360×800, CPU 4×, RTT 150 ms, download 200.000 bytes/s, cache desativado e catálogo fictício de três partidas. p75 **656 ms** na rodada registrada. É emulação local, não Android/iOS físico nem cenário de RNF-05 formalmente acordado; sua homologação continua aberta.

## Reprodução

- Checks: `npm run lint`, `npm run format:check`, `npm run css:build`, `node src/frontend/tests/slip-state.test.mjs` e `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw spotless:check verify`.
- Ensaio completo: `PLAYWRIGHT_MODULE=/caminho/playwright/index.mjs python3 src/frontend/tests/run-readiness.py`. Exige Chrome/Docker/Java 17 nos caminhos documentados. Cria app/bancos/credenciais e usa exclusivamente dados fictícios; WhatsApp é interceptado, sem mensagem real. O limite de login é elevado apenas no processo de teste para os vários cenários. Não usa banco ou operador local.
- Diagnóstico de carga isolado: mesmo runner com `TEST_READINESS_SCOPE=load` e, opcionalmente, `DB_POOL_MAX_SIZE=30`; não interpretar o parâmetro como configuração aprovada de produção. Fluxo de UI isolado: `TEST_READINESS_SCOPE=flow`.
- Imagem: `docker buildx build --provenance=false --output=type=docker,rewrite-timestamp=true -t trevo4folhas-app:sprint5 .`; validar com `python3 deploy/verify-image.py`. Cria rede/volume próprios, remove somente recursos desse ensaio e não publica a imagem em registry.

Artefatos não versionados: `target/surefire-reports/`, `target/site/jacoco/`, `target/verification-sprint5/` e regressões anteriores. `load-default10.json` e `load-pool30.json` preservam as rodadas descritas; outros diagnósticos podem permanecer como arquivos experimentais. `lcp-report.json`, `recovery-report.json`, `image-report.json`, `reproducible-jar.json`, `reproducible-image.json`, planos SQL e screenshots registram os escopos locais. Não há CI remoto executado, deploy ou merge nesta entrega.

## Gates que permanecem abertos

1. RNF-04: corrigir/perfilar p95 e repetir a carga representativa. Atingir o alvo em uma rodada isolada também não dispensaria homologação da hospedagem.
2. RNF-05 e usabilidade: acordar o cenário móvel; testar Android/iOS e WhatsApp Web reais, com cópia/falha de abertura, e executar teste com dez pessoas.
3. S0.01/S1.06/US-12: fornecedor, licença/cobertura/regras/odds/quotas/frescor, proposta e decisão explícita do cliente. A chave Free e fixtures não satisfazem esse gate.
4. DEC-06/07: política de retenção/exceções, domínio, HTTPS/certificados, segredos/roles na hospedagem, backup diário criptografado externo/monitorado, responsáveis/contatos e restauração nesse ambiente. Há templates/procedimentos, não instalações reais.
5. Treinamento efetivo, liberação operacional e duas semanas de piloto com registro de resultados. Não iniciados nem substituídos por automação.

Para testar o código local, recarregue o projeto Maven no IntelliJ (nova dependência Actuator), reinicie com perfil `local` e abra **Administração → Indicadores operacionais**. Os segredos e alterações do IDE permanecem locais.
