# API-Football — primeira evidência autenticada

Data: 31/10/2026, 19:50–19:52 UTC. Referências: S1.06, RF-21–24 e gate T4F-028. O usuário informou que a chave inserida no `.env`, com plano gratuito de 100 requisições/dia, seria utilizada nos primeiros testes. A execução ficou limitada a **cinco consultas**, sem retentativas, com reserva conservadora de dez unidades no teto de cem. Nenhuma automação foi habilitada.

## Resultados observados

| Consulta | Resultado autenticado |
| --- | --- |
| `GET /status` | HTTP 200, plano `Free`, assinatura ativa, consumo inicial informado `0`, limite diário informado `100` |
| `GET /leagues?country=Brazil` | HTTP 200, 109 ligas, página única; Série A identificada por ID 71, com temporadas anunciadas de 2010 a 2026 |
| `GET /fixtures?league=71&season=2024` | HTTP 200, 380 partidas, página única, todas com status `FT` |
| `GET /odds?league=71&season=2024&page=1` | HTTP 200 sem erro no envelope, lista vazia, página única |
| `GET /fixtures?league=71&season=2026` | HTTP 200 com erro de plano no envelope; mensagem do fornecedor indica intervalo gratuito entre 2022 e 2024 |

O endpoint de ligas anunciar uma temporada não prova permissão da assinatura para consultar suas partidas. Uma resposta HTTP 200 também não prova sucesso: o erro no envelope da quinta consulta foi reconhecido como restrição de plano. A resposta vazia de odds não comprova cobertura, preço, semântica de mercados ou paridade. O resultado deste lote sustenta somente autenticação e disponibilidade dos dados históricos consultados.

## Proteção e contabilização

- A chave foi lida localmente e transmitida exclusivamente no header `x-apisports-key` ao host HTTPS fixo `v3.football.api-sports.io`. Não foi copiada para código, argumentos, relatórios ou logs.
- Não foram seguidos redirecionamentos. Cada consulta teve reserva persistida antes do transporte, timeout de 15 segundos e limite de corpo de 2 MiB.
- Registro protegido fora do repositório: `~/.local/state/trevo4folhas/api-preflight/state.json`, diretório com permissão 700, arquivos com permissão 600 e lock de execução. O lote não se renova automaticamente, e uma ação já tentada não é repetida automaticamente.
- Cinco reservas conservadoras foram registradas. O saldo local conservador ao encerrar foi 95; não é afirmação sobre débito exato do fornecedor. A documentação informa que `/status` não consome quota diária, mas esta rodada o reservou conservadoramente. Headers diários não forneceram valor utilizável na quinta consulta.
- O corpo bruto de `/status` não foi persistido, pois contém dados pessoais da conta. Somente plano, atividade e contadores foram extraídos. Evidências esportivas ficaram em arquivos locais protegidos no mesmo diretório; não foram publicadas.
- A chave e as opções de sincronização no `.env` não foram alteradas. O ledger desta descoberta deverá ser reconciliado com o `QuotaStore` antes de habilitar importação; não iniciar um contador de produção ignorando estas consultas. Não foi configurado reset automático por suposição.

## Alcance e próximo aceite

Esta rodada foi um diagnóstico HTTP autenticado executado por um harness local, **não uma execução do adapter Java nem uma importação no catálogo**. Não houve alteração do banco, aprovação de curadoria, emissão ou confirmação de bilhetes.

A primeira amostra autenticada deixa de ser uma pendência genérica de credencial, mas S1.06 continua parcial: falta executar e validar o adapter com dados reais, obter amostra não vazia de odds, conferir bookmakers, identidades, regras e mapeamentos e comprovar sua importação. O plano gratuito consultado limita o teste de partidas atuais; dados de 2024 encerrados não servem como ofertas elegíveis para emissão. Os testes locais simulados continuam úteis e devem permanecer rotulados. Nenhuma decisão de contratação ou produção foi tomada.

Fontes oficiais consultadas nesta rodada: [documentação da API](https://www.api-football.com/documentation_v3), recuperada parcialmente por busca, e [guia de ligas e temporadas](https://www.api-football.com/news/post/how-to-get-all-data-from-a-league-with-api-football). A documentação completa não teve conteúdo legível na extração direta. As observações da tabela são evidência das respostas autenticadas, não inferências de publicidade ou garantia de paridade.
