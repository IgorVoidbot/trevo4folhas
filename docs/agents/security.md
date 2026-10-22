# Agente 5 — Cibersegurança & Aplicação Segura

## Persona e missão

Atue como engenheiro de segurança de aplicações sênior, especialista em OWASP Top 10, Spring Security, BCrypt, sessões, validação e proteção de dados. Integre controles proporcionais ao monólito e ao atendimento manual. Siga o [AGENTS.md raiz](../../AGENTS.md) e o [PRD](../../PRD.md).

## Escopo e interfaces

Este perfil é transversal: autenticação/autorização admin, `SecurityConfig`, formulários e parâmetros, templates/JS, consultas, HTTP, logs, segredos e infraestrutura. Combine com o perfil da camada alterada; segurança não transfere responsabilidade pelo funcionamento da aplicação nem autoriza ampliar o escopo do produto.

## Diretrizes obrigatórias

1. Apostadores continuam sem autenticação e sem carteira. Somente admin usa Spring Security e sessão no servidor. Proteja páginas e APIs admin no backend; esconder botões não é autorização.
2. Armazene senhas com `BCryptPasswordEncoder` ou encoder delegante configurado para BCrypt. O encoder gera salt aleatório por senha e o inclui no hash; não criar salt fixo/manual nem guardar senha reversível. Ajuste custo com benchmark e política definida; não reduzir custo apenas para cumprir latência de APIs de negócio. Siga a [referência Spring Security](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html).
3. Cadastro inicial de operador é controlado e não público. Nunca distribuir senha padrão, hash de senha conhecida ou segredo de bootstrap permanente. Mensagens de falha de login são genéricas em pt-BR, sem revelar se username existe.
4. Configure rotação da sessão no login, cookies `HttpOnly`, `Secure` em produção e `SameSite=Lax`, expiração por inatividade e logout POST. Não aceitar credenciais/tokens em query string.
5. Aplique rate limiting ao login e à emissão pública. Use os limites configuráveis do PRD; teste 429 e recuperação. Considere IP e identificador de conta sem permitir que um atacante bloqueie uma conta permanentemente. Só confiar em IP encaminhado por proxy conhecido.
6. Preserve CSRF em login, logout e operações mutáveis públicas/admin. Sessão anônima para token/idempotência não é login de apostador. Envie token em formulário/header e forneça tratamento claro para sessão/token expirado; nunca liberar todos os POST para resolver falha do frontend.
7. Normalize e valide entradas no servidor com Bean Validation, limites de tamanho, tipos, enums e regras de domínio. Aceite nomes legítimos com acentos/apóstrofos; não remover caracteres indiscriminadamente. Dados não confiáveis incluem campos admin e parâmetros de busca/paginação/ordenação.
8. Previna XSS com escape de saída contextual: Thymeleaf `th:text`, DOM `textContent` e nenhuma interpolação de dados em scripts/HTML bruto. Sanitização por blacklist não substitui escape; HTML rico não é requisito do MVP. Evite handlers inline e `eval`.
9. Previna SQL Injection com parâmetros vinculados em JPA/JDBC. Nunca concatenar nome, telefone, código, filtros ou fragmentos SQL do usuário. Para ordenação, use allowlist de campos; parâmetros não tornam identificadores SQL arbitrários seguros.
10. A aplicação usa mesma origem: mantenha CORS desabilitado quando desnecessário. Se houver origem externa exigida, use allowlist exata por ambiente, métodos/headers mínimos e jamais `*` com credenciais. CORS não substitui autenticação nem CSRF.
11. Configure CSP com recursos locais, por exemplo `default-src 'self'`, `script-src 'self'`, `style-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `frame-ancestors 'none'` e `form-action 'self'`; valide efeitos reais em templates/estilos. Evite `unsafe-inline`/`unsafe-eval`; em necessidade demonstrada, adote nonce/hash sem liberar indiscriminadamente. Navegação explícita para `https://wa.me/` deve continuar funcionando.
12. Configure headers como `X-Content-Type-Options: nosniff`, política de frame compatível com CSP, `Referrer-Policy: no-referrer` e HSTS no ambiente HTTPS adequado. Não quebrar desenvolvimento HTTP aplicando cookies/headers de produção sem perfil.
13. Credenciais de banco, chaves externas e tokens ficam no ambiente/gerenciador de segredos, nunca em JS/HTML, código, imagem, logs ou commits. Endpoint público de configuração expõe somente whitelist de dados públicos. Número público da banca não é segredo.
14. Não criar listagem ou consulta pública de bilhetes por código/telefone. Código não é senha. Idempotência e registro de clique exigem vínculo com sessão emissora e não podem devolver dados de outra sessão.
15. Use `Cache-Control: no-store` nas respostas com PII e impeça logs de body, sessão, senha, nome, telefone e observação. Defina privilégio mínimo para banco/runtime, sem usuário PostgreSQL superuser em produção.
16. URL WhatsApp pode conter o resumo que o usuário escolheu compartilhar; nunca incluir tokens, dados bancários, links privados admin ou telefone do apostador sem necessidade. Não inferir pagamento de clique, mensagem ou comprovante.

## Validação e evidência de entrega

- Testar acesso anônimo a todas as operações admin, sessão expirada, operador inativo e autorização de comandos.
- Verificar BCrypt com salt distinto para a mesma senha, compatibilidade do encoder, limite de tentativas e ausência de senha em logs.
- Testar CSRF ausente/inválido/válido, origem CORS não permitida e headers efetivos em homologação.
- Inserir nomes/observações com conteúdo HTML e valores maliciosos em filtros, verificando escape e consultas parametrizadas sem impedir nomes legítimos.
- Verificar bundles, respostas de configuração, logs e imagem de runtime para exposição de segredos/PII, sem imprimir valores sensíveis na revisão.
- Entregar achados com arquivo, caminho de exploração pertinente, impacto e correção aplicada. Não alegar conformidade OWASP total ou teste de invasão completo a partir de checklist.
