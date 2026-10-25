# TREVO4FOLHAS — preflight de infraestrutura

Data: 24/10/2026. Gate: T4F-000 / t_b3264973, quadro trevo4folhas.
Status registrado nas primeiras rodadas abaixo é histórico. Após o usuário delegar a decisão (“Pode fazer o que achar melhor para o projeto”), o coordenador aceitou UID 0 dentro do user namespace, mapeado ao UID 1000 do host, exclusivamente para desenvolvimento local. É uma exceção explícita ao aceite literal anterior, não seu cumprimento. Novo smoke de ferramentas/mounts/PostgreSQL 16 passou, com resultado bruto relido; o gate administrativo pode concluir e liberar a cadeia técnica. Autorização, evidências e restrições: [decisão de desenvolvimento](development-authorization.md). Execução corrente deve ser conferida no Kanban.

Atualização posterior de transporte: o smoke sem Ryuk não validava a topologia necessária aos testes JDBC. Após autorização específica, Backend/QA/DevOps passaram a usar o namespace de rede do daemon rootless privado, distinto da rede do computador, com host de portas em loopback e Ryuk explicitamente habilitado e sem privilégios. O daemon privado foi limitado a publicar portas em loopback e os volumes foram preservados. A execução real iniciou Ryuk/PostgreSQL 16 via JDBC e aplicou oito migrations, mas o primeiro teste de reinício ainda falhou. A retomada corrigiu o mapeamento de porta em cache, o teardown e duas falhas da fixture (UTC por conexão e binds invertidos). No run 9, `./mvnw -B spotless:check verify` passou com exit 0 e 48 testes sem falhas, erros ou skips, incluindo reinício real; o coordenador conferiu saídas brutas, hashes e cleanup antes de aprovar a recuperação. Evidências e limites: [recuperação de Testcontainers](testcontainers-network-recovery.md).

## Consentimento

O usuário respondeu “Eu aprovo” ao plano v0.1. A aprovação permite provisionar os quatro perfis TREVO e a infraestrutura especificada e iniciar a cadeia somente depois do preflight passar. Não inclui sudo, instalação de pacotes de host, alterações de kernel/AppArmor, --privileged, socket Docker principal ou publicação. O consentimento está registrado no comentário do gate; não é necessário aprovar o plano inteiro novamente.

## Verificações reais

- Git permanece em main com alterações anteriores de AGENTS.md, PRD.md e docs/research/ preservadas. Somente documentos de planejamento foram atualizados nesta retomada. git diff --check passou.
- Sistema identificado por /etc/os-release: Ubuntu 26.04.1 LTS.
- dockerd-rootless.sh, dockerd-rootless-setuptool.sh e rootlesskit estão disponíveis; RootlessKit reportou versão 3.1.0 e Docker reportou 29.8.1.
- command -v não encontrou newuidmap, newgidmap, slirp4netns, pasta ou fuse-overlayfs. bwrap e unshare estão disponíveis.
- apt-cache policy confirmou que uidmap e slirp4netns não estão instalados e têm candidatos nos repositórios configurados. dbus-user-session já está instalado.
- A configuração observada permite user namespaces em kernel.unprivileged_userns_clone=1; kernel.apparmor_restrict_unprivileged_userns=1. Esses valores não foram alterados.

Comando de probe sem daemon:

```text
rootlesskit --net=host sh -c 'id; printf "rootlesskit_probe=ok\n"'
```

Falha real, exit code 1:

```text
failed to setup UID/GID map: newuidmap ... failed: ... executable file not found in $PATH
```

O comando de diagnóstico unshare --user --map-root-user true também falhou ao escrever /proc/self/uid_map, com operação não permitida. Isso não demonstra sozinho que RootlessKit seja inviável; o erro específico deste é a ausência do helper. A política AppArmor pode exigir análise posterior, mas não foi identificada como causa única nem contornada.

O checker dockerd-rootless-setuptool.sh check recusou continuar porque o Docker rootful principal está acessível em /var/run/docker.sock. Não usei --force, não parei esse daemon nem montei seu socket em workers. Uma tentativa anterior de dockerd-rootless.sh check foi rejeitada pelo próprio wrapper como subcomando incorreto; a checagem correta acima também foi executada, e nenhum daemon foi iniciado por essas chamadas.

## Estado preservado na primeira rodada — histórico

