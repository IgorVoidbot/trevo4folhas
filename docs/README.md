# Documentação — TREVO4FOLHAS

A implementação continua no repositório Projeto-aposta. O produto é uma plataforma de pré-bilhetes de futebol, sem conta de cliente, com atendimento WhatsApp e confirmação administrativa após conferência manual do Pix.

## Documentos de referência

A página `/` exibe o catálogo público, revisão, emissão de pré-bilhetes pendentes e compartilhamento explícito pelo WhatsApp. O login administrativo permanece em `/admin/login`. Comportamento, testes e limites estão nas validações da [Sprint 2](planning/sprint-2-validation.md) e [Sprint 3](planning/sprint-3-validation.md); screenshots locais em `target/verification-sprint3/`. A fila, decisões e expiração da Sprint 4 estão disponíveis em `/admin/tickets`; veja a [validação operacional](planning/sprint-4-validation.md).

- [PRD](PRD.md): produto, arquitetura, UX, dados, requisitos, aceites, métricas e decisões pendentes.
- [Sprints](SPRINTS.md): backlog preservado, dependências, responsáveis e evidência de conclusão.
- [Auditoria atual](planning/project-audit-2026-10-31.md): implementação conferida, checks executados, lacunas e critérios dos checklists.
- [Fluxo VPS/local](DEVELOPMENT_WORKFLOW.md): regras de branches, revisão e passagem de trabalho; [estado histórico da VPS](PROJECT_STATE.md), preservado com contexto temporal.
- [Consolidação das branches](planning/branch-consolidation-2026-10-31.md): origem e preservação dos checkpoints antes de organizar as referências.
- [Arquitetura](agents/ARCHITECT_AGENT.md): coordenação técnica e contratos.
- [Backend](agents/BACKEND_AGENT.md): Java, APIs e integridade transacional.
- [Frontend](agents/FRONTEND_AGENT.md): UI/UX e fluxo sem cadastro.

## Instruções e histórico

### Pré-requisitos e checks

Use Java 17, Node.js 20 ou superior e Docker com Compose. Execute `npm ci` para instalar as ferramentas pelo lockfile; gere os assets com `npm run css:build` antes de empacotar. O formatter atual é incompatível com Java 25; configure `JAVA_HOME` para o JDK 17.

Checks locais: `npm run lint`, `npm run format:check`, `node src/frontend/tests/slip-state.test.mjs` e `./mvnw spotless:check verify`. A suíte Java usa PostgreSQL 16 real via Testcontainers e precisa acessar o Docker. Relatórios: `target/surefire-reports/` e `target/site/jacoco/`. Não há workflow de CI remoto implementado.

### Banco local em container

O serviço `db` do `docker-compose.yml` usa PostgreSQL 16 e o volume persistente `trevo4folhas_postgres_data`. O binding fica em `127.0.0.1`; `POSTGRES_PORT` permite escolher a porta do host e mantém 5432 como padrão quando ausente.

Na configuração local de 31/10/2026 foi escolhida a porta **5433**, pois 5432 já estava ocupada. O `.env` recebeu `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`. Usuário e senha locais foram definidos conforme solicitado pelo usuário; não foram adicionados a arquivos versionados. As demais entradas do `.env` foram preservadas.

Para iniciar somente o banco:

```sh
docker compose --env-file .env.example --env-file .env up -d --wait db
```

O arquivo de exemplo fornece placeholders para interpolar as variáveis do serviço `app`, mesmo quando só `db` é iniciado; o `.env` prevalece e fornece os valores reais do banco. Esse comando não inicia o serviço `app`. Para executar a aplicação no IntelliJ, mantenha o perfil `local` e a raiz do projeto como diretório de execução. Para executar `app` no Compose, preencha seus parâmetros reais antes; os placeholders não configuram a aplicação.

Reiniciar o container preserva os dados. Não remova o volume para corrigir erros de conexão. Alterar `POSTGRES_PASSWORD` no `.env` não troca a senha de um banco já inicializado.

Validação em 31/10/2026: Compose válido, container `trevo4folhas-db-1` saudável, autenticação TCP por senha aprovada, PostgreSQL 16 em UTC, oito migrations Flyway e seis tabelas de domínio. A aplicação foi iniciada temporariamente com perfil `local` e respondeu HTTP 200 em `/api/config`; depois foi encerrada. O banco permaneceu ativo. Integração esportiva e bootstrap administrativo foram desativados explicitamente nessa verificação. O pacote Maven passou; não foi reexecutada a suíte completa por esta alteração de ambiente.

### Chave da API no desenvolvimento local

