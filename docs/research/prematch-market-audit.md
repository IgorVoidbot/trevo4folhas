# Levantamento de mercados pré-jogo — Picos da Sorte

Data: 24/10/2026. Versão: 0.1 — catálogo observado e avaliação documental, não homologação de fornecedor.

## Decisão do cliente e limites deste trabalho

O cliente exige os mesmos mercados pré-jogo da referência Picos da Sorte. Não foi autorizada uma redução para 1X2 ou a exclusão de escanteios, jogadores e combinações. Ao vivo fica para uma etapa futura. Este levantamento não altera o PRD, instruções, código ou modelo de negócio; registra a divergência entre o requisito informado e a especificação atual.

O recorte pesquisado é futebol, conforme o projeto. A referência também mostra outros esportes, mas não há decisão explícita de incluí-los no TREVO4FOLHAS. Login de apostador, carteira, pagamento integrado e consulta pública de bilhetes não foram incorporados por associação com o site. Não foi identificado o fornecedor da referência.[1][2]

## Resumo verificado

- Foram inspecionados os detalhes públicos de **30 partidas de 28 competições**: 22 partidas da listagem de 24/10/2026 e oito da listagem de 25/10/2026. Capturas entre 14h24 e 14h26, UTC−03:00.[1][2]
- A união contém **60 IDs locais de categoria**, com **58 rótulos distintos**, agrupados provisoriamente em **28 famílias semânticas**. Não são 60 mercados comerciais universalmente equivalentes.[1][2]
- Foram preservadas **7.784 linhas de seleção por partida**, incluindo **547 linhas sem cotação textual**. Não são 7.784 tipos de aposta nem 7.784 ofertas aceitas pelo backend.[1][2]
- O badge `+950` não corresponde ao número de categorias extraídas. Não foi usado para inferir total de mercados, seleções ou cobertura.[1]
- Nenhum fornecedor foi testado com credenciais; **não há paridade comprovada, preço cotado ou contratação recomendada como definitiva**.

## Método e rastreabilidade

Navegação pública, sem cadastro, login, emissão de bilhete ou pagamento. A coleta abriu os links de mercados adicionais de forma sequencial e aguardou a resposta assíncrona. Antes de salvar, verificou que as referências das seleções pertenciam à partida solicitada. Foram lidas todas as linhas da tabela carregada, inclusive sem odds. Em uma repetição de captura, foi mantida a última captura completa por `fixture_id`.

A amostra foi intencionalmente variada: Séries A/B/C, estaduais, futebol feminino e de base, ligas europeias, Argentina, MLS, Austrália, China, Coreia do Sul e reservas. O detalhe por partida está no CSV de amostras. Não houve varredura de todos os eventos, dias ou esportes, nem prova de saturação do catálogo. **O inventário é completo para as tabelas capturadas, não para todo o site.** Novas categorias devem ampliar a matriz antes de fechar o aceite.[1][2]

### Arquivos de evidência

| Arquivo em `data/` | Conteúdo |
| --- | --- |
| `picos-prematch-market-catalog.csv` | 60 categorias: ID e nome originais, família proposta, ocorrências e exemplos literais. |
| `picos-prematch-selections.csv` | 7.784 linhas: partida, categoria, seleção, odd exibida, sinal `data-off`, referência local e horário da coleta. |
| `picos-prematch-samples.csv` | 30 partidas, competições, datas da listagem e detalhe, badges e contagens. |
| `picos-prematch-evidence.json` | Capturas estruturadas usadas para gerar os três CSVs; sem cookies ou credenciais. |
| `prematch-provider-coverage-matrix.csv` | Uma linha por categoria, com os cinco produtos/canais avaliados, evidência e pendências. |
| `prematch-provider-source-manifest.json` | URLs, forma de obtenção e limitações das fontes dos fornecedores; não inclui cópia integral dos catálogos de terceiros. |

Os IDs da referência e de cada fornecedor pertencem a namespaces distintos. Um número coincidente não é um mapeamento. `proposed_family` é classificação deste estudo, não campo existente na aplicação.

