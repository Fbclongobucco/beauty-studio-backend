## Purpose

Avisa por WhatsApp a profissional, a cada novo pedido, e a cliente, a cada mudança de status do agendamento, a partir dos eventos do RabbitMQ. Assim a profissional confirma no painel sem precisar estar com ele aberto.

## ADDED Requirements

### Requirement: Aviso de novo pedido à profissional
Ao receber `appointment.created`, o notification-service SHALL enviar uma mensagem de WhatsApp ao `whatsappPhone` da profissional responsável. Esse número é o que ela configurou no próprio painel e chega no evento. A mensagem SHALL ter:
- nome da cliente;
- serviço;
- data por extenso em pt-BR;
- horário;
- preço formatado (ou "Consultar valor");
- observação da cliente, se houver;
- link do painel para confirmar.

A mensagem MUST NOT ter dados de outros pedidos.

#### Scenario: Pedido de Manicure
- **WHEN** chega `appointment.created` de Manicure para sexta-feira, 06/11, às 11:00, cliente Maria, com a Stéfani
- **THEN** o WhatsApp da Stéfani recebe uma mensagem com "Maria", "Manicure", "sexta-feira, 6 de novembro", "11:00", "R$ 20,00" e o link do painel

#### Scenario: Profissional sem WhatsApp cadastrado
- **WHEN** chega `appointment.created` de uma profissional com `whatsappPhone` nulo
- **THEN** nenhuma mensagem é enviada, o registro de notificação fica como `SKIPPED` com o motivo, e o evento não é retentado

### Requirement: Aviso de status à cliente
Ao receber `appointment.confirmed`, `appointment.rejected` ou `appointment.cancelled`, o notification-service SHALL enviar uma mensagem de WhatsApp ao telefone que a cliente informou ao agendar, que chega no evento. A mensagem SHALL ter:
- nome do salão;
- serviço;
- data e horário;
- o novo status em linguagem simples ("Confirmado", "Recusado", "Cancelado");
- o motivo, quando informado;
- o link de status do pedido.

Pedido recusado ou cancelado SHALL trazer também o WhatsApp do salão para contato. O texto SHALL ser curto e legível para clientes idosas, sem abreviações.

#### Scenario: Confirmação à cliente
- **WHEN** a Stéfani confirma o pedido da Maria
- **THEN** a Maria recebe no WhatsApp "Confirmado", o serviço, a data, o horário e o link do pedido

#### Scenario: Cancelamento com motivo
- **WHEN** a Lorrayne cancela um agendamento com o motivo "Imprevisto de saúde"
- **THEN** a cliente recebe "Cancelado", o motivo "Imprevisto de saúde" e o contato do salão

### Requirement: Envio desacoplado do provedor
O envio SHALL passar por uma interface de envio de mensagens independente do provedor. O provedor desta versão é a Evolution API, rodando no próprio compose e conectada a um número de WhatsApp por QR Code. Trocar de provedor MUST exigir só um novo adaptador, sem mudar regras nem textos. Uma configuração SHALL permitir desligar o envio real e só registrar a mensagem no log, para desenvolvimento e testes.

#### Scenario: Modo somente log
- **WHEN** o envio real está desligado e chega `appointment.created`
- **THEN** o texto completo da mensagem aparece no log, e o registro fica como `LOGGED`

### Requirement: Proteção contra falha do provedor
As chamadas ao provedor de WhatsApp SHALL ter tempo limite e circuit breaker. Com o provedor fora do ar ou com o circuito aberto, a mensagem MUST NOT ser perdida: ela SHALL ser retentada conforme a política de retentativa (capacidade `event-messaging`) e, esgotadas as tentativas, ir para a DLQ com registro `FAILED`. Erros permanentes do provedor (número inexistente no WhatsApp) MUST ser registrados como `FAILED` sem retentativa.

#### Scenario: Evolution API fora do ar
- **WHEN** a Evolution API para por 2 minutos e chegam 3 pedidos nesse intervalo
- **THEN** as 3 mensagens são enviadas depois que ela volta, dentro do número máximo de tentativas, sem duplicação

#### Scenario: Número inexistente
- **WHEN** o telefone da cliente não tem WhatsApp
- **THEN** o registro fica `FAILED` com o motivo, e a mensagem não é retentada

### Requirement: Registro das notificações
Cada notificação SHALL ficar registrada no banco do notification-service com estes dados:
- `eventId`;
- tipo do evento;
- destinatário (papel e telefone mascarado nos logs);
- status (`SENT`, `LOGGED`, `SKIPPED`, `FAILED`);
- número de tentativas;
- identificador do provedor;
- datas.

O registro SHALL ser a base da idempotência por `eventId` e destinatário.

#### Scenario: Consulta de falhas
- **WHEN** o operador consulta o banco de notificações após uma queda do provedor
- **THEN** encontra cada notificação com o status final e o número de tentativas

### Requirement: Telefones protegidos nos logs
Telefones e nomes de clientes MUST aparecer mascarados nos logs (ex.: `+55219****7976`), exceto no modo somente log de desenvolvimento, que é explicitamente ligado.

#### Scenario: Log em produção
- **WHEN** uma mensagem é enviada com o envio real ligado
- **THEN** o log registra o `eventId` e o telefone mascarado, sem o texto da mensagem