Com o perfil `local`, a aplicação carrega o arquivo `.env` do diretório de execução (use a raiz do projeto). Adicione `API_FOOTBALL_API_KEY=sua_chave` nele, sem aspas e sem `export`. O arquivo é lido como propriedades Java, não como script de shell. Não publique esse arquivo. Variáveis de ambiente do processo têm prioridade sobre seus valores.

No IntelliJ, selecione `local` em **Active profiles** na configuração Spring Boot; se esse campo não estiver disponível, adicione `--spring.profiles.active=local` em **Program arguments**. No terminal, use `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` com Java 17. Reinicie a aplicação após alterar o `.env`.

Os demais parâmetros obrigatórios de banco e negócio continuam necessários. O carregamento da chave não habilita importação: integração e agendamento seguem desligados por padrão. A aplicação fora do perfil `local` continua recebendo os segredos pelo ambiente. O Compose mantém seu repasse explícito de variáveis; este carregamento local não altera o ambiente do container. Para usar outro arquivo local, defina `TREVO_ENV_FILE` com seu caminho.

### Primeiro acesso administrativo local

Em 31/10/2026, o primeiro operador local foi criado pelo `AdminBootstrapService`, após verificar que não havia operadores no banco. Uma senha aleatória foi fornecida apenas ao processo temporário de bootstrap; não foram adicionadas credenciais de bootstrap permanentes ao `.env`. O arquivo local `.env.admin-access`, ignorado pelo Git e com permissão `600`, contém os campos `ADMIN_USERNAME` e `ADMIN_PASSWORD` para o usuário consultar. Esse arquivo não é carregado automaticamente pela aplicação. O banco mantém somente o hash BCrypt. A senha não tem expiração automática nesta versão.

Foram verificados cadastro, hash BCrypt persistido, login com CSRF e acesso autenticado a `/admin/matches`. O processo temporário foi encerrado; o acesso já pode ser usado na aplicação iniciada pelo IntelliJ em `/admin/login`, sem reiniciar o banco. Nenhuma chamada esportiva foi realizada nessa operação.

O [AGENTS.md da raiz](../AGENTS.md) permanece no lugar; seu inventário foi atualizado na auditoria de 31/10/2026. Os novos perfis complementam [Backend](agents/backend.md), [Frontend](agents/frontend.md), [DBA](agents/database.md), [DevOps](agents/devops.md) e [Segurança](agents/security.md), que foram preservados.

A descrição antiga de repositório apenas documental foi superada pela existência de código; o inventário observado está no PRD §0.1. Histórico de execução e pesquisa permanecem em planning/ e research/. Não tratar resultados históricos como validação atual nem iniciar agentes/serviços por causa destes arquivos.

## Atualização de 30/10/2026

PRD 1.3 centralizado em docs/, backlog extraído sem renumerar tarefas, perfis solicitados adicionados e PRD.md da raiz convertido em encaminhamento. Nenhuma funcionalidade da aplicação foi modificada por essa organização documental.

### Emissão e WhatsApp no desenvolvimento local

Configure `BANK_WHATSAPP_NUMBER` no `.env` com número internacional somente dígitos; reinicie a aplicação no IntelliJ após atualizar o código/configuração. Em `/`, escolha palpites disponíveis, informe valor, nome e telefone, leia e aceite as declarações, clique em **Revisar cupom** e depois **Gerar pré-bilhete**. Código, prazo e resumo aparecem somente após persistência. Use **Enviar para o WhatsApp** por decisão própria; confira e envie a mensagem no aplicativo. Clique ou geração nunca confirmam o Pix.

Em falha de conexão, mantenha a aba aberta e use **Tentar novamente**: a mesma chave/corpo recupera o pedido sem duplicação. Mudança de oferta exige atualizar o catálogo, escolher novamente e revisar. Não há consulta pública por código/telefone; se a sessão original foi perdida, consulte a banca. Os dados pessoais não ficam em storage do navegador.

Limite local padrão: `TICKET_RATE_LIMIT_MAX_ATTEMPTS=10` por `TICKET_RATE_LIMIT_WINDOW_SECONDS=60`, por endereço remoto e sessão, em memória do processo; configuração experimental de desenvolvimento, sem homologação de capacidade/abuso em produção. A sync esportiva segue desligada e emissão não consulta o fornecedor.

