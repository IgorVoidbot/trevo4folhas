# Operação, privacidade e recuperação — preparação da Sprint 5

Este procedimento é preparação técnica, sem publicação em hospedagem. Responsável/contatos, domínio, segredos, licença/fornecedor, thresholds e política final de retenção ainda exigem as decisões DEC-01–07 e US-12. Use somente dados fictícios até essas liberações. Não compartilhar `.env`, sessões, dumps ou credenciais por Git/PR.

## Implantação e reinício

1. Revisar a versão e os relatórios de validação; gerar CSS minificado com lockfile e executar os checks Java 17/PostgreSQL 16. O Dockerfile compila assets e JAR e usa runtime não root; Node/Maven não devem estar na imagem final.
2. Preparar segredos no mecanismo da hospedagem, separados por ambiente. O `.env` local é um arquivo de propriedades Java, não deve ser executado como shell nem copiado para a imagem. Bootstrap usa senha aleatória controlada, é desativado depois do primeiro operador e nunca deve criar credenciais padrão.
3. Separar owner/migrations, runtime e backup. O usuário `postgres` do desenvolvimento não é uma escolha de runtime de produção. Revisar `deploy/runtime-role.sql.example`, aplicar migrations com owner e iniciar runtime com `spring.flyway.enabled=false`, após validar o schema. Hibernate permanece `ddl-auto=validate`.
4. O manifesto canônico é `docker-compose.yml`, com `app`, `db` e volume persistente. Antes de produção, remover publicação de porta do banco e manter aplicação somente no loopback/rede privada atrás do proxy. Não executar `down -v`, `reset` ou exclusão de volume como rotina.
5. Revisar `deploy/nginx.conf.example` com domínio e certificados reais; validar configuração e renovação antes de ativar. Ainda não foi configurado HTTPS em hospedagem. Se o proxy estiver no loopback e controlar os headers, usar `server.forward-headers-strategy=native` e limitar `server.tomcat.remoteip.internal-proxies` ao endereço efetivo desse proxy. Nunca confiar em `*` ou em cabeçalhos enviados diretamente por clientes. O proxy deve substituir `X-Forwarded-*`, não concatenar valores não confiáveis.
6. Health público: `GET /actuator/health`, somente `status`, sem detalhes. Métricas técnicas `/actuator/metrics/**` exigem sessão administrativa ativa; env/configprops/heapdump/logfile não são expostos. CSP, no-sniff, no-referrer, CSRF e cookies HttpOnly/SameSite=Lax permanecem ativos; cookie Secure é padrão, relaxado somente no perfil local HTTP.
7. Reiniciar a versão já validada com `docker compose restart app`. Validar health e login com conta autorizada, sem registrar a senha. Expiração recupera pendentes vencidos ao iniciar; consultas/comandos já respeitam o prazo mesmo antes do job. Monitorar falhas de expiração, disponibilidade e backups sem copiar dados pessoais aos logs.