## Catálogo observado: o que precisa entrar na avaliação

A lista nominal completa está em `data/picos-prematch-market-catalog.csv`; todas as variantes extraídas estão em `data/picos-prematch-selections.csv`.[1][2]

| Conjunto exigido | Variantes efetivamente observadas | IDs locais de categoria |
| --- | --- | --- |
| Resultado e proteção | 1X2 no jogo/tempos, dupla chance no jogo/tempos, empate anula e intervalo/final | 15, 46, 62, 316, 16, 48, 321, 17, 14 |
| Totais de gols | Jogo, por equipe e por tempo; contagem exata por jogo/tempo | 11, 9, 10, 69, 63, 61, 108, 109 |
| Ambas marcam | Jogo, por tempo e combinação entre os dois tempos | 6, 4, 164, 306 |
| Par/ímpar | Jogo, tempos e gols de cada equipe | 12, 55, 74, 165, 166 |
| Placar e margem | Placar exato do jogo/tempos e margem de vitória | 13, 59, 75, 57 |
| Handicaps | Asiático e grupo de handicap de gol com seleções por período | 8, 20, 329 |
| Combinações pré-definidas | Resultado + gols, resultado + ambas marcam, gols + ambas marcam, primeiro gol + resultado | 18, 35, 19, 900, 317 |
| Dinâmica dos gols | Tempo com mais gols, por equipe, momento do primeiro gol em faixas de 10/15 min, primeiro gol do 2º tempo e último gol por equipe | 110, 111, 308, 72, 73, 318, 319, 328, 323 |
| Desempenho da equipe | Vencer sem sofrer, não sofrer gols, marcar nos dois tempos, vencer ambos os tempos e quais equipes marcam | 314, 315, 107, 65, 67, 64, 37 |
| Jogadores | Primeiro marcador, último marcador e marcar a qualquer momento | 325, 324, 327 |
| Escanteios | Acima/abaixo, quantidades exatas, faixas e par/ímpar | 91, 313, 85 |

Essa tabela organiza os 60 IDs sem descartar categorias semelhantes. Assistências, finalizações e cartões de jogador **não foram observados nesta amostra**; capacidade anunciada por fornecedor não os transforma automaticamente em requisito. A ausência na amostra também não prova ausência no site.

## Inconsistências da referência e pontos sem regra comprovada

1. **Datas conflitantes:** todas as 30 telas de detalhe mostraram `10/07/2026`, enquanto as listagens indicavam `24/10/2026` ou `25/10/2026`. O horário também divergia em exemplos. São datas anteriores/distintas da listagem, não um detalhe de fuso. Preservamos ambos os valores; não validamos a agenda real das partidas. Não copiar esse comportamento.[1][2]
2. **Categorias com nomes repetidos:** IDs 62/316 compartilham o rótulo de vencedor do 2º tempo; 110/111 compartilham um título, mas suas seleções tratam recortes diferentes. Os grupos 164/306 e 18/35 são parecidos, sem equivalência contratual demonstrada. Não fundir apenas pelo texto.[1]
3. **Categoria não é mercado normalizado:** o ID 4 reúne ambos os tempos; 20 mistura seleções com e sem indicação de 1º tempo; 91 reúne múltiplas linhas e formatos de escanteios. Uma categoria pode gerar várias instâncias de mercado.[1]
4. **Linha sem odd não é preço válido:** um campo vazio ou `data-off` isolado não comprova suspensão nem disponibilidade operacional. A coleta não tentou selecionar/apostar para descobrir a regra.[1][2]
5. **Regras ainda pendentes:** duração regulamentar, acréscimos, prorrogação, gols contra, jogador não participante, jogo interrompido/adiado, devolução de linha inteira/quarto e faixas superiores. Rótulos e odds não bastam para estabelecer liquidação.

Antes do contrato de paridade, o cliente deve aprovar essa referência fixa e as regras. A coleta serve para especificar e comparar, não para redistribuir cotações ou identidade visual da referência.

## Como ler a matriz de fornecedores

Os status são níveis de evidência, **não notas, percentuais de cobertura ou aprovação de compra**:

