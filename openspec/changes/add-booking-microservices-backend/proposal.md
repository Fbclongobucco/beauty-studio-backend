## Why

O front-end do Longo Bucco Beauty Studio (`../beauty-studio`) funciona sobre uma API de teste em memória: tudo se perde ao reiniciar, as senhas ficam em texto puro e os eventos de agendamento só vão para o log. A profissional não fica sabendo de um pedido novo se não estiver com o painel aberto. Para o uso real, o salão precisa de um backend persistente, que mantenha os contratos HTTP do front e avise por WhatsApp a cada evento de agendamento.

## What Changes

- Criação de um backend em microsserviços orientados a eventos, em Java 25 e Spring Boot com Spring Cloud, com virtual threads ligadas em todos os serviços.
- **booking-service** (Clean Architecture, no padrão do projeto App-adm-obras: `core` / `application` / `infra`):
  - implementa os mesmos contratos HTTP de `/api/*` e `/api/admin/*` do front;
  - mantém as regras de agenda, disponibilidade, status, conflito, catálogo e ganhos já especificadas;
  - persiste em PostgreSQL, com migrations Flyway e o catálogo inicial carregado por migration.
- Autenticação persistente:
  - contas no banco, com senha em hash;
  - master inicial vindo do ambiente;
  - sessão em cookie `httpOnly` `admin_session` de 12 h, o mesmo nome que o front já usa.
- **messaging-contracts**: módulo Maven compartilhado, também em Clean Architecture. Define os eventos `appointment.created|confirmed|rejected|cancelled`, a topologia do RabbitMQ (exchange, filas, DLQ) e as portas de publicação e consumo.
- Publicação confiável:
  - o booking-service grava o evento em uma tabela outbox na mesma transação da mudança de status;
  - um relay publica no RabbitMQ com confirmação do broker.
- **notification-service** (Clean Architecture):
  - consome os eventos e envia WhatsApp pela Evolution API, que roda no próprio compose;
  - a profissional recebe aviso de pedido novo e confirma no painel;
  - a cliente recebe aviso de confirmado, recusado ou cancelado;
  - processa cada evento uma vez só (idempotência) e registra cada envio no banco.
- **discovery-server** (Eureka) e **api-gateway** (Spring Cloud Gateway):
  - o gateway é a única porta exposta, com roteamento por descoberta, CORS para o front e circuit breaker (Resilience4j) com resposta 503 padronizada.
- Circuit breaker também nas integrações externas: Evolution API no notification-service e MinIO no booking-service.
- **MinIO** para fotos dos serviços do catálogo: endpoints de upload e remoção no painel e URLs públicas das fotos na lista de serviços. A tela do front fica para outra mudança.
- **Infra com Docker Compose**: PostgreSQL (um banco por serviço), RabbitMQ com painel de gestão, MinIO, Evolution API, discovery, gateway, booking e notification, com healthchecks e `.env.example`.
- Extensões de contrato em relação à API de teste:
  - profissional ganha o campo `whatsappPhone`, configurado por ela mesma no painel em `PATCH /api/admin/me`, que é um endpoint novo; o master só vê o número;
  - o WhatsApp da cliente continua sendo o informado ao agendar e passa a ser o destino dos avisos de status;
  - serviço ganha `photoUrl` (uma foto por serviço), com `PUT`/`DELETE /api/admin/services/{id}/photo`, seguindo o contrato da mudança `add-service-photo` do front;
  - em `GET /api/admin/accounts`, o campo `login` ganha o valor `"db"` para logins criados pelo painel, que agora persistem.
  - O front precisa de pequeno ajuste de tipos e textos (fora deste repositório).

## Capabilities

### New Capabilities

- `platform-infrastructure`: composição dos serviços (Docker Compose), descoberta, gateway, circuit breaker, virtual threads, Flyway e configuração por ambiente.
- `booking-api`: API persistente de catálogo, disponibilidade, agendamentos, painel e ganhos, compatível com os contratos do front.
- `admin-auth`: contas persistentes com hash de senha, perfis master e profissional, sessão por cookie e escopo por profissional.
- `event-messaging`: contratos de eventos de agendamento, topologia do RabbitMQ, outbox e entrega confiável.
- `whatsapp-notification`: envio de WhatsApp para profissional e cliente a partir dos eventos, com idempotência, retentativa e registro.
- `service-photo-storage`: armazenamento de fotos dos serviços no MinIO e exposição pública das URLs.

### Modified Capabilities

Nenhuma. O repositório ainda não tem specs. As regras de negócio vêm das specs do front (`../beauty-studio/openspec/specs/`) e são reaproveitadas aqui como requisitos do backend.

## Impact

- **Repositório novo**: projeto Maven multi-módulo com:
  - `messaging-contracts`, `discovery-server`, `api-gateway`, `booking-service` e `notification-service`;
  - `compose.yaml`, `.env.example`, Dockerfiles e scripts de inicialização do Postgres.
- **Front-end (`../beauty-studio`)**:
  - passa a apontar `NEXT_PUBLIC_API_BASE_URL` para o gateway;
  - pequeno ajuste em tipos e textos de contas (`login: "db"`) e fotos;
  - nova tela ou campo "Meu WhatsApp" no painel da profissional, usando `PATCH /api/admin/me`;
  - aviso na tela de agendamento de que a cliente receberá as atualizações nesse WhatsApp;
  - a API em memória continua disponível para testes.
- **Dependências externas**:
  - um número de WhatsApp para conectar na Evolution API por QR Code;
  - os números de WhatsApp das profissionais, que cada uma informa no próprio painel. Até ela informar, os avisos de pedido novo dela ficam como `SKIPPED`.
- **Risco operacional**: a Evolution API não é oficial. O número conectado pode ser bloqueado pelo WhatsApp. O envio fica isolado atrás de uma porta, para trocar de provedor depois.