Validação de navegador: após gerar CSS e `./mvnw spotless:check verify`, execute `python3 src/frontend/tests/run-ticket-issuance-browser.py`. Requer Docker, Java 17 em `/usr/lib/jvm/java-17-openjdk-amd64`, Chrome em `/usr/bin/google-chrome` e Playwright importável (ou caminho em `PLAYWRIGHT_MODULE`). O runner cria banco/app temporários, operador e ofertas fictícias, usa contato fictício, bloqueia a navegação externa do WhatsApp e encerra os processos. Não usa as credenciais administrativas locais. Relatórios/screenshots ficam em `target/verification-sprint2/` e `target/verification-sprint3/`, sem versionamento.

### Operação administrativa — Sprint 4

Reinicie a aplicação no IntelliJ com o perfil `local` para carregar as novas rotas e o job. Entre em `/admin/login` e abra **Gerenciar pré-bilhetes** na administração, ou acesse `/admin/tickets`. A fila oferece código exato (normaliza caixa/espaços nas extremidades), situação, período de emissão e paginação. As datas do filtro usam o fuso da banca, inclusive quando o dispositivo está em outro fuso.

Abra **Ver pré-bilhete** para conferir identificação, snapshots e valores. Use **Conferir Pix e confirmar** somente depois de verificar o recebimento integral no banco; marque a declaração e registre a decisão. Cancelamento exige motivo e só se aplica a pendente ainda válido. Ambas as ações permitem observação operacional opcional. Operador e horário vêm do servidor. Mudanças de odds não recalculam o bilhete emitido; ofertas precisam continuar disponíveis, curadas e frescas para confirmar.

Se outro operador decidir primeiro, a tela recebe conflito e estado atual. Em falha de conexão, preserve os campos, feche o diálogo e use **Atualizar detalhe** para consultar o resultado antes de nova decisão. Não há repetição automática. Bilhetes vencidos não podem ser reativados; pagamento após vencimento exige atendimento manual fora do sistema. Partidas canceladas aparecem em alerta e não alteram automaticamente bilhetes já confirmados.

A expiração roda ao iniciar e a cada minuto, em lotes limitados; consultas e comandos já tratam vencidos como expirados antes do job. O controle `trevo.tickets.expiration.enabled=false` é usado somente para isolar os testes Java; o job fica habilitado por padrão na aplicação. Não há migration nova nesta sprint: o schema V1–V9 já possui os campos e índices necessários. Nenhuma operação consulta o fornecedor.

Validação completa de navegador e reinício: depois do build com Java 17, execute `PLAYWRIGHT_MODULE=/caminho/para/playwright/index.mjs python3 src/frontend/tests/run-ticket-operations-browser.py`. Usa Docker/PostgreSQL 16, Chrome e os mesmos requisitos do runner da Sprint 3. O runner cria credenciais e catálogo fictícios, eleva o limite de logins apenas no processo descartável para executar vários cenários, intercepta o WhatsApp e reinicia o app mantendo somente o banco de teste. Artefatos locais: `target/verification-sprint4/`. Não usa o banco ou credenciais administrativas locais.

### Indicadores e preparação operacional — Sprint 5 em andamento

Recarregue o Maven no IntelliJ e reinicie a aplicação com perfil `local` após atualizar as dependências. Em **Administração → Indicadores operacionais** (`/admin/metrics`), confira os dois eixos: emitidos por data de emissão e volume por horário de confirmação. Conversões da coorte ficam sem percentual enquanto houver validade aberta; sem base/média, a tela mostra “—”. Volume confirmado não é lucro, saldo ou Pix conciliado automaticamente.

O health público `/actuator/health` retorna somente situação; `/actuator/metrics/**` exige operador ativo. Rotação de senha e desativação agora invalidam sessões antigas na próxima requisição administrativa. Procedimentos de manutenção, backup/restauração, retenção e templates de TLS/roles/cron estão no [runbook](planning/operations-runbook.md); nenhum foi aplicado em hospedagem.

CSS de build agora é minificado. `verify` exige cobertura de linhas ≥80% por classe de serviço e regras de negócio selecionadas; o relatório completo permanece disponível. O pool de banco conserva o padrão de dez conexões, com ajuste opcional `DB_POOL_MAX_SIZE`, que precisa ser medido conforme a capacidade do ambiente.

A [validação da Sprint 5](planning/sprint-5-validation.md) registra 209 testes Java, regressões no navegador, fluxo único ponta a ponta com WhatsApp simulado, imagem não root, papel de runtime não superusuário, persistência e recuperação. A carga de cinquenta usuários **ainda não atende p95 <200 ms**. LCP é somente experimental; Android/iOS, WhatsApp Web reais, cenário móvel acordado, fornecedor/licença, hospedagem e piloto continuam pendentes. A Sprint 5 não está concluída.
