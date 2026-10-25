# T4F-006 — revisão independente e aceite

Data: 25/10/2026. Quadro: `trevo4folhas`. Implementador: `trevo-backend`, run 13. Revisor: coordenador.

## Veredito

**APROVADA para o escopo da T4F-006.** A primeira revisão havia reprovado o candidato do run 12; os defeitos R1/R2 foram corrigidos, os fontes alterados foram inspecionados e a nova versão foi reexecutada independentemente. Este aceite não declara o MVP, a autenticação, a integração externa ou a operação de produção concluídos.

## Critérios e evidências

| Critério | Evidência conferida |
| --- | --- |
| Seis entidades com `BaseEntity`, repositories e DTOs sem exposição de JPA | Fontes inspecionados; Flyway V1–V8 e `ddl-auto=validate` em PostgreSQL 16 real |
| Auditoria pelo banco, `@Version`, `BigDecimal` e precisão do schema | Suites de round-trip/auditoria, persistência, invariantes e serialização aprovadas |
| Identidade canônica sem duplicação, uma seleção por partida e snapshots históricos preservados | `CatalogTicketPersistenceTest`, suites de migrations e inspeção das relações e DTOs |
| R1: bookkeeping não perdido em flush comercial | Correção nas três entidades; seis regressões permanentes, incluindo transações distintas; três sondas independentes originais aprovadas sem alteração |
| R2: valores financeiros como strings JSON exatas | Quatro campos dos DTOs corrigidos; quatro testes de serialização/round-trip e a sonda independente original aprovados |

As colunas operacionais do catálogo são excluídas do UPDATE comercial JPA com `updatable=false`; os bulk updates não renovam versão comercial. O contexto gerenciado continua desatualizado até recarga, como documentado. Os DTOs mantêm `BigDecimal` internamente e configuram a representação JSON textual por campo.

## Reexecução do coordenador

Executada em cópia descartável do projeto dentro de container com a imagem fixada, workspace original somente leitura, daemon rootless privado e Ryuk habilitado/não privilegiado:

- `./mvnw -B -Dmaven.repo.local=/home/trevo/.m2/repository spotless:check verify`: **BUILD SUCCESS; 72 testes, zero falhas, erros ou skips**.
- `npm run lint`: aprovado.
- `npm run format:check`: aprovado.
- Sonda original `ReviewAcceptanceTest.java`, copiada sem modificação e executada separadamente: **4 testes, zero falhas, erros ou skips**. Esses quatro casos sobrepõem regressões do conjunto principal; não são 76 testes únicos.
- SHA-256 da sonda: `18e664fe5cd4ab1aceca489f4f651d03d036af950d2468dd0c444ad3f31cb276`.
- Relatórios XML foram agregados por código e conferidos contra os logs.
- Hashes de todos os fontes e migrations preservados durante a revisão; oito migrations idênticas à baseline anterior. Nenhum teste preexistente foi alterado pela correção.
- Inspeções durante a execução observaram containers de teste não privilegiados e portas publicadas somente em loopback.
- A consulta imediata após a JVM ainda encontrou recurso temporário; a releitura posterior confirmou **zero containers de teste remanescentes**, sem desabilitar Ryuk. App e db existentes permaneceram saudáveis.

Logs e XMLs independentes: `target/review-006-round2/`. Evidências duráveis, manifestos SHA-256 e histórico da primeira reprovação ficam anexados ao cartão; não dependem de `target/` sobreviver ao próximo build.

## Autorização de continuidade

Após esclarecer que a pausa da T4F-007 vinha da ordem anterior de encerrar após a T4F-006, o coordenador perguntou se poderia liberar as próximas etapas depois de aprová-la. O usuário respondeu **“Sim”**. Está autorizada a retomada serial de T4F-007 a T4F-027, após este aceite, pelos perfis e limites existentes. A retirada do gate e o início efetivo do worker devem ser confirmados no quadro, não inferidos deste documento.

T4F-028/029 permanecem bloqueadas: não há autorização nova para credenciais, chamadas autenticadas, orçamento de fornecedor ou produção. Workers continuam sem autorização de commit/push, merge, deploy, alteração de perfis ou expansão de privilégios. A autorização anterior de publicação da T4F-006 continua restrita ao coordenador e ao checkpoint aprovado.

Alterações locais de IDE não relacionadas permanecem fora da publicação. Nenhum ensaio de desempenho ou aceite visual é alegado por esta revisão.