- `D`: família/mercado identificado em documentação pública; mapeamento proposto, sem prova por partida/linha.
- `H`: indicação em documento de atualização incerta ou exemplo histórico; requer revalidação atual.
- `P`: evidência parcial; não cobre comprovadamente todas as variantes da categoria.
- `E`: nome encontrado em exemplo histórico, mas sem seleções/odds nesse exemplo.
- `A`: anúncio genérico do produto; sem evidência específica para esta categoria.
- `N`: não comprovado na pesquisa; não significa que o fornecedor não ofereça.
- `X`: exclusão explícita na documentação do produto avaliado.

Toda linha permanece com `authenticated_sample_status=not_tested` e cobertura das competições-alvo não verificada. As células `D` da API-Football se baseiam em trechos indexados do guia oficial: a leitura integral foi bloqueada. Não comparar quantidade de células `D` como desempenho de fornecedores com documentação de granularidades diferentes.[3]

### Síntese por produto

| Produto exato a avaliar | Evidência útil | Limitação decisiva |
| --- | --- | --- |
| API-Football v3 — `/odds`, catálogo `/odds/bets`; contratação direta/RapidAPI a conferir | Guia cita resultado, ambas marcam, total e placar exato. | Faltou catálogo autenticado atual para escanteios, jogadores, períodos e combinações; não concluir ausência. O canal RapidAPI não foi validado nesta coleta.[3] |
| LSports TRADE/ARENA360 — distribuição PreMatch | PDF público tem coluna pré-jogo e famílias amplas, inclusive escanteios e marcadores. | HTTP `Last-Modified` do PDF: 16/04/2024; não é garantia de atualidade editorial. Confirmar provedor/competição/mercado no Coverage Hub e pacote proposto.[5][6][7] |
| Sportradar UOF — produtor pré-jogo Ctrl | Catálogo ligado no overview permite propor IDs de gols, tempos, placares, escanteios e marcadores. | Catálogo não garante linhas ou competições habilitadas na conta; propostas de ID continuam sujeitas à regra e variante.[11][12] |
| BetsAPI — Event Odds `/v2/event/odds` | Catálogo limitado documenta resultado, handicap, totais e algumas variantes de 1º tempo/escanteios. | Exclui explicitamente dupla chance e ambas marcam. Esse produto isolado não atende à exigência.[16] |
| BetsAPI — produto Bet365, `/v4/bet365/prematch` | Anuncia catálogo amplo; exemplo público contém diversos mercados e combinações. | O exemplo tem timestamp de 03/09/2020, não comprova o payload atual v4 e contém listas vazias. Exigir amostra autenticada recente.[17][18][19] |

### Verificações específicas antes de pedir preço

- **LSports:** tratar Odds Service como legado e pedir avaliação TRADE PreMatch. O PDF tem entradas atípicas na seção Football; validar somente equivalentes de futebol, não promover nomes de outros esportes ao escopo. As marcações históricas da matriz exigem revisão no pacote atual.[5][6][7]
- **Sportradar:** os IDs candidatos estão na coluna própria da matriz. Exemplo: mercado local de faixa de escanteios 313 tem candidato UOF 169, mas isso não valida automaticamente a mesma regra. DST e SportingRisk possuem catálogos pré-jogo específicos; alguns props DST incluem prorrogação e não equivalem automaticamente a 90 minutos. Complementos e condições comerciais precisam ser confirmados.[11][13][14]
- **BetsAPI:** `source=bet365` no Event Odds não elimina suas limitações. O produto Bet365 tem documentação separada e endpoint v4. Um nome presente com lista vazia foi marcado `E`, não como oferta de odd disponível.[16][18][19]
- **API-Football:** estatística de escanteio ou jogador não comprova mercado de aposta. A prova pendente é retorno atual de odds pré-jogo com mercado, seleção e preço, no pacote contratado.[3]

## Combinações: duas necessidades diferentes

Os grupos de resultado + gols, ambas marcam + resultado e similares foram observados como **seleções pré-definidas com cotação própria**. Isso não demonstra que o site permita qualquer combinação livre entre palpites da mesma partida.[1]