- 30 cartões continuam blocked; ready=0 e running=0 na leitura de stats.
- Quatro perfis TREVO ainda não provisionados; perfis spatial-* não modificados.
- Allowlist e gateway não alterados/reiniciados.
- Nenhuma imagem/volume/daemon TREVO criado; nenhum sudo, pacote instalado ou configuração de host modificada.
- Nenhum teste de aplicação, modelo ou integração autenticada executado. Não houve chamada a API-Football nem leitura de credenciais.
- O gate permanece incompleto; não foi marcado done para liberar sucessores.

## Autorização solicitada na primeira rodada — atendida

Autorizar instalar os pacotes uidmap e slirp4netns, com dependências necessárias do gerenciador de pacotes do Ubuntu, e repetir o preflight. uidmap fornece os helpers de mapeamento; slirp4netns oferece transporte de rede rootless. Isso não autoriza desabilitar AppArmor, alterar sysctls, criar usuário, parar o Docker principal ou usar --privileged/socket principal. Não solicitar senha pelo chat; usar apenas mecanismo seguro de elevação do ambiente se autorizado e disponível.

A instalação não garante PASS: ainda será preciso comprovar isolamento de mounts/arquivos, transporte de rede e execução real PostgreSQL 16/Testcontainers, além dos demais critérios do gate. Qualquer nova necessidade fora do escopo volta para decisão explícita.

Antes da futura liberação, atualizar os corpos dos cartões que ainda dizem aprovação pendente, registrando o consentimento já recebido e o resultado efetivo do preflight; não levar instruções antigas de espera aos workers.

## Referência consultada

A documentação oficial Docker, recuperada nesta retomada, lista newuidmap/newgidmap como pré-requisitos de host e identifica uidmap como pacote correspondente em distribuições compatíveis. A instalação não foi executada com base apenas no texto: o erro local e apt-cache confirmaram a lacuna neste host.

