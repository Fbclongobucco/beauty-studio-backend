## Why

Hoje a cliente acompanha o pedido só pelo link da tela de pedido enviado ou pelo link que chega nas mensagens de WhatsApp. Se ela fechar a aba e não tiver a mensagem à mão, não há como achar o pedido de novo pelo site.

Ao voltar à página inicial, o front vai levar a cliente direto à tela do pedido em andamento, com o status (mudança `add-customer-request-tracking` no repositório `beauty-studio`). Para isso, a API precisa lembrar, no próprio aparelho, quais pedidos foram feitos ou abertos ali.

## What Changes

- **Cookie `my_requests`:**
  - gravado pela API, `HttpOnly`, com caminho `/api/appointments` e validade de 60 dias;
  - guarda até 10 pedidos, em token assinado;
  - entra na resposta de `POST /api/appointments` (pedido criado) e de `GET /api/appointments/{id}` (pedido aberto pelo link, inclusive o do WhatsApp em outro aparelho).
- **Novo `GET /api/appointments/mine`:** devolve os pedidos do cookie que ainda importam, no formato público de sempre (`PublicAppointment`):
  - pendentes;
  - confirmados que ainda não terminaram;
  - recusados ou cancelados com início ainda no futuro.
  - Sem cookie, a lista vem vazia.
- Os endpoints atuais não mudam; só ganham o `Set-Cookie`.

## Capabilities

### New Capabilities
- `customer-request-tracking`: lembrar no aparelho os pedidos feitos ou abertos e listar os que estão em andamento, sem login.

### Modified Capabilities
<!-- nenhuma: os contratos existentes não mudam -->

## Impact

- **booking-service:**
  - `PublicController`;
  - novo componente de cookie de acompanhamento em `infra/security`;
  - caso de uso de listagem por ids em `application`;
  - `SecurityConfig`: as rotas novas são públicas.
- **api-gateway:** nenhuma mudança; ele já repassa `Set-Cookie` e `Cookie` (como no `admin_session`) e não guarda cookies entre requisições.
- **Banco:** nenhuma migration; o cookie carrega só ids.
- **Deploy:** a mesma exigência do painel, front e API no mesmo site, para o navegador enviar o cookie.
