# Auditoria do estado do projeto — 31/10/2026

Solicitação: conferir a implementação, reconciliar os checklists das sprints, revisar o Git e registrar um commit local. Baseline anterior à auditoria: `8f1eecc` (`Save WIP authentication and catalog checkpoint`). Não houve autorização de push ou publicação.

## Resultado por sprint

| Sprint | Estado | Tarefas concluídas | Implementação e pendência |
| --- | --- | --- | --- |
| S0 — Fundação | IN_PROGRESS | 3/4 | Java/build, assets, PostgreSQL/Compose, configuração, Clock e checks prontos localmente. S0.01 ainda depende de decisões comerciais/operacionais |
| S1 — Persistência, admin e catálogo | IN_PROGRESS | 5/6 | Migrations, seis entidades, autenticação, gestão/curadoria e APIs públicas entregues. S1.06 permanece parcial em homologação |
| S2 — Catálogo visual e cupom | DONE local | 5/5 | Seleção multimercado, cupom responsivo, cálculo de prévia e revisão com ofertas fictícias, sem emissão |
| S3 — Emissão e WhatsApp | TODO | 0/6 | Há entidades, schema e DTOs de leitura; não há cálculo financeiro autoritativo, serviço/endpoint de emissão, idempotência operacional ou link derivado de bilhete persistido |
| S4 — Decisão e expiração | TODO | 0/6 | Há enums, colunas e repositories; não há fila/detalhe de bilhetes, comandos de confirmação/cancelamento nem job de expiração |
| S5 — Métricas e operação | TODO | 0/5 | Infraestrutura e segurança de base reaproveitáveis; indicadores, fluxo completo, carga, homologação, backup/restauração e piloto pendentes |

São **13 de 32 tarefas principais** concluídas no escopo local; não é percentual de prontidão do MVP. A relevância e o esforço dos aceites restantes variam. Os checklists completos estão em [SPRINTS.md](../SPRINTS.md). Itens de sprints futuras não foram marcados pelo simples reaproveitamento de uma tabela, DTO ou controle de segurança.

## Implementação conferida

- Monólito Java 17/Spring Boot 3.5.16, Maven Wrapper e dependências Web/Thymeleaf/JPA/Security/Validation, JDBC/Flyway e Testcontainers.
- PostgreSQL 16, migrations V1–V8, schema técnico Flyway separado, UTC, auditoria pelo banco, integridade e precisão de domínio. `BaseEntity`, versões e metadados operacionais foram revisados junto aos testes de regressão.
- Bootstrap controlado, BCrypt, login e logout POST com CSRF, rotação de sessão, cookies, respostas JSON/páginas diferenciadas, limite de tentativas e headers de segurança. Usuários de banco solicitados para desenvolvimento não constituem política de produção.
- Catálogo administrativo completo no incremento: criação/edição, curadoria, suspensão, estados, regras, versões, locks, rejeição de alterações históricas e de cancelamento reaberto. Catálogo público paginado e filtrado por elegibilidade/frescor; configurações públicas em whitelist.
- Adapter backend provisório: host fixo, allowlists, mapeamento explícito, quota protegida, orçamento/reserva, locks de execução, paginação, timeouts e tratamento conservador de falhas; testes simulados separados da evidência autenticada.
- Página pública com filtros, mercados/seleções paginados, regras, uma seleção por partida, limite configurável até dez e cálculo de prévia com produto inteiro exato. Cupom desktop/móvel, campos de revisão e declarações, PII somente em memória e revalidação explícita de alterações.
- `.env` local opcional somente no perfil `local`; Compose com porta do banco configurável. Script de demonstração autentica nas APIs e não duplica as partidas de teste. Nenhum segredo local foi incluído no código.

## Lacunas e decisões que continuam abertas