Não ampliar automaticamente para Bet Builder. Caso o cliente o exija, solicitar precificação de correlação como capacidade separada: LSports ComboBuilder recebe odds-base do operador; não fornece essas odds. Sportradar Custom Bet é um serviço separado e distingue habilitações pré-jogo/live, com restrições próprias de linhas.[9][15]

Até existir decisão e precificação adequada, preservar a regra atual de uma seleção por partida em cada bilhete. Não multiplicar cotações de seleções correlacionadas como se fossem independentes. Uma seleção composta já precificada pelo fornecedor pode representar um item, mediante contrato específico.

## Impacto arquitetural proposto — não implementado

| Contrato atual do projeto | Mudança a avaliar posteriormente |
| --- | --- |
| Cadastro manual; feed externo fora do MVP — `PRD.md:38–39` | Importação externa e gestão local de oferta, com curadoria e bloqueio pelo operador. |
| `Match` com três odds — `PRD.md:447` | Partida → instância de mercado → seleção, incluindo período, linha, participante, variante e namespace do fornecedor. |
| `TicketItem.selection` em HOME/DRAW/AWAY — `PRD.md:450` | Snapshot de seleção, mercado, período, linha, jogador/equipe, cotação e versão da oferta. |
| Quatro entidades de domínio — `PRD.md:371` | Revisar explicitamente o contrato antes de adicionar entidades de mercados; não disfarçar mudança de schema como simples integração. |
| Produto simples de odds — `PRD.md:104–110` | Manter para partidas distintas; seleção composta utiliza preço próprio, não composição ingênua de probabilidades correlacionadas. |
| Partida referenciada imutável — `PRD.md:175` | Importação de reagendamento gera bloqueio/pendência administrativa; nunca sobrescrever silenciosamente início e snapshots. |
| Sem filas por padrão — `AGENTS.md:9` | Escolher transporte após definir produto. TRADE e UOF documentam consumo RabbitMQ/AMQP, não apenas polling HTTP.[8][12] |

**Correção da hipótese anterior:** não é seguro prometer integração de qualquer fornecedor por um agendador HTTP simples e sem mensageria. Os feeds TRADE/UOF pesquisados introduzem um consumidor, recuperação, controle de ordem e disponibilidade da conexão. Isso não obriga a adotar microsserviços ou hospedar um broker próprio, mas exige revisão explícita da restrição atual antes da implementação.[8][12]

Como proposta, preservar o monólito, armazenar um catálogo normalizado no PostgreSQL e manter credenciais no backend. Distinguir estado externo da partida, disponibilidade do mercado e suspensão manual. Distinguir horário de consulta, sincronização bem-sucedida e atualização real da cotação. Uma importação não reabre oferta suspensa pelo operador.

A política de dados vencidos precisa abranger emissão e confirmação. Não foi inventado um prazo universal: deve derivar do contrato e do risco aceito. Snapshots existentes não são recalculados; confirmed/cancelled/expired permanecem sujeitos às regras atuais. Somente pré-jogo é aceito inicialmente. Ao vivo requer um projeto posterior com suspensão, latência, ordem e recuperação de eventos; um campo `is_live` não resolve isso.

A disponibilização dos mercados não implementa apuração de resultados/prêmios. Essa função permanece fora do MVP atual e exige requisito próprio; placar final sozinho não resolve todos os mercados de jogadores, períodos, linhas e escanteios.

## Roteiro de prova de cobertura e aceite

