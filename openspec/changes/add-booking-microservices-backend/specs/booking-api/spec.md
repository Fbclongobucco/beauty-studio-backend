## Purpose

Fornece a API definitiva e persistente do salão: catálogo, disponibilidade, pedidos de agendamento, ações do painel e ganhos. Mantém os contratos HTTP e as regras de negócio já especificados no front-end (`beauty-studio/openspec/specs/`), para que o front troque a API de teste pelo backend sem mudar código.

## ADDED Requirements

### Requirement: Compatibilidade de contrato com o front-end
A API SHALL expor os mesmos caminhos, parâmetros, códigos HTTP, códigos de erro e formatos JSON da API de teste do front.

Endpoints públicos:
- `GET /api/services`;
- `GET /api/availability`;
- `GET /api/availability/days`;
- `POST /api/appointments`;
- `GET /api/appointments/{id}`.

Endpoints do painel:
- `/api/admin/login|logout|me`;
- `/api/admin/appointments` e as sub-rotas `pending-count`, `mark-seen` e `{id}/confirm|reject|cancel`;
- `/api/admin/services`;
- `/api/admin/categories`;
- `/api/admin/professionals`;
- `/api/admin/accounts`;
- `/api/admin/earnings`.

Erros MUST seguir o formato `{ code, message, fields? }` com mensagens em pt-BR. Os códigos são `VALIDATION_ERROR`, `SLOT_UNAVAILABLE`, `SLOT_TAKEN`, `INVALID_TRANSITION`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND` e `LOGIN_DISABLED`. Campos novos (`whatsappPhone`, `photoUrl`) SHALL ser apenas adicionados, sem remover nem renomear campos existentes.

#### Scenario: Troca de backend
- **WHEN** o front-end é apontado para o gateway em vez da API de teste
- **THEN** agendamento, painel, catálogo e ganhos funcionam sem mudança de código no front

#### Scenario: Dados inválidos no pedido
- **WHEN** `POST /api/appointments` chega sem `customerPhone`
- **THEN** a resposta é 400 `VALIDATION_ERROR` com `fields.customerPhone` preenchido

### Requirement: Persistência
Profissionais, categorias, serviços, agendamentos, histórico de status e contas SHALL ficar no banco do booking-service e MUST sobreviver a reinícios. O catálogo inicial (3 profissionais, 3 categorias e 21 serviços, com preços, durações, observações e a marcação de duração estimada da spec `service-catalog` do front) SHALL ser carregado por migration uma única vez. Edições posteriores feitas no painel MUST NOT ser sobrescritas por novas subidas.

#### Scenario: Reinício preserva pedidos
- **WHEN** a cliente faz um pedido e o booking-service reinicia
- **THEN** o pedido continua listado no painel com o mesmo id e status

#### Scenario: Catálogo inicial
- **WHEN** o sistema sobe pela primeira vez
- **THEN** `GET /api/services` retorna os 21 serviços ativos, e "Box braids" aparece no painel com "duração estimada"

### Requirement: Regras de agenda e disponibilidade
A API SHALL aplicar as regras da spec `appointment-booking` do front:
- segunda a sábado, 09:00 às 20:00, no fuso America/Sao_Paulo;
- grade de 30 minutos;
- o serviço termina até 20:00;
- janela do horário atual até 30 dias corridos;
- disponibilidade por profissional, em que só agendamentos `confirmed` bloqueiam o intervalo [início, início + duração).

Datas e horas MUST ser calculadas no fuso America/Sao_Paulo, independente do fuso do servidor.

#### Scenario: Último horário de serviço de 2 h
- **WHEN** a cliente consulta a disponibilidade de "7 a 12 tranças" (2 h) em uma terça sem agendamentos
- **THEN** o último horário retornado é 18:00

#### Scenario: Pendente não bloqueia
- **WHEN** existe um pedido `pending` de Manicure às 11:00
- **THEN** 11:00 continua na disponibilidade de Manicure

#### Scenario: Servidor em UTC
- **WHEN** o contêiner roda com fuso UTC e são 22:30 UTC de uma segunda (19:30 em São Paulo)
- **THEN** a disponibilidade do dia ainda oferece 19:30 para um serviço de 30 min

### Requirement: Criação de pedido
`POST /api/appointments` SHALL validar os dados:
- nome com no mínimo 2 caracteres;
- telefone brasileiro com DDD, com 10 ou 11 dígitos, guardado em E.164 (`+55...`);
- observação com até 300 caracteres;
- data e hora dentro da grade e da janela.

O pedido válido SHALL ser criado com status `pending`, guardando o preço e a duração do serviço no momento do pedido. A resposta SHALL ser 201 com início e fim em ISO 8601 com deslocamento -03:00. Horário fora do expediente ou já ocupado por agendamento confirmado MUST responder 409 `SLOT_UNAVAILABLE`. Toda criação SHALL gerar o evento `appointment.created` (capacidade `event-messaging`).

#### Scenario: Pedido válido
- **WHEN** a cliente envia Depilação na sexta 06/11 às 15:00 com nome e WhatsApp válidos
- **THEN** a resposta é 201 com `status: "pending"`, `priceCents` 8000 e `durationMin` 60, e um evento `appointment.created` é registrado para publicação

#### Scenario: WhatsApp da cliente vem do agendamento
- **WHEN** a cliente informa `(21) 98888-7777` no passo de nome e WhatsApp e envia o pedido
- **THEN** o pedido guarda `+5521988887777`, e esse é o número usado nos avisos de status para essa cliente

#### Scenario: Horário ocupado no envio
- **WHEN** o horário pedido já está ocupado por um agendamento confirmado da mesma profissional
- **THEN** a resposta é 409 `SLOT_UNAVAILABLE` e nenhum evento é gerado

### Requirement: Transições de status sem dupla confirmação
O ciclo de status SHALL ser: `pending` → `confirmed` | `rejected`, e `confirmed` → `cancelled`. Qualquer outra transição MUST responder 409 `INVALID_TRANSITION`. Cada transição SHALL guardar data, hora, autor e motivo opcional, e SHALL gerar o evento correspondente na mesma transação.

Confirmar um pedido cujo intervalo se sobrepõe a outro confirmado da mesma profissional MUST responder 409 `SLOT_TAKEN`. Isso vale também quando duas confirmações chegam ao mesmo tempo, inclusive em instâncias diferentes do serviço: no máximo uma das duas SHALL ser aceita.

#### Scenario: Confirmações simultâneas
- **WHEN** dois pedidos pendentes de Manicure às 11:00 com a Stéfani são confirmados ao mesmo tempo por duas sessões
- **THEN** um vira `confirmed`, o outro recebe 409 `SLOT_TAKEN`, e só um evento `appointment.confirmed` é gerado

#### Scenario: Confirmar cancelado
- **WHEN** o painel tenta confirmar um agendamento `cancelled`
- **THEN** a resposta é 409 `INVALID_TRANSITION` e nenhum evento é gerado

### Requirement: Listas, conflito e pedidos novos no painel
A lista do painel SHALL aceitar os filtros `status`, `date`, `from`, `to` (inclusivos) e `professionalId`. Pendentes que se sobrepõem a um confirmado ou a outro pendente da mesma profissional SHALL vir com `conflict: "confirmed"` (sobrepõe um confirmado) ou `conflict: "pending"` (sobrepõe só pendentes), e os demais com `conflict: null`, como no contrato do front.

A contagem de pendentes e a marca "Novo" SHALL seguir a spec `admin-appointments` do front:
- a marca de visto é guardada por pedido;
- só a profissional dona marca o pedido como visto;
- o master não marca.

#### Scenario: Conflito sinalizado
- **WHEN** duas clientes pedem Manicure às 11:00 com a Stéfani e uma é confirmada
- **THEN** a lista mostra o outro pedido com `conflict: "confirmed"`

#### Scenario: Master não marca como visto
- **WHEN** o master chama `mark-seen` para todos os pendentes
- **THEN** a contagem de não vistos da Lorrayne não muda

### Requirement: Catálogo editável
O painel SHALL criar e editar serviços com nome, categoria, subcategoria, duração (múltiplo de 30 min, de 30 min a 8 h), preço opcional em centavos, profissional e status ativo. O master também SHALL editar categorias e observações e cadastrar profissionais. Editar a duração de um serviço SHALL remover a marca de duração estimada. Alterações SHALL valer de imediato no catálogo público e na disponibilidade. Agendamentos existentes MUST manter preço e duração gravados. Serviços e categorias inativos ou sem serviço ativo MUST NOT aparecer no catálogo público.

#### Scenario: Duração inválida
- **WHEN** a profissional envia duração de 45 minutos
- **THEN** a resposta é 400 `VALIDATION_ERROR` em `durationMin`

#### Scenario: Preço alterado depois do pedido
- **WHEN** o preço da Manicure muda de 2000 para 2500 centavos depois de um pedido
- **THEN** o pedido existente continua com `priceCents` 2000

### Requirement: WhatsApp da profissional configurado por ela mesma
A profissional SHALL ter o campo opcional `whatsappPhone`, em E.164. O número SHALL ser informado pela própria profissional, no painel dela, por `PATCH /api/admin/me` com `{ whatsappPhone }`. O formato aceito é brasileiro com DDD, de 10 ou 11 dígitos, com ou sem máscara. `null` ou texto vazio remove o número.

Regras de acesso:
- O master MUST NOT alterar o WhatsApp de uma profissional. Ele SHALL ver o número só para leitura em `GET /api/admin/professionals`.
- `PATCH /api/admin/me` com sessão master MUST responder 403 `FORBIDDEN`.
- O campo MUST NOT aparecer nos endpoints públicos.
- Telefone em formato inválido MUST responder 400 `VALIDATION_ERROR` em `fields.whatsappPhone`.

A mudança SHALL valer para os eventos gerados a partir dela. Eventos já gravados mantêm o número do momento em que foram gerados.

#### Scenario: Profissional cadastra o próprio número
- **WHEN** a Stéfani, logada, envia `PATCH /api/admin/me` com `{ "whatsappPhone": "(21) 99999-0000" }`
- **THEN** o número é guardado como `+5521999990000`, `GET /api/admin/me` passa a retorná-lo, e os próximos pedidos da Stéfani geram eventos com esse número

#### Scenario: Profissional remove o número
- **WHEN** a Lorrayne envia `PATCH /api/admin/me` com `{ "whatsappPhone": null }`
- **THEN** o número é removido, e os próximos pedidos dela não geram mensagem de WhatsApp (registro `SKIPPED`)

#### Scenario: Master não altera número de profissional
- **WHEN** o master envia `PATCH /api/admin/professionals/stefani` com `whatsappPhone`
- **THEN** a resposta é 400 `VALIDATION_ERROR` em `fields.whatsappPhone`, informando que só a profissional altera o próprio número, e o número não muda

#### Scenario: Número inválido
- **WHEN** a Michele envia `{ "whatsappPhone": "9999-0000" }`
- **THEN** a resposta é 400 `VALIDATION_ERROR` com a mensagem "Informe o WhatsApp com DDD, ex.: (21) 96811-7976"

#### Scenario: Número fora do catálogo público
- **WHEN** a cliente chama `GET /api/services`
- **THEN** a resposta não contém o WhatsApp de nenhuma profissional

### Requirement: Ganhos
`GET /api/admin/earnings` SHALL seguir a spec `admin-earnings` do front:
- soma dos preços gravados nos agendamentos `confirmed` com data no período;
- divisão entre realizado e previsto pelo horário de término no fuso America/Sao_Paulo;
- contagem à parte dos sem preço;
- detalhamento `byService`, ordenado por valor decrescente;
- `byDay` e `byProfessional`;
- valores em centavos.

Período com fim antes do início ou com mais de 366 dias MUST responder 400 `VALIDATION_ERROR`.

#### Scenario: Cancelado fora da soma
- **WHEN** a Stéfani tem no mês dois confirmados de Manicure (2000 cada) e um cancelado de Pedicure (2500)
- **THEN** `total` é 4000 com 2 atendimentos