1. **S0.01 / DEC-01–DEC-07:** WhatsApp oficial e horários, aprovação dos limites/validade e tratamento de Pix, baseline completa de mercados, termos/privacidade/retenção, fornecedor/licença e operação/hospedagem. O timezone e os valores DEV estão registrados; não equivalem a decisão de produção.
2. **S1.06:** o diagnóstico autenticado confirmou assinatura Free e partidas históricas, mas a amostra de odds veio vazia e a temporada atual foi recusada. Falta amostra de odds não vazia, documentação integral, execução real do adapter, bookmakers/mapeamentos homologados, fluxo de reagendamento aprovado com vínculo explícito e testes de US-11 que dependem de emissão/confirmação futuras. A documentação de quota tinha timeouts de 300 s/30 s incompatíveis com os limites de código; foi alinhada para 120 s/15 s nesta revisão. A descoberta e o runtime ainda precisam reconciliar seus contadores antes de habilitar importação.
3. **Compatibilidade antes de S3:** o nome da revisão pública admite até 120 caracteres e `Ticket.bettorName`/schema admitem 100. `BettingProperties` admite 14 dígitos inteiros monetários, enquanto `numeric(14,2)` comporta 12. Harmonizar DTO/configuração/UI/schema antes de habilitar emissão; hoje a geração está desabilitada e não persiste esses dados.
4. **S3 e S4:** persistência isolada não implementa transação de emissão, replay, prazo, locks financeiros, decisão Pix ou expiração. As proteções existentes não dispensam os testes de corrida previstos nessas sprints.
5. **S5:** sem workflow remoto de CI, limiar JaCoCo imposto automaticamente, métricas FIN-01–06, carga/p95, LCP homologado, recuperação/backup demonstrados, HTTPS/hospedagem ou piloto. O Dockerfile multi-stage existe, mas não foi reconstruído nesta auditoria. Gerar CSS/JAR local não comprova release ou operação pronta.

Referências de código para os alinhamentos de S3: `src/main/resources/templates/index.html`, `src/main/java/com/trevo4folhas/ticket/Ticket.java`, `src/main/java/com/trevo4folhas/config/BettingProperties.java` e `src/main/resources/db/migration/V6__create_tickets.sql`. Foram registradas pendências de implementação, sem mudar regras financeiras nesta auditoria.

## Verificações desta execução

| Verificação | Resultado |
| --- | --- |
| `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw spotless:check verify` | BUILD SUCCESS; **151 testes, zero falhas, erros ou ignorados**, PostgreSQL 16 real via Testcontainers; JAR e JaCoCo gerados |
| `npm run lint` | Aprovado |
| `npm run format:check` | Aprovado |
| `npm run css:build` | Aprovado; CSS gerado a partir da fonte |
| `node src/frontend/tests/slip-state.test.mjs` | **4 testes aprovados** |
| `public-catalog-browser.mjs` via instância local temporária | Aprovado: Chrome 360/1440, viewport reduzido, contraste/ampliação CSS, estados/paginação, cupom/revisão, dez seleções, mudanças de versão/preço, segurança de saída, privacidade e erros simulados |
| `git diff --check` | Aprovado |
| Verificação de segredos candidatos | Arquivos candidatos conferidos contra as credenciais locais sem imprimir valores; sem correspondência ou bloco de chave privada; `.env` e `.env.admin-access` ignorados e não rastreados |

Cobertura por **linhas dos pacotes**, extraída do relatório atual: catálogo 405/449 (90,2%), integração esportiva 553/644 (85,9%), administração 126/128 (98,4%). Esses números não impõem automaticamente o limiar do PRD nem comprovam cenários ainda não implementados. Não foi declarado p95 ou cobertura completa do MVP.

Logs desta auditoria: `/tmp/trevo-audit-java.log`, `/tmp/trevo-audit-frontend-tests.log` e `/tmp/trevo-audit-browser.log`. Relatórios e imagens em `target/surefire-reports/`, `target/site/jacoco/` e `target/verification-sprint2/`. Os artefatos temporários não entram no commit. Não foram feitas novas chamadas ao fornecedor nem mutações de bilhetes na auditoria. A instância temporária foi encerrada; aplicação do usuário e volume do banco foram preservados.

Os testes visuais são em Chrome headless com viewports emulados, cores renderizadas e ampliação CSS, não em aparelhos físicos, leitores de tela, todos os navegadores ou teclado virtual real. O aviso Node sobre inferência de módulo ES e os avisos Java de APIs descontinuadas não impediram os checks.

## Revisão do Git e checkpoint

A árvore inicialmente tinha implementação das Sprints 1/2, organização documental e alterações locais de IDE ainda sem commit. O checkpoint reúne o código funcional, os testes, a documentação e os checklists reconciliados. As mudanças preexistentes em `.idea/misc.xml` e a exclusão de `.idea/modules.xml` foram preservadas fora do commit. Credenciais, `.env*` privados, estado operacional de quota, payloads autenticados privados, dependências e `target/` ficam excluídos.

O assunto do commit descreve a entrega de catálogo/cupom e a reconciliação das sprints; o hash será informado no retorno da execução, sem autorreferência circular neste arquivo. Não houve push, merge, tag ou deploy. O próximo incremento funcional é S3, com S0.01/S1.06 acompanhados como pendências separadas.
