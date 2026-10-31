# Catálogo manual de demonstração

Data: 31/10/2026. Continuidade autorizada com dados manuais de teste, após a verificação das limitações do plano Free da API-Football.

## Dados cadastrados

Competição: **TESTE — Campeonato fictício**. Todas as equipes, partidas e cotações foram inventadas para desenvolvimento; nenhuma cotação foi copiada de uma casa nem apresentada como amostra do fornecedor.

- Equipe fictícia Verde × Equipe fictícia Azul.
- Equipe fictícia Sol × Equipe fictícia Lua.
- Equipe fictícia Norte × Equipe fictícia Sul.

Os inícios são às 15h de `America/Sao_Paulo`, nos três dias seguintes ao cadastro inicial. Cada partida possui três mercados, totalizando **9 mercados e 21 seleções**:

| Mercado | Seleções e odds fictícias |
| --- | --- |
| Resultado da partida (`1X2`) | Mandante 1,80; empate 3,20; visitante 4,50 |
| Total de gols (`TOTALS`), linha 2,5 | Mais de 2,5: 2,50; menos de 2,5: 1,55 |
| Ambas marcam (`BTTS`) | Sim 1,90; não 1,85 |

Mercados usam período `FT`, regra do tempo regulamentar com acréscimos e origem `MANUAL`. A regra textual de cada mercado informa que são dados fictícios. Os registros de teste foram aprovados pelo operador local para exercitar a elegibilidade. Isso não comprova a matriz completa de mercados nem homologação de fornecedor.

## Configuração e repetição

O `.env` local recebeu `MANUAL_PRICE_MAX_AGE_SECONDS`, `MANUAL_SCHEDULE_MAX_AGE_SECONDS` e `MANUAL_STATUS_MAX_AGE_SECONDS` com valor explícito `86400`, conforme a política experimental DEV já registrada. Demais configurações, incluindo a chave externa, foram preservadas. A aplicação do IntelliJ deve ser reiniciada para carregar o novo prazo. A política não renova dados pelo acesso e não define tolerância de produção.

O cadastro usa exclusivamente as APIs administrativas autenticadas com sessão, CSRF e versões. Não insere SQL diretamente, não cria operador e não recebe credenciais por argumento. Para repetir contra a aplicação local:

```sh
python3 deploy/seed-local-catalog.py --base-url http://127.0.0.1:8081
```

O script usa o arquivo local ignorado `.env.admin-access`, aceita somente destino HTTP em `127.0.0.1`, preserva registros existentes e não reabre partidas bloqueadas. Repetição identifica as partidas de teste por competição/equipes, seus mercados e seleções; não sobrescreve odds ou curadoria já modificadas pelo usuário. Se os dados existentes estiverem indisponíveis ou ambíguos, exige revisão em vez de restaurá-los silenciosamente.

Depois de 24 horas sem revisão manual, as ofertas deixam de ser elegíveis. Reexecutar o script não renova seu frescor. Revise explicitamente os registros no painel quando necessário.

## Verificação

Na primeira execução foram criadas 3 partidas, 9 mercados e 21 seleções. Na segunda, foram criados zero registros e as 3 partidas continuaram elegíveis. O catálogo público foi consultado em uma instância temporária do perfil `local`, conectada ao PostgreSQL 16 do projeto. Essa instância foi encerrada após a verificação e os dados foram preservados no banco.

A verificação final consultou os detalhes públicos e confirmou 3 partidas, 9 mercados e 21 seleções elegíveis, odds serializadas como strings e versões positivas. Duas repetições adicionais preservaram os mesmos totais sem criar registros. `git diff --check` passou. Não houve alteração de código Java nem reexecução da suíte Java completa nesta operação de dados locais.

Nenhuma requisição à API-Football, emissão de bilhete, pagamento ou operação de confirmação foi feita neste cadastro. A interface pública de seleção e cupom foi posteriormente entregue na [Sprint 2](sprint-2-validation.md), usando estas ofertas como demonstração. S1.06 permanece parcial pelos motivos registrados no diagnóstico autenticado.