O JAR usa `project.build.outputTimestamp` fixo, e o Dockerfile define `SOURCE_DATE_EPOCH` técnico. Eles não são datas de emissão/aceite. Para exportação normalizada com BuildKit compatível, usar `docker buildx build --provenance=false --output=type=docker,rewrite-timestamp=true -t trevo4folhas-app:sprint5 .`. A [referência Docker](https://docs.docker.com/build/ci/github-actions/reproducible-builds/) explica o controle de timestamps; qualquer afirmação de igualdade binária deve acompanhar hashes e as condições efetivamente comparadas.

## Trocar senha ou desativar operador

Existe um modo explícito de manutenção de uma execução, sem servidor HTTP: `java -jar target/trevo4folhas.jar --operator-maintenance`. Use exatamente o JAR da versão instalada e conexão de manutenção autorizada, nunca o runtime de privilégio mínimo. A configuração do banco vem do ambiente; migrações são verificadas como no startup e não devem ser usadas para atualizar versão por acidente.

- `TREVO_OPERATOR_ACTION=reset-password`, `TREVO_OPERATOR_USERNAME` e `TREVO_OPERATOR_PASSWORD`: nova senha de ao menos 12 caracteres e até 72 bytes UTF-8, codificada com BCrypt da aplicação e salt aleatório. Em terminal com console, a leitura é oculta; sem console, recebe segredo pelo ambiente temporário.
- `TREVO_OPERATOR_ACTION=disable` e `TREVO_OPERATOR_USERNAME`: desativa a conta, preservando FK e histórico. Não excluir operador nem reativar por conveniência; reset de senha não reativa conta desativada.
- Não colocar senha em argv, arquivos versionados, histórico ou SQL. Usar entrada oculta/secret manager, sem `set -x`. Apagar variáveis temporárias depois. Exemplo com container existente: `docker compose exec -T -e TREVO_OPERATOR_ACTION -e TREVO_OPERATOR_USERNAME -e TREVO_OPERATOR_PASSWORD app java -jar /app/trevo4folhas.jar --operator-maintenance` (variáveis já fornecidas com segurança no processo chamador).
- Sessões antigas são recusadas na próxima requisição administrativa após desativação ou rotação de hash. Não há endpoint web de reset/cadastro público. Se perder o acesso administrativo, usar o procedimento de manutenção autorizado, sem criar senha padrão.

## Backup e restauração

Volume não é backup. Meta proposta do PRD: backup diário, sete dias de retenção, RPO ≤24 h e RTO ≤4 h; essas metas dependem de agendamento, monitoramento, armazenamento e ensaio na hospedagem. Tempos de banco descartável não comprovam RTO de incidente real.

- Backup privado: `python3 deploy/database-maintenance.py --container CONTAINER --user BACKUP_ROLE --database DATABASE backup --directory /diretorio/privado --prune`. Exige PostgreSQL 16, diretório 700 e cria dump custom comprimido em modo 600. Remove apenas arquivos próprios `trevo-*.dump` com mais de sete dias e somente com `--prune`. Falha remove arquivo parcial; não imprime dados/segredos.
- Revisar `deploy/backup.cron.example`; não foi instalado em nenhum host. O responsável deve confirmar diariamente sucesso/idade, monitorar falhas e copiar o backup para armazenamento externo criptografado aprovado. Modo 600 não é criptografia. O dump contém PII e hashes de operadores: controlar acesso, chaves, retenção e incidentes.
- Restaurar **somente** em PostgreSQL 16 vazio/isolado: `python3 deploy/database-maintenance.py --container DESTINO_VAZIO --user RESTORE_OWNER --database DATABASE restore --file /backup/privado.dump`. Destino com tabelas é recusado; não usa `--clean`. A restauração usa transação e configura UTC. Conferir seis tabelas, histórico Flyway, checksums, health e fluxos com credenciais autorizadas antes de qualquer troca de tráfego.
- Recuperação: isolar a falha e suspender decisões se a integridade for incerta; selecionar último dump íntegro, restaurar em destino novo, conferir dados/versão, aplicar somente migrations compatíveis, validar a aplicação e então solicitar autorização para troca do serviço. Registrar período potencialmente perdido e conciliar atendimento/Pix manual fora do sistema. Responsável e contato precisam constar no plano real de hospedagem.
- Rollback: nesta sprint não há DDL novo, mas rotação de senha, anonimização e decisões são mudanças persistidas. Não restaurar dump sobre banco ativo nem reverter dinheiro/decisão para fazer downgrade. Um JAR anterior à Sprint 5 perde revogação imediata de sessão e indicadores; não tratá-lo como rollback equivalente de segurança.

## Retenção e anonimização

Noventa dias é premissa do PRD, não política legal aprovada. O responsável deve revisar necessidade de preservação, incidentes e pendências operacionais antes de qualquer execução real, inclusive de confirmados. Sessões e logs não substituem essa revisão.

1. Preparar arquivo privado (600) com até mil UUIDs distintos, explicitamente revisados. Não usar nome, telefone ou código em parâmetros/arquivos de apoio.
2. Simular: `python3 deploy/database-maintenance.py --container CONTAINER --user OWNER --database DATABASE anonymize --ticket-ids-file /lista/privada --reviewed-retention`. Sem `--apply`, a transação é revertida e devolve somente contagem.
3. Após a revisão, repetir com `--apply`. Todos os UUIDs precisam existir, ter mais de 90 dias e estar decididos/expirados; lista parcialmente inelegível é recusada integralmente. Troca nome/telefone por rótulos de remoção, remove nota livre e anonimiza motivo de cancelamento, preservando a obrigatoriedade desse motivo. Incrementa versão/auditoria e é idempotente.
4. Preserva códigos, valores, estados, responsável e snapshots. Dados em dumps antigos permanecem até rotação; documentar prazo de expurgo e acesso às cópias. Restauração de dump antigo pode reintroduzir PII: reaplicar a lista de retenção aprovada antes de liberar o serviço.

## Piloto e passagem de trabalho

Treinar operadores para conferir Pix fora do sistema, procurar o registro original, comparar snapshots, respeitar prazo/estado, resolver conflito pela consulta e tratar partida cancelada/pagamento tardio manualmente. Não confirmar por mensagem, clique ou comprovante isolado.

Ainda são necessários: Android/iOS e WhatsApp Web reais, teste com dez participantes, treinamento efetivo, proposta/decisão do fornecedor e sua licença/cobertura, política de frescor/retencão, domínio/segredos/contatos e duas semanas de acompanhamento. Registrar datas, responsáveis, evidência e problemas; não substituir pessoas por usuários simulados nem chamar a preparação técnica de piloto concluído.
