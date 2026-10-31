# Sprint 2 — catálogo público e cupom

Data: 31/10/2026. Escopo: S2.01–S2.05, usando catálogo local manual explicitamente fictício. S1.06 continua parcial em homologação e não foi apresentada como concluída para iniciar este incremento local, conforme a dependência flexível registrada no plano.

## Comportamento entregue

A página `/` agora consulta `/api/config` e o catálogo público existente. Há filtros por data e nome completo de competição, paginação de partidas, abertura dos mercados, carregamento paginado de mercados/seleções e estados de carregamento, vazio, indisponibilidade e tentativa novamente. As informações exibem horário da banca, regra, período, linha, participante e odds em pt-BR. O navegador não consulta fornecedor externo.

O cupom fica em memória, indexado por `matchId`, com uma seleção por partida e limite retornado pela API, limitado a dez. Escolher outro mercado da mesma partida substitui o palpite. O rascunho mantém IDs internos e as três versões aceitas. Permite remover, limpar e normalizar valor com vírgula para string decimal. O cálculo de prévia usa inteiros `BigInt`, preservando o produto completo e arredondando somente o retorno final em centavos; o backend continuará autoritativo na Sprint 3.

No desktop, o cupom é lateral e sticky, com rolagem interna. No celular, uma barra inferior abre o painel modal com safe area, rolagem própria, foco inicial, contenção de Tab, Escape, botão de fechamento e retorno do foco. O fundo fica inerte durante a abertura. O espaço da barra é reservado no conteúdo.

A revisão valida seleção, valor/retorno máximo, nome, telefone com DDD e declarações de maioridade e leitura das regras/privacidade. O telefone recebe formatação visual ao perder foco e é normalizado em memória. Resumo e campos usam saída textual segura; não há PII em URLs, storage ou requisições de revisão. Recarregar limpa o rascunho. O texto de regras/privacidade é identificado como prévia e não substitui a aprovação comercial/legal prevista para produção.

Atualização manual, retorno à aba e revisão revalidam as ofertas na API local. Consultas simultâneas de revalidação são deduplicadas. Falha de consulta, indisponibilidade, alteração das versões/preço ou início da partida bloqueiam a revisão do item, preservando a cotação anterior. A nova cotação não aparece como aceita nem é adotada silenciosamente; exige nova escolha explícita ou remoção.

**Emissão permanece desabilitada.** O botão “Gerar pré-bilhete” informa a indisponibilidade. Nenhum código, ticket, WhatsApp ou pagamento é gerado nesta sprint. Não houve alteração de schema, contratos HTTP ou fontes Java.

## Arquivos principais

- `src/main/resources/templates/index.html`: catálogo e revisão.
- `src/main/resources/static/js/public-catalog.js`: consultas, apresentação, modal e validação.
- `src/main/resources/static/js/slip-state.js`: estado e cálculo de prévia.
- `src/frontend/styles/input.css`: estilos de origem; `static/css/app.css` foi recompilado.
- `src/frontend/tests/slip-state.test.mjs` e `public-catalog-browser.mjs`: verificações reproduzíveis.

## Verificações executadas

```sh
npm run lint
npm run format:check
npm run css:build
node src/frontend/tests/slip-state.test.mjs
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./mvnw -DskipTests package
git diff --check
```

Os quatro testes de estado/cálculo passaram: normalização e rejeição de entradas ambíguas, exemplo R$ 10 × 1,80 × 2,50 = R$ 45, precisão/rounding final, produto de dez odds, substituição entre mercados, limites, versões e nova aceitação explícita.

Chrome headless via Playwright, contra instância temporária Java 17 com perfil `local`, PostgreSQL 16 do projeto e dados manuais já cadastrados:

- Viewports 360 e 1440 px, sem overflow horizontal, com dados da banca exibidos no fuso correto mesmo com navegador em UTC.
- Seleções, substituição por outro mercado, remoção, limpeza com confirmação e revisão de dados fictícios.
- Foco visível, Tab contido no painel, Escape e retorno ao botão de abertura.
- Viewport de altura reduzida (360 × 450): campo e ação de revisão alcançáveis dentro do painel, sem a barra inferior sobrepor os controles.
- Ampliação CSS de 200% sem overflow horizontal e redução de viewport para 720 × 450. Isso não é declaração de teste do zoom nativo em todos os navegadores.
- Contraste calculado a partir das cores renderizadas dos botões de revisão e seleção, inclusive selecionados: razão mínima 4,5:1. Não constitui auditoria integral de acessibilidade.
- Paginação de partidas e limite de dez seleções, alteração de preço/versão preservando o valor anterior e exigindo escolha explícita, teto de retorno e catálogo vazio, com fixtures de navegador rotuladas.
- Recuperação de falhas 400, 403, 409, 429, 500 e conexão interrompida; configuração indisponível impede a revisão.
- Texto hostil de equipe/regra exibido como texto, sem criação de elementos executáveis.
- Nome/telefone fictícios apagados após recarga, storage vazio, nenhuma requisição mutável no fluxo e ausência de erros JavaScript/CSP nos cenários exercitados.

Capturas inspecionadas em `target/verification-sprint2/`: catálogo/cupom em 360 e 1440 px e `zoom-200.png`. O teste usa `TEST_BASE_URL` em `127.0.0.1` e `PLAYWRIGHT_MODULE` apontando para a instalação de teste do Playwright; não adicionou dependências de runtime ao projeto. Harness local: `/tmp/trevo-sprint2-smoke.py`; logs: `/tmp/trevo-sprint2-browser.log` e `/tmp/trevo-sprint2-package.log`.

O pacote Maven passou com testes Java explicitamente ignorados nesse empacotamento. A suíte Java completa não foi reexecutada nesta mudança de frontend; o resultado histórico de 147 testes da Sprint 1 não foi contabilizado como execução atual. Houve aviso do Node sobre detecção automática do módulo ES `.js`, sem falha dos quatro testes.

## Handoff e limites

S2.01–S2.05 entregues como incremento local com fixtures rotuladas. Para visualizar no IntelliJ, reiniciar a aplicação e acessar `/` na porta configurada, atualmente 8081. Se as ofertas manuais vencerem, revisar explicitamente os registros no admin; a UI não inventa ofertas nem renova frescor pelo acesso.

Próxima dependência liberada: integrar emissão transacional da Sprint 3 usando IDs, versões e valor normalizado; implementar validação autoritativa, idempotência e snapshots no servidor antes de habilitar geração. Amostra real de odds e homologação S1.06 continuam pendentes.

Não houve teste em aparelho físico, teclado virtual real, leitor de tela, todos os navegadores ou benchmark p95. Não houve chamada adicional ao fornecedor, operação financeira, commit, publicação ou atualização do quadro externo.
