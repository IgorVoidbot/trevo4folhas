# Testcontainers — recuperação da rede privada

Quadro `trevo4folhas`, T4F-005 (`t_39f2d62f`). A investigação e a correção começaram em 24/10/2026, no horário local. Este documento preserva o histórico das falhas e registra a revalidação final aprovada pelo coordenador, sem confundir verificações intermediárias com o resultado completo.

## Falha e autorização

O run 7 terminou por limite de 1.800 segundos. Os relatórios Surefire registravam `Could not connect to Ryuk at 172.17.0.1:32768` antes de iniciar PostgreSQL. O smoke anterior desabilitava Ryuk somente naquele processo e não validava JDBC com portas publicadas; portanto não comprovava esta topologia.

Após o diagnóstico inicial, o usuário autorizou explicitamente, em pergunta interativa, o ajuste restrito dos perfis `trevo-backend`, `trevo-qa` e `trevo-devops` para o namespace de rede do daemon rootless privado e, se necessário, o reinício desse daemon. Condições: portas em loopback; Ryuk habilitado e sem privilégios; socket privado; volumes preservados; Docker principal e perfis Spatial intocados.

## Reprodução e decisão

- Um Ryuk 0.12.0 de diagnóstico iniciou com `privileged=false`, `cap-drop ALL`, `no-new-privileges` e somente o socket privado montado.
- A partir do worker na bridge, a conexão à porta publicada no gateway foi recusada; o IP direto do Ryuk na bridge respondeu. Isso separou a disponibilidade do processo da rota de acesso à porta publicada.
- Um probe read-only no namespace de rede do daemon privado alcançou a mesma porta pelo loopback. As identidades de namespace de rede foram comparadas: o namespace do daemon era diferente do namespace do computador.
- Não foi adotado um IP externo arbitrário, socket principal, `--privileged` ou desativação do reaper.

## Configuração aplicada e relida

Nos três perfis autorizados, via CLI oficial Hermes, preservando os demais campos:

- `terminal.docker_extra_args`: acrescentado `--network=host`. Neste backend, `host` significa o namespace do daemon rootless privado, não o namespace de rede do computador; isso foi verificado por identidade de namespace em execução real.
- `terminal.docker_env.TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`.
- `terminal.docker_env.TESTCONTAINERS_RYUK_DISABLED=false`.
- `terminal.docker_env.TESTCONTAINERS_RYUK_CONTAINER_PRIVILEGED=false`.

Frontend não foi alterado e continua sem socket Docker. Nenhuma configuração de credencial, aprovação, modelo, ferramenta ou perfil Spatial foi modificada.

No daemon privado, `runtime/engine-rootfs-entry.sh` recebeu `--ip=127.0.0.1`. Somente `trevo4folhas-test-engine.service` foi reiniciado. A opção da bridge `com.docker.network.bridge.host_binding_ipv4` foi relida como `127.0.0.1`; os sete volumes anteriores permaneceram presentes. A unidade continuou ativa/habilitada, com limite agregado de 1 CPU/2 GiB e sem cgroups individuais comprovados. App e db voltaram a `healthy` com suas portas em loopback.

Valores anteriores e baseline dos fontes foram preservados fora do repositório, em `/home/igor/.hermes/kanban/boards/trevo4folhas/runtime/recovery/`. Não há segredos nesse snapshot de parâmetros de transporte; credenciais não foram lidas ou copiadas.

## Verificação real do transporte

Foi executado, pelas ferramentas reais do perfil Backend:

`timeout 180 ./mvnw -B -Dtest=MigrationRestartTest test`

Sessão `20261024_231344_d9f7ed`, mensagem de ferramenta 258. O coordenador releu o resultado bruto no banco de sessões, não apenas o relato final do agente. Evidência completa sanitizada preservada em `runtime/recovery/transport-observed.json`.

Resultados observados:

- Variáveis do transporte conferidas no processo; Ryuk habilitado e sem privilégios.
- Namespace de rede do processo igual ao namespace privado e diferente do computador; `NoNewPrivs=1`, capacidades limitadas.
- Testcontainers 1.21.4 conectou ao socket privado e iniciou Ryuk 0.12.0.
- PostgreSQL 16 real iniciou e foi acessado por JDBC no loopback.
- Flyway aplicou as oito migrations com sucesso.
- Os quinze fontes da baseline desta investigação permaneceram idênticos nessa verificação.
- Após o processo, não havia containers com o rótulo `org.testcontainers=true` remanescentes no daemon consultado. Nenhum volume anterior foi removido.

## Resultado intermediário da primeira verificação

O primeiro comando terminou com exit code 1 em `TestDb.awaitJdbc`: `PostgreSQL did not accept connections after restart`. Naquele momento, transporte inicial PASS não equivalia ao teste de reinício PASS nem à conclusão de T4F-005.

A retomada foi condicionada à investigação da reconexão após stop/start, comparando os mapeamentos de porta antes/depois e o estado de inspeção do container, e à correção do teardown que produzia NPE quando a inicialização falhava. Permaneceram proibidos remover testes, pular reinício, substituir PostgreSQL por H2, desativar Ryuk ou editar migrations já aplicadas para mascarar erros.

A liberação de T4F-006 depende do sucesso real do conjunto de verificações de T4F-005 e da conclusão desse gate. Prazo por tentativa, concorrência serial, limites e gates externos/humanos continuam inalterados.

