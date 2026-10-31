# FRONTEND_AGENT — UI, UX e jornada sem cadastro

## Missão e referências

Construir uma experiência moderna, clara e acessível para montar pré-bilhetes de futebol e operar a banca. Ler [PRD](../PRD.md), especialmente §§9 e 15, [SPRINTS](../SPRINTS.md), [AGENTS.md](../../AGENTS.md), o [perfil frontend existente](frontend.md) e os AGENTS.md do caminho. Este perfil complementa as instruções existentes e não inicia automações.

## Escopo e stack

Templates Thymeleaf, assets estáticos, estilos em src/frontend, ES modules, ferramentas JS/CSS e testes de interface. Preservar HTML semântico, Tailwind compilado e JavaScript nativo; não introduzir React/SPA, CDN de produção ou biblioteca de estado sem requisito aprovado. Formulários, renderização e dados pessoais aplicam [Segurança](security.md).

## Diretrizes de produto e design

- Interface inteira em pt-BR, BRL e horário da banca; código/IDs em inglês. Aspas simples onde a sintaxe permitir.
- Dark mode com slate/Verde Trevo, tipografia legível e números alinhados. Verificar contraste efetivo; não copiar marca, banners ou assets da referência.
- Mobile primeiro: 360 px sem overflow, safe area e teclado considerados; cupom inferior não cobre conteúdo. Desktop com cupom sticky.
- Seleção por matchId: outra seleção da mesma partida substitui a anterior. Exibir mercado, período, linha, participante e regra; 1X2 não representa todo o catálogo final.
- Coletar nome/WhatsApp apenas na revisão. Sem cadastro, senha do cliente, saldo, depósito, saque ou promessa de ganho.
- Ações: “Gerar pré-bilhete” e, depois da persistência, “Enviar para o WhatsApp”. Manter “Pendente — válido somente após confirmação da banca” e prazo visíveis.

## Estado e integração

1. Rascunho em memória; sem PII em localStorage ou URLs internas. Prévia de cálculo é informativa.
2. Enviar IDs/versões, valor decimal string, identificação e aceites; resumo final vem do servidor.
3. Preservar a mesma Idempotency-Key e conteúdo no retry de resultado desconhecido. Não limpar formulário em erro nem gerar outra chave automaticamente após timeout.
4. Em 409, apresentar mudanças para revisão; não aceitar nova odd silenciosamente. Em 403/sessão expirada, recuperar sessão/CSRF antes de nova ação explícita.
5. WhatsApp abre pelo gesto explícito com resumo persistido codificado, fallback de cópia e número visível; telemetria não bloqueia navegação.
6. Clique não significa mensagem enviada, pagamento ou confirmação. A URI não contém credenciais, tokens nem links admin.
7. Não adicionar WebSocket ao MVP pré-jogo. Atualizações consultam catálogo local; backend sempre revalida na emissão.
8. Na fila admin, atualizar estado após conflito e exigir declaração Pix. No dashboard, distinguir volume, retorno potencial e média; não inventar lucro.

## Acessibilidade e segurança

Labels persistentes, foco visível, aria-pressed nas seleções, modais com foco/escape/retorno e mensagens acessíveis. Não anunciar cada variação de número. Usar th:text/textContent para dados externos; nunca innerHTML com nomes, notas ou texto do fornecedor. Não persistir dados pessoais em analytics.

## Validação e handoff

Cobrir seleção/substituição/remoção, limites, exemplo R$ 10 × 1,80 × 2,50 = R$ 45, falhas 400/403/409/429/5xx, timeout, duplo clique e WhatsApp indisponível. Verificar 360 px, desktop, zoom, teclado, foco, contraste e teclado virtual.

Quando alterar assets, executar `npm run lint`, `npm run format:check` e `npm run css:build`. Não editar CSS gerado manualmente. Registrar screenshots se houver ambiente e distinguir emulação de teste em aparelho real. Handoff contém telas/fluxos, contratos utilizados, evidências e limitações.
