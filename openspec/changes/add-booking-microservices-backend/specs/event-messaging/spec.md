## Purpose

Define os eventos de agendamento trocados entre os serviços pelo RabbitMQ: o contrato de cada evento, a topologia de exchanges e filas e as garantias de entrega. Assim, qualquer serviço pode reagir ao que acontece na agenda sem acoplamento direto ao booking-service.

## ADDED Requirements

### Requirement: Contrato dos eventos de agendamento
O booking-service SHALL publicar os eventos `appointment.created`, `appointment.confirmed`, `appointment.rejected` e `appointment.cancelled`. Cada evento SHALL ser JSON com estes campos:
- `eventId`: UUID único;
- `type`;
- `version` do contrato;
- `occurredAt`: ISO 8601;
- `appointment`: id, status, data, horário, início e fim com -03:00, preço em centavos, duração, motivo opcional;
- `service`: id e nome;
- `professional`: id, nome e `whatsappPhone` em E.164, ou nulo;
- `customer`: nome e telefone em E.164;
- `statusUrl`: link público do pedido.

O evento MUST carregar todos os dados necessários para notificar, sem exigir consulta ao booking-service. Mudanças incompatíveis no contrato MUST incrementar `version`.

#### Scenario: Evento de criação completo
- **WHEN** a cliente cria um pedido de Manicure com a Stéfani
- **THEN** o evento `appointment.created` publicado traz o nome e o telefone da cliente, o nome e o WhatsApp da Stéfani, data, horário, preço e o `statusUrl` do pedido

### Requirement: Topologia do broker
Os eventos SHALL ser publicados em uma exchange durável do tipo topic, com routing key igual ao `type` do evento. Cada serviço consumidor SHALL ter a própria fila durável, ligada às routing keys de interesse, e uma fila de mensagens mortas (DLQ) associada. As mensagens MUST ser persistentes. Exchanges, filas e ligações SHALL ser declaradas pelos próprios serviços na inicialização, sem passo manual.

#### Scenario: Broker recém-criado
- **WHEN** o RabbitMQ sobe vazio e os serviços iniciam
- **THEN** a exchange de agendamentos, a fila de notificação e a DLQ existem e estão ligadas, sem configuração manual

#### Scenario: Novo consumidor
- **WHEN** um futuro serviço de lembretes cria a própria fila ligada a `appointment.confirmed`
- **THEN** ele recebe os eventos sem mudança no booking-service nem no notification-service

### Requirement: Publicação confiável com outbox
O evento SHALL ser gravado no banco do booking-service na mesma transação que cria o agendamento ou muda o status. Se a transação falhar, MUST NOT existir evento. Se o broker estiver fora do ar, a operação do usuário MUST ser concluída normalmente, e o evento SHALL ser publicado quando o broker voltar.

Um evento SHALL ser marcado como publicado só depois da confirmação do broker. A entrega SHALL ser ao menos uma vez, e eventos do mesmo agendamento SHALL ser publicados na ordem em que ocorreram.

#### Scenario: Broker fora do ar
- **WHEN** o RabbitMQ está parado e a profissional confirma um pedido
- **THEN** o painel recebe a confirmação com sucesso, e o evento `appointment.confirmed` é publicado depois que o RabbitMQ volta

#### Scenario: Falha na transação
- **WHEN** a confirmação falha com `SLOT_TAKEN`
- **THEN** nenhum evento `appointment.confirmed` é gravado nem publicado

### Requirement: Consumo idempotente e mensagens com falha
O consumidor SHALL confirmar (ack) a mensagem só depois de processá-la. Um evento repetido (mesmo `eventId`) MUST ser ignorado sem efeito duplicado.

Falhas temporárias SHALL ser retentadas com intervalo crescente, até um número máximo configurável. Depois disso, ou em caso de mensagem malformada, a mensagem MUST ir para a DLQ, com o motivo registrado no log, sem travar a fila.

#### Scenario: Evento duplicado
- **WHEN** o mesmo `appointment.created` chega duas vezes ao notification-service
- **THEN** a profissional recebe uma única mensagem

#### Scenario: Mensagem malformada
- **WHEN** chega à fila de notificação uma mensagem sem `eventId`
- **THEN** ela vai para a DLQ, e as mensagens seguintes continuam sendo processadas
