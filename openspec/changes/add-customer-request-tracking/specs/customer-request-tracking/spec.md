## ADDED Requirements

### Requirement: Cookie de acompanhamento
A API SHALL gravar o cookie `my_requests` nas respostas de `POST /api/appointments` (201) e de `GET /api/appointments/{id}` (200).

O cookie MUST ser:
- `HttpOnly`, com `Path=/api/appointments`;
- `SameSite` e `Secure` iguais aos do cookie de sessão do painel;
- válido por 60 dias, renovado a cada gravação.

O valor SHALL ser um token assinado pelo servidor, com até 10 ids de pedidos, do mais recente para o mais antigo. Um id já presente MUST ir para o início sem duplicar. Acima de 10, os mais antigos saem. Cookie com assinatura inválida, expirado ou malformado MUST ser tratado como vazio, sem erro para a cliente.

O cookie MUST NOT conter nome, telefone ou outro dado da cliente.

#### Scenario: Pedido criado entra no cookie
- **WHEN** a cliente cria um pedido pelo site
- **THEN** a resposta 201 traz `Set-Cookie: my_requests=...; Path=/api/appointments; HttpOnly`, com o id do pedido

#### Scenario: Link aberto em outro aparelho
- **WHEN** a cliente abre no celular o link do pedido recebido no WhatsApp
- **THEN** a resposta de `GET /api/appointments/{id}` grava o cookie com esse id nesse celular

#### Scenario: Cookie adulterado
- **WHEN** a requisição traz um `my_requests` com a assinatura alterada
- **THEN** a API trata como cookie vazio, `GET /api/appointments/mine` responde 200 com a lista vazia e a próxima gravação substitui o cookie

#### Scenario: Limite de pedidos
- **WHEN** o aparelho já tem 10 pedidos no cookie e a cliente cria mais um
- **THEN** o cookie passa a ter o novo pedido no início e perde o mais antigo

### Requirement: Pedidos em andamento do aparelho
`GET /api/appointments/mine` SHALL responder 200 com `{ appointments: PublicAppointment[] }`, no mesmo formato de `GET /api/appointments/{id}`.

A lista SHALL trazer, entre os pedidos do cookie que ainda existem:
- os `pending`;
- os `confirmed` cujo término ainda não passou;
- os `rejected` e `cancelled` cujo início ainda está no futuro, para a cliente ver o desfecho.

A ordem SHALL ser pelo início, do mais próximo para o mais distante. Sem cookie, ou sem pedido que se encaixe, a lista MUST vir vazia. Ids que não existem mais MUST ser retirados do cookie na resposta. O endpoint MUST NOT exigir login e MUST NOT expor dados além do formato público.

#### Scenario: Pedido pendente
- **WHEN** o aparelho tem no cookie um pedido de Manicure pendente para sexta às 11:00
- **THEN** a lista traz esse pedido com `status: "pending"` e `customerFirstName`

#### Scenario: Atendimento já passou
- **WHEN** o único pedido do cookie é um confirmado que terminou ontem
- **THEN** a lista vem vazia

#### Scenario: Recusa ainda relevante
- **WHEN** a profissional recusa um pedido para a semana que vem
- **THEN** a lista traz o pedido com `status: "rejected"` até o horário pedido passar

#### Scenario: Sem cookie
- **WHEN** um aparelho que nunca fez pedido chama `GET /api/appointments/mine`
- **THEN** a resposta é 200 com `{ "appointments": [] }`

### Requirement: Esquecer um pedido no aparelho
`DELETE /api/appointments/mine/{id}` SHALL retirar o id do cookie e responder 200 com a lista atualizada, no formato de `GET /api/appointments/mine`. Id ausente do cookie MUST responder 200 sem mudar nada. A operação MUST NOT alterar o pedido em si.

#### Scenario: Não é meu pedido
- **WHEN** a cliente toca em "Não é meu pedido" no cartão de um pedido
- **THEN** o id sai do cookie, o pedido continua com o mesmo status no painel e não aparece mais no cartão desse aparelho