## Revalidação final — run 9

Sessão Backend `20261024_235037_e51ca5`. O worker entregou a tarefa em `review`, sem liberar o sucessor. O coordenador conferiu fontes, hashes, relatórios XML, logs brutos e o encerramento do processo antes de aprovar a recuperação.

### Causas confirmadas e correções

- **Reconexão após reinício:** a porta dinâmica publicada mudava no stop/start real, enquanto o Testcontainers mantinha o mapeamento inicial em cache. No log final, o endpoint reportado permaneceu em `32789`, mas a inspeção viva mostrou `32790`. A fixture passou a resolver o mapeamento atual antes de criar a conexão; o teste real passou, preservando dados, auditoria e validação Flyway após o restart.
- **Teardown:** `TestDb.stopQuietly` passou a aceitar container nulo ou nunca iniciado, com duas regressões aprovadas, sem NPE secundário que mascarasse a falha inicial.
- **Timezone da fixture:** as sessões de teste retornavam `Etc/UTC` em vez do `UTC` explicitamente exigido. `UtcDriverDataSource` executa `SET TIME ZONE 'UTC'` em cada conexão entregue a SQL e Flyway, inclusive depois do reinício, reproduzindo a política já existente em `application.yml`. As assertions literais foram mantidas.
- **Binds SQL invertidos:** `MigrationInvariantsTest.insertMarket` enumerava `specifiers_key, rule_key`, mas vinculava `ruleKey, specifiers`. O valor longo caía em `rule_key varchar(255)`. A regressão com valores distintos falhou antes da correção e passou depois. A coluna `specifiers_key` já era `TEXT` com limite de 1.024 bytes; não foi criada a migration V9 sugerida no checkpoint intermediário.

### Execução comprovada

| Verificação | Resultado |
| --- | --- |
| Regressão antes da correção, `MigrationInvariantsTest` | RED: 22 testes, 2 falhas e 2 erros; troca de colunas e UTC reproduzidos |
| `MigrationCatalogTest`, `MigrationInvariantsTest`, `MigrationRestartTest`, `TestDbTeardownTest` | Exit 0; 38 testes, sem falhas, erros ou skips |
| `./mvnw -B spotless:check verify` completo | Exit 0; `BUILD SUCCESS`; 48 testes, sem falhas, erros ou skips |
| Flyway em PostgreSQL 16 real | Oito scripts aplicados; segundo `migrate` após restart executou zero migrations; `validate` aprovado |
| Preservação de migrations | SHA-256 dos oito arquivos V1–V8 idênticos à baseline anterior à recuperação |
| Cleanup externo | Nenhum container com label `org.testcontainers` remanescente após a JVM; processo do worker encerrado |

O resultado bruto da ferramenta na mensagem **388** registra `VERIFY_EXIT=0` e os totais completos. O coordenador também leu o log dentro do container antes do encerramento e conferiu a cópia persistida: SHA-256 `4d7af67f6bd7d3a1e1429978b9248e7c9ea034bf293c0a3f61a0f085c23e2706`. Inspeções externas durante os testes observaram Ryuk e PostgreSQL sem privilégios e portas publicadas somente em loopback.

Os seis relatórios Surefire atuais totalizam 48 testes: configuração de apostas 7, relógio 3, catálogo de migrations 13, invariantes 22, reinício 1 e teardown 2. `target/site/jacoco/` e `target/trevo4folhas.jar` foram gerados pelo build; a existência do relatório JaCoCo não constitui uma alegação de cobertura do domínio ainda não implementado.

### Evidências e limites do aceite

- Logs: `target/verification-logs/t4f005-01-red-invariants.log`, `t4f005-02-green-four-suites.log` e `t4f005-03-verify-full.log`.
- Relatórios: `target/surefire-reports/TEST-*.xml` e `target/site/jacoco/jacoco.xml`.
- Regressão RED e inspeções de containers preservadas como anexos do cartão, além das evidências iniciais do transporte.
- Revisão técnica da recuperação pelo coordenador: **PASS**. Conclusão do gate e execução dos sucessores são registradas no quadro; esse aceite não substitui o QA independente do produto.
- Gates T4F-028/029 continuam bloqueados. Não houve autorização de integração autenticada com API-Football, gasto adicional, deploy ou produção.

## Fontes primárias consultadas

- [Testcontainers — configuração](https://java.testcontainers.org/features/configuration/): distingue o host de portas (`TESTCONTAINERS_HOST_OVERRIDE`) do caminho do socket remoto.
- [Testcontainers — execução dentro de containers](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/): seleção automática de gateway e considerações de montagem.
- [RyukContainer da versão 1.21.4](https://github.com/testcontainers/testcontainers-java/blob/1.21.4/core/src/main/java/org/testcontainers/utility/RyukContainer.java): socket remoto e configuração de privilégio.
- [TestcontainersConfiguration da versão 1.21.4](https://github.com/testcontainers/testcontainers-java/blob/1.21.4/core/src/main/java/org/testcontainers/utility/TestcontainersConfiguration.java): mapeamento das variáveis e default de privilégio do Ryuk. Como a biblioteca possui default privilegiado, a correção define `false` explicitamente; isso não é inferência sobre o estado de um container antigo que já foi removido.
