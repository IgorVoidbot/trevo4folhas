# BACKEND_AGENT — Backend Java e integridade transacional

## Missão e referências

Entregar APIs e regras de negócio do TREVO4FOLHAS com Java/Spring Boot, mantendo os valores e estados autoritativos no servidor. Ler [PRD](../PRD.md), [SPRINTS](../SPRINTS.md), [AGENTS.md](../../AGENTS.md), o [perfil backend existente](backend.md) e os AGENTS.md do caminho.

Este perfil complementa backend.md; não o substitui, não inicia processos nem autoriza migração de stack. Divergências devem ser resolvidas com base nas instruções vigentes e registradas, sem escolher silenciosamente a regra mais permissiva.

## Escopo

`src/main/java/com/trevo4folhas/**`, respectivos testes, contratos HTTP e dependências Java. SQL/migrations exigem [DBA](database.md); autenticação e PII exigem [Segurança](security.md); pom/build/infra exigem [DevOps](devops.md).

## Contratos obrigatórios

- Controller trata HTTP/DTOs; Service aplica regras e transações; Repository concentra persistência. Injeção por construtor e Bean Validation. Código em inglês, mensagens pt-BR.
- Reutilizar entidades e serviços presentes. BaseEntity, colunas ticket_code/match_date, precisão numeric e migrations V1–V8 não devem ser recriados nem alterados retroativamente.
- Usar BigDecimal de string, produto completo e HALF_UP apenas no retorno monetário final. Não aceitar odds finais, retorno, status ou operador do cliente.
- Emissão valida IDs/versões, relação seleção→mercado→partida, uma seleção por jogo, limites, horário, curadoria e frescor; ticket e snapshots são atômicos.
- Replay idempotente persistido é resolvido antes de revalidar novas condições. Chave vinculada à sessão; conteúdo divergente retorna 409, outra sessão não recupera PII.
- Confirmação só ocorre para PENDING válido, com declaração Pix e operador da sessão. Após adquirir locks, revalidar relógio e disponibilidade; preservar preço histórico.
- Seguir hierarquia de locks do PRD §8.4. Nenhum HTTP externo ou atendimento humano dentro da transação.
- Expiração independe da pontualidade do job. Cancelamento de confirmado/estorno/liquidação não pertencem ao MVP.
- Adapter executa fora das transações, com allowlists, quotas, paginação, timestamps e estado parcial seguro. Sync não reabre suspensão manual nem renova preço por replay.
- Dashboard usa os eixos temporais e fórmulas FIN-01–06; não calcular lucro com volume menos retorno potencial.

## APIs e proteção

Paginação limitada, DTOs sem entidades JPA, códigos HTTP coerentes e erros seguros. Proteger todas as rotas administrativas no backend. Sessão anônima técnica permite CSRF/idempotência, sem criar conta do apostador. Não adicionar consulta pública por código/telefone. Segredos e PII ficam fora de logs; respostas sensíveis usam no-store.

## Testes e entrega

Cobrir cálculo/precisão, limites, snapshots, rollback, códigos únicos, idempotência simultânea, prazo exato, concorrência confirmação/cancelamento/expiração e sync/suspensão. Validar SQL no PostgreSQL 16 via Testcontainers e endpoints com MockMvc; testar também autorização, CSRF e FIN-01–06.

Executar `./mvnw spotless:check verify` quando houver alteração Java pertinente e ambiente disponível. Conferir relatório e cobertura de domínio/serviços ≥ 80%, sem confundir plugin configurado com limiar efetivamente aplicado. Não substituir banco por H2 para obter aprovação.

Handoff: requisito/tarefa, contrato HTTP/schema, comportamento, arquivos, testes e resultados, limitações e dependências para frontend. Chamadas reais ao fornecedor exigem condições do PRD e autorização vigente; não criar contas ou contratar planos.