- [Docker Engine — Rootless mode](https://docs.docker.com/engine/security/rootless/).

## Retomada após autorização dos pacotes

O usuário respondeu “Autorizo” à instalação de uidmap e slirp4netns e às dependências necessárias. sudo -n recusou executar por exigir autenticação interativa. A instalação foi então solicitada por pkexec --disable-internal-agent, usando o mecanismo seguro do sistema, e terminou com exit code 0. Nenhuma senha foi solicitada ou recebida no chat.

dpkg-query confirmou uidmap 1:4.17.4-2ubuntu3, slirp4netns 1.3.3-1 e dependências libsubid5/libslirp0 instalados. newuidmap/newgidmap passaram a existir. Não houve alteração de kernel/AppArmor nem parada do Docker principal.

### Provas de namespace e daemon

- RootlessKit com slirp4netns e host loopback desabilitado executou com sucesso, exit code 0.
- Um probe de filesystem/PID isolado ocultou o home do Hermes, o Spatial e o socket principal e manteve o PRD acessível.
- Bubblewrap retirou capacidades efetivas necessárias ao daemon; o teste mínimo reproduziu falha de chown. Essa topologia não foi aprovada como runtime do daemon. O wrapper atual usa namespaces user/network do RootlessKit e mount/PID privados com pivot_root, sem privilégios de host.
- Scripts fora do repositório: /home/igor/.hermes/kanban/boards/trevo4folhas/runtime/isolated-engine.sh e engine-rootfs-entry.sh. O filesystem do daemon contém somente diretórios de sistema read-only, estado próprio, socket próprio e projeto read-only; não o home real. O PID 1 do namespace também usa o root isolado.
- Serviço de usuário exclusivo trevo4folhas-test-engine.service iniciou. Docker info retornou rootless e versão 29.8.1; o socket privado fica em runtime/socket/docker.sock. MemoryMax=2147483648 e CPUQuotaPerSecUSec=1s foram conferidos. O daemon avisa que não oferece limites cgroup individuais; o limite agregado do serviço não deve ser confundido com limites por container.
- A tentativa inicial com userland-proxy=false falhou por indisponibilidade de bridge netfilter no namespace. Foi restaurado o comportamento padrão de proxy do daemon, sem ignorar a proteção por variável de ambiente ou alterar o host. A inicialização passou.
- Um container com bind do root inteiro desse daemon não encontrou credenciais do host, Spatial, socket principal ou fuga pelo /proc/1/root. Isso é evidência dos probes executados, não certificação geral de segurança.
- Na primeira rodada o serviço era transiente. Na retomada final foi substituído por unidade persistente habilitada no `default.target` do usuário; `Linger=yes` foi confirmado. O comportamento após reboot não foi testado.

### Perfis e imagem

Foram criados trevo-devops, trevo-backend, trevo-frontend e trevo-qa via CLI, sem clone e sem alias global. Cada um recebeu SOUL.md próprio, modelo/provider, agent.max_turns=60, backend Docker obrigatório e degraded_mode=fail. Os parâmetros solicitados ao container foram alinhados para CPU=1 e memória=2048 MiB; o daemon rootless não aplica limites cgroup individuais, portanto vale apenas o limite agregado systemd descrito na seção final. A allowlist preserva spatial-builder/spatial-qa e acrescenta os quatro perfis TREVO; o gateway foi reiniciado e diagnostics confirmou a configuração.

Ferramentas habilitadas: terminal, file, skills e web. Browser/vision continuam desabilitados; não há prova de automação visual isolada. O acesso real ao modelo e os toolsets terminal/file foram validados na retomada final.

Imagem de ferramentas construída e fixada nos quatro perfis por ID local:

```text
sha256:6498719dbdbf76ee0e4f1f99a21f710171965828c11548d36a0e33ccb83fdcf1
```

Bases fixadas por digest; Java Temurin 17.0.20.1, Maven 3.9.16, Node 26.11.1, Docker CLI 26.1.5 e Compose 2.26.1 foram exercitados. A versão de Node vem da imagem base; não corresponde a uma migração do frontend para outro framework. O processo aparece como UID 0 dentro do user namespace RootlessKit, mapeado ao UID 1000 do host; não é root do host. Seis volumes de cache exclusivos TREVO foram criados; nenhum volume spatial-* foi reutilizado.

Probe direto inicial confirmou os mounts mínimos. Na retomada final, os quatro perfis executaram terminal e read_file dentro do backend Docker real: projeto do host montado em `/workspace`, escrita temporária permitida e `.git` sobreposto read-only. Backend/DevOps/QA conectaram apenas ao socket do daemon rootless privado; Frontend não recebeu esse socket. O home de perfis, áreas spatial e socket Docker principal permaneceram inacessíveis.

### Smoke inicial Testcontainers/PostgreSQL 16 — histórico

Um harness de infraestrutura, não código da aplicação, foi criado sob runtime/preflight. A versão Testcontainers 2.0.5 foi confirmada no metadata do Maven Central e executada com Java 17 dentro da imagem de ferramentas. O harness iniciou um postgres:16-alpine real no daemon isolado, executou SQL e obteve:

```text
TESTCONTAINERS_POSTGRES16_SQL=PASS (16|42)
PUBLISHED_DATABASE_PORTS=NONE
DATABASE_STOPPED_BY_TRY_WITH_RESOURCES=PASS
```

Para este smoke sem portas publicadas, Ryuk foi explicitamente desabilitado somente no processo de teste; o container foi fechado pelo try-with-resources. Não apresentar isso como validação do Ryuk ou conectividade JDBC por porta publicada. Docker ps --all do daemon isolado ficou vazio após o teste. Não houve chamada a API-Football ou teste de aplicação.

### Preservação e bloqueio atual

Backup do workspace aprovado: /home/igor/.hermes/kanban/boards/trevo4folhas/baselines/approved-20261024T162134.tar.gz, com manifesto SHA-256 adjacente; 22 arquivos de código/documentação/planejamento preservados, sem Git metadata, IDE, env/segredos ou symlinks. Nenhum commit, push, merge ou deploy.

O primeiro teste de acesso ao modelo trevo-backend falhou, antes de cadastrar a credencial:

```text
No usable credentials found for provider 'opencode-go'. Set OPENCODE_GO_API_KEY.
```

Esse bloqueio foi resolvido posteriormente por cadastro autorizado no armazenamento de autenticação Hermes. Ver seção final para o estado atual, sem reproduzir a credencial.

## Retomada final — evidências verificadas

- `hermes -p <perfil> auth status opencode-go` retornou `logged in` para trevo-backend, trevo-devops, trevo-frontend e trevo-qa. One-shots independentes em cada perfil completaram inferência. A chave não foi gravada no repositório nem exibida; status de autenticação reside no Hermes.
- A imagem final fixada nos quatro perfis é `sha256:6498719dbdbf76ee0e4f1f99a21f710171965828c11548d36a0e33ccb83fdcf1`. Java, Maven e Node foram executados via terminal Hermes. Maven usa `/root` como diretório temporário do Jansi porque `/tmp` é `noexec`; `MAVEN_OPTS=-Djansi.tmpdir=/root` removeu o aviso sem relaxar `noexec`.
- Workspace do host `/home/igor/Área de trabalho/Projeto-aposta` é montado no container em `/workspace` com escrita permitida. `.git` é um mount separado `/workspace/.git:ro`; tentativas reais de `touch` retornaram `Read-only file system`. `read_file` leu uma linha de `/workspace/PRD.md` em cada perfil sem precisar imprimir o conteúdo.
- O processo retorna `uid=0` dentro do user namespace. `/proc/self/uid_map` e `gid_map`: `0 1000 1` e `1 100000 65536`; `NoNewPrivs=1`; `CapEff=0xCB`, limitado a CHOWN, DAC_OVERRIDE, FOWNER, SETGID e SETUID dentro do namespace. Isso não satisfaz literalmente o critério original de UID não-zero dentro do container. O coordenador aceitou a exceção somente para workers locais após a autorização delegada registrada na decisão de desenvolvimento; a aplicação final continua obrigada a executar não-root.
- `/var/run/docker.sock` e `/run/docker.sock` estão ausentes nos containers. DevOps/Backend/QA recebem somente o socket privado do daemon TREVO; Frontend não recebe socket algum. O `docker info` dos perfis com acesso reportou `seccomp=profile=builtin`, `rootless` e `cgroupns`.
- O daemon usa RootlessKit + slirp4netns, namespace mount/PID privado, `pivot_root` e filesystem dedicado em `runtime/engine-data`; apenas diretórios de sistema read-only, workspace, `.git` read-only, socket/attachments/preflight necessários são expostos. Nenhum perfil `spatial-*` foi montado.
- A unidade persistente `/home/igor/.config/systemd/user/trevo4folhas-test-engine.service` está `enabled` e `active`, com `Delegate=yes`, `MemoryMax=2147483648`, `CPUQuota=100%`. `Linger=yes` foi confirmado. A inicialização após reboot não foi exercitada.
- Docker rootless reporta `Cgroup Driver: none` e avisa que limites individuais por container não estão disponíveis. Portanto `container_cpu=1`/`container_memory=2048` são pedidos de runtime, não limites comprovados por container. O limite efetivo comprovado é agregado no serviço systemd (1 CPU/2 GiB); o dispatcher global limita a um worker ativo entre quadros.
- Testcontainers 2.0.5 executou em terminal Hermes dentro dos perfis Backend e QA. `postgres:16-alpine` respondeu à consulta real `SELECT current_setting('server_version_num')::int / 10000, 6 * 7`, resultado `16|42`; nenhuma porta foi publicada (`PUBLISHED_DATABASE_PORTS=NONE`); fechamento via `try-with-resources` passou. Ryuk foi desativado somente nesses smokes; não é prova de Ryuk.
- Aviso restante no harness: SLF4J não encontrou binding e usou logger NOP; o smoke terminou com exit code 0 e os asserts acima. Não houve chamada à API-Football ou teste da aplicação.
- Egress de rede dos containers está habilitado para dependências e documentação e não foi restringido por allowlist de domínios. Não alegar egress filtrado. Browser/vision estão desabilitados e precisam de validação própria antes de tarefas que dependam de navegação visual.
- Na leitura final antes da transição administrativa, o quadro tinha 30 cartões `blocked`, zero `ready`/`running` e zero runs. `kanban.max_in_progress=1` é o limite global entre quadros; `max_in_progress_per_profile=1`; dispatcher in-gateway ativo. A allowlist foi carregada preservando os dois perfis spatial e acrescentando os quatro TREVO. Os 29 sucessores e o gate T4F-000 tiveram os corpos atualizados e foram relidos; todos permanecem blocked aguardando conclusão do gate.

Resultado desta rodada histórica: verificações técnicas PASS; identidade aguardava decisão. A decisão posterior abaixo resolve esse bloqueio, sem reclassificar UID 0 como UID não-zero.

## Liberação sob decisão delegada — 24/10/2026

O usuário delegou a escolha ao coordenador. Foi mantido o runtime rootless, com exceção de UID interno restrita ao desenvolvimento local. Revalidação no perfil Backend, sessão `20261024_195105_ee88bd`: resultados brutos de terminal/read_file relidos, exit code 0; workspace RW, `.git` RO, caminhos privados ocultos e Testcontainers/PostgreSQL 16 (`16|42`) passaram. As configurações dos quatro perfis, autenticação, serviço, concorrência e os 30 cartões também foram relidos; nenhum worker de implementação havia iniciado nessa verificação.

T4F-001–027 podem avançar em sequência após conclusão administrativa de T4F-000. T4F-028 (credencial e orçamento externos) e T4F-029 (decisão humana/produção) continuam bloqueados. Sem novas permissões de host, alteração de perfis Spatial, publicação, gastos adicionais ou operações financeiras. Limitações anteriores permanecem. Ver [decisão e escopo](development-authorization.md) e o Kanban para estado de execução, sem confundir liberação com aplicação concluída.
