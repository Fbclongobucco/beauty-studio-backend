## Context

O booking-service já emite JWT HS256 para o `admin_session`, com `SessionCookies`, `APP_JWT_SECRET` e o `cookieSecure`/`cookieSameSite` do perfil. Os ids dos pedidos são UUID aleatórios, e quem tem o id já consegue ler o pedido em `GET /api/appointments/{id}`. O gateway repassa cookies e não os guarda (correção feita no `JdkClientHttpRequestFactory`).

## Goals / Non-Goals

**Goals:**
- Acompanhar sem login, no mesmo aparelho, sem banco novo.
- Cookie gravado pelo servidor. O Safari limita a 7 dias os cookies gravados por JavaScript; o gravado pela resposta HTTP do próprio site segue a validade pedida.

**Non-Goals:**
- Acompanhar entre aparelhos sem o link; o WhatsApp continua cobrindo isso.
- Conta de cliente.
- Notificação no navegador.

## Decisions

### D1. Token assinado em vez de lista crua de ids
O valor é um JWT HS256 com `aud: "my-requests"`, `ids: [...]` e `exp` de 60 dias, assinado com `APP_JWT_SECRET`. A audiência própria impede usar esse token como sessão do painel, e vice-versa, e o validador de sessão passa a exigir a audiência dele.

Com assinatura, a cliente não consegue injetar ids no cookie. Isso não muda o que ela já consegue ver, porque o id sozinho já abre o pedido, mas mantém o formato fechado e versionável.

Alternativa descartada: ids crus no cookie. Mais simples, mas qualquer valor viraria entrada para o banco sem validação de origem.

### D2. Caminho `/api/appointments`
O navegador só envia `my_requests` às rotas de pedido público. O cookie não vai para `/api/admin/*`, nem para o catálogo ou as fotos.

### D3. Filtro no servidor
"Em andamento" (requisito "Pedidos em andamento do aparelho") é calculado no booking-service, com o `Clock` e o fuso America/Sao_Paulo já usados na disponibilidade. Assim o front não repete a regra, e a API de teste segue o mesmo contrato.

### D4. Gravação no GET do pedido
Abrir o link do pedido grava o cookie. É efeito colateral em um GET, mas idempotente e restrito ao navegador de quem abriu. É o que faz o link do WhatsApp "ensinar" o celular da cliente.

## Risks / Trade-offs

- **Aparelho compartilhado:** outra pessoa no mesmo aparelho vê o primeiro nome, o serviço e o horário, os mesmos dados que o link já mostra, e nenhum telefone. O botão "Não é meu pedido" foi retirado a pedido do salão.
- **Troca do `APP_JWT_SECRET`:** invalida os cookies, que passam a contar como vazios. A cliente perde só o atalho; o link continua funcionando.
- **Sessões do painel no deploy:** ao passar a exigir audiência no `admin_session` (tarefa 1.2), as sessões abertas antes do deploy deixam de valer, e as contas fazem login de novo uma vez.
- **Front e API em sites diferentes:** o cookie não seria enviado. A exigência é a mesma do painel e já está documentada.