1. Aprovar com o cliente o catálogo capturado, tratamento das inconsistências, competições e temporadas. Confirmar se outros esportes entram; não presumir inclusão nem exclusão definitiva.
2. Enviar a matriz e o catálogo aos fornecedores, pedindo resposta por categoria e **competição × partida × período × linha × participante**, com pacote/produtor de origem, regras e condições de uso.
3. Obter ambiente de avaliação autorizado. Consultar partidas futuras reais nas competições acordadas, usando IDs próprios do fornecedor, sem pressupor equivalência com IDs da referência.
4. Registrar payloads permitidos pelo contrato, horários, seleções com preço, estados, paginação, limites e lacunas. Repetir em momentos diferentes antes do início para verificar abertura e atualização das ofertas.
5. Resolver todas as linhas `N/P/E/A` e revalidar `H`; para `D`, verificar variantes e disponibilidade atual. Categoria obrigatória sem prova permanece pendente/reprova a proposta, não desaparece do escopo.
6. Conferir licença para uso pretendido, publicação/armazenamento, competições e jogadores, custos base/excedentes, suporte, quotas e transporte. Nenhum desses itens foi aprovado por este documento.
7. Somente após a prova e aprovação do cliente, autorizar atualização do PRD, contratos, backlog e implementação. Não há motivo para contratar um pacote ao vivo agora só por uma possibilidade futura.

## Conclusão

A documentação encontrada torna **Sportradar UOF e LSports TRADE PreMatch candidatos para solicitar prova de cobertura**, não vencedores homologados.[5][11]

BetsAPI Bet365 merece uma avaliação recente, distinta do Event Odds limitado.[16][18]

A API-Football continua sem evidência suficiente de paridade nesta pesquisa; não foi descartada por suposta ausência de mercados.[3]

A decisão depende principalmente da cobertura real de ligas regionais, dos três mercados de marcadores, das variantes de escanteios e das combinações pré-definidas. **Nenhum fornecedor está comprovado como equivalente ao site inteiro.** A próxima ação única é submeter estes anexos aos fornecedores para uma demonstração de cobertura e proposta do pacote pré-jogo.

## Sources

Fontes oficiais consultadas em 24/10/2026. Nos itens 1–2, a evidência é a navegação pública preservada nos anexos. No item 3, foram consultados trechos indexados oficiais; a extração integral retornou bloqueio. No item 5, a data HTTP é antiga. No item 11, foi inspecionado também o PDF UOF Markets vinculado na página. O item 19 é um exemplo histórico, não resposta autenticada atual.

[1] https://picosdasorte.com — Picos da Sorte — catálogo público observado
[2] https://picosdasorte.com/amanha — Picos da Sorte — listagem pública de amanhã
[3] https://www.api-football.com/news/post/how-to-get-started-with-api-football-the-complete-beginners-guide — API-Football — beginner guide
[5] https://www.lsports.eu/wp-content/uploads/Market-Coverage-table.pdf — LSports — Market Coverage PDF
[6] https://docs.lsports.eu/u/legacy-products/migration — LSports — migration to TRADE
[7] https://docs.lsports.eu/u/coverage-hub/markets — LSports — Coverage Hub markets
[8] https://docs.lsports.eu/u/trade/integration — LSports — TRADE integration
[9] https://docs.lsports.eu/u/trade/combo-builder — LSports — ComboBuilder
[11] https://docs.sportradar.com/uof/introduction/market-overview-uof-and-cb — Sportradar — Market Overview UOF and CB
[12] https://docs.sportradar.com/uof/introduction/key-concepts/producers — Sportradar — Producers
[13] https://docs.sportradar.com/uof/introduction/market-overview-uof-and-cb/odds-by-sports/soccer/soccer-pre-match-player-markets-dst — Sportradar — Soccer Pre-Match Player Markets DST
[14] https://docs.sportradar.com/uof/introduction/market-overview-uof-and-cb/odds-by-sports/soccer/soccer-pre-match-player-markets-sportingrisk — Sportradar — Soccer Pre-Match Player Markets SportingRisk
[15] https://docs.sportradar.com/uof/custombet-cb/intro — Sportradar — Custom Bet intro
[16] https://betsapi.com/docs/events/odds.html — BetsAPI — Event Odds
[17] https://betsapi.com/docs/bet365 — BetsAPI — Bet365 Summary
[18] https://betsapi.com/docs/bet365/prematch.html — BetsAPI — Bet365 PreMatch Odds
[19] https://betsapi.com/docs/samples/bet365_prematch_odds.json — BetsAPI — historical public prematch sample
