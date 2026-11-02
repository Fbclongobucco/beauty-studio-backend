## 1. Base do monorepo

- [x] 1.1 Conferir na tabela oficial do Spring Cloud o release train compatível com Spring Boot 4.1.x e fixar as tags das imagens (Postgres, RabbitMQ, MinIO, Evolution API v2) no Docker Hub; registrar as versões escolhidas no topo do `pom.xml` parent. Verificar: nenhuma tag `latest` no repositório.
- [x] 1.2 Criar o `pom.xml` parent com módulos `messaging-contracts`, `discovery-server`, `api-gateway`, `booking-service` e `notification-service`, Java 25, BOMs de Spring Boot e Spring Cloud, Maven Wrapper, `.gitignore` e `.dockerignore`. Verificar: `./mvnw -q validate` passa.
- [x] 1.3 Criar o Dockerfile multi-stage comum (build com Maven, runtime JRE 25, usuário não root, `JAVA_OPTS`). Verificar: `docker build` de um módulo de exemplo gera imagem que inicia.

## 2. messaging-contracts

- [x] 2.1 Criar em `core` os records `AppointmentEvent` (eventId, type, version, occurredAt, appointment, service, professional, customer, statusUrl), o enum `AppointmentEventType` com routing keys e `CONTRACT_VERSION`, sem dependência de Spring. Verificar: teste unitário de serialização e desserialização JSON ida e volta.
- [x] 2.2 Criar em `application` as portas `EventPublisher` e `EventHandler<T>`. Verificar: teste ArchUnit garante que `core` e `application` não importam Spring.
- [x] 2.3 Criar em `infra` a auto-configuração AMQP com exchange `beauty.appointments`, DLX `beauty.appointments.dlx`, conversor JSON, propriedades de mensagem (persistente, `message-id`, `x-event-version`) e publisher com publisher confirms. Verificar: teste com Testcontainers RabbitMQ publica e recebe confirmação.
- [x] 2.4 Criar o helper de declaração de fila de consumidor com fila de retry por TTL, DLQ e bindings por routing key, configurável por propriedades. Verificar: teste com Testcontainers mostra a mensagem rejeitada indo ao retry, voltando e, após o máximo de tentativas, chegando à DLQ.

## 3. Infra Docker Compose

- [x] 3.1 Criar `infra/postgres/init/01-databases.sh` com os bancos `booking`, `notification` e `evolution` e os usuários próprios, com senhas vindas do ambiente. Verificar: subir só o Postgres e conferir os bancos com `psql \l`.
- [x] 3.2 Criar o `compose.yaml` com postgres, rabbitmq (management), minio (`chainguard/minio` fixado por digest; o bucket é criado pelo booking-service, tarefa 10.1), evolution-api, discovery-server, api-gateway, booking-service e notification-service. Incluir healthchecks, `depends_on` com `service_healthy`, `mem_limit` e só a porta do gateway publicada. Criar `compose.local.yaml` com as portas de gestão (RabbitMQ, MinIO console, Evolution manager) para desenvolvimento. Verificar: `docker compose config` sem erros.
- [x] 3.3 Conferir na documentação oficial da versão fixada da Evolution API as variáveis de banco, cache e chave e configurar o serviço no compose. Verificar: o contêiner fica `healthy` e responde à chamada autenticada de listagem de instâncias.
- [x] 3.4 Criar o `.env.example` com todas as variáveis, por exemplo:
  - senhas: `POSTGRES_PASSWORD` e as dos bancos, `RABBITMQ_PASSWORD`, `MINIO_ROOT_PASSWORD`;
  - chaves da Evolution: `EVOLUTION_API_KEY`, `EVOLUTION_INSTANCE`;
  - sessão e contas: `APP_JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_USERS`;
  - URLs e CORS: `APP_CORS_ALLOWED_ORIGINS`, `APP_PUBLIC_FRONT_URL`, `APP_PUBLIC_MEDIA_URL`;
  - liga e desliga: `APP_VIRTUAL_THREADS`, `NOTIFICATION_SEND_ENABLED`, `SALON_WHATSAPP`.

  Usar `:?` nas obrigatórias. Verificar: `docker compose up` sem `.env` falha indicando a variável faltante.

## 4. discovery-server e api-gateway

- [x] 4.1 Criar o `discovery-server` (Eureka Server) com actuator health e virtual threads. Verificar: o painel Eureka abre localmente e o health responde `UP`.
- [x] 4.2 Criar o `api-gateway` (Gateway Server WebMVC) com rota `/api/**` → `lb://booking-service` e registro no Eureka. Verificar: teste de integração com stub registrado roteia `/api/services`.
- [x] 4.3 Adicionar o CircuitBreaker Resilience4j por rota, com timeout e fallback JSON 503 `SERVICE_UNAVAILABLE`. Verificar: teste com destino parado retorna 503 no formato `{code,message}` e, com o circuito aberto, responde sem aguardar o timeout.
- [x] 4.4 Configurar o CORS global com origens de `APP_CORS_ALLOWED_ORIGINS` e `allowCredentials`. Verificar: teste de preflight aceita a origem configurada e não autoriza outra.
- [x] 4.5 Adicionar a rota `/media/**` → MinIO com reescrita de caminho, só GET. Verificar: teste ou checagem manual mostra objeto público servido e PUT recusado.

## 5. booking-service: estrutura e domínio

- [x] 5.1 Criar o módulo com pacotes `core`, `application` e `infra` (padrão App-adm-obras), `ClockConfig` com fuso America/Sao_Paulo, virtual threads, Hikari configurável, Eureka client e actuator. Criar o teste ArchUnit das regras de dependência entre camadas. Verificar: `./mvnw -pl booking-service test` passa.
- [x] 5.2 Implementar os value objects `PhoneE164` (10 ou 11 dígitos BR → +55), `Money` (centavos) e `Duration30` (múltiplo de 30, de 30 a 480). Verificar: testes unitários portados de `beauty-studio/lib/domain/validation.test.ts`.
- [x] 5.3 Implementar `Schedule`: expediente de segunda a sábado, 09:00 a 20:00, grade de 30 min, fim até 20:00 e janela de agora até 30 dias, com `Clock`. Verificar: testes portados de `schedule.test.ts`, incluindo o caso do servidor em UTC.
- [x] 5.4 Implementar `Availability`, com sobreposição [início, fim), só confirmados bloqueando e por profissional. Verificar: testes portados de `availability.test.ts`.
- [x] 5.5 Implementar `Appointment`, com máquina de estados, histórico (data, autor, motivo), preço e duração congelados e detecção de conflito. Verificar: testes portados de `status.test.ts` e de conflito.
- [x] 5.6 Implementar `Earnings`, com confirmados, realizado e previsto pelo término no fuso, sem preço à parte, `byService` decrescente, `byDay` e `byProfessional`. Verificar: testes portados de `earnings.test.ts`.

## 6. booking-service: persistência

- [x] 6.1 Escrever `V1__esquema_inicial.sql`:
  - tabelas profissional (id texto, `whatsapp_phone`), categoria, serviço (`duration_estimated`), agendamento (`starts_at` e `ends_at` timestamptz, preço, duração, `seen_at`), `appointment_status_history`, conta (hash, `pwd_version`, origem `env`/`db`) e `outbox_event`;
  - `btree_gist` e a exclusion constraint de confirmados por profissional.

  Verificar: teste Testcontainers aplica a migration e insere dois confirmados sobrepostos, com erro 23P01.
- [x] 6.2 Escrever `V2__catalogo_inicial.sql` com as 3 profissionais, as categorias com observação e os 21 serviços, copiados de `beauty-studio/lib/server/seed.ts`. Verificar: teste compara contagem, preços, durações e marcas de estimativa com a tabela da spec `service-catalog` do front.
- [x] 6.3 Implementar entidades JPA, repositórios Spring Data, adaptadores e mappers de entidade para as portas do `core`. Verificar: testes de adaptador com Testcontainers (salvar, buscar, filtros de data e profissional).

## 7. booking-service: autenticação e escopo

- [x] 7.1 Implementar o adaptador `PasswordHasher` (BCrypt) e `BootstrapAccounts`, que sincroniza master e `ADMIN_USERS` na subida, ignora profissional inexistente com aviso e incrementa `pwd_version` quando a senha muda. Verificar: testes de bootstrap (criação, senha alterada, id inválido, sem contas → `LOGIN_DISABLED`).
- [x] 7.2 Implementar `AuthUseCase` e o emissor JWT:
  - login sem diferenciar maiúsculas;
  - mesma mensagem para usuário e senha errados;
  - cookie `admin_session` HttpOnly de 12 h, com `SameSite` e `Secure` configuráveis;
  - logout.

  Verificar: testes de controller para login válido, inválido e 503.
- [x] 7.3 Implementar o filtro de sessão (assinatura, expiração, `pwd_version`), `GET /api/admin/me` e 401 `UNAUTHORIZED`. Verificar: testes de sessão expirada e de troca de senha derrubando só a conta afetada.
- [x] 7.4 Implementar `AccessScope` para as regras de 404 em recurso alheio, 403 `FORBIDDEN` em ação de master e filtro `professionalId` ignorado. Verificar: testes de use case cobrindo os cenários da spec `admin-auth`.
- [x] 7.5 Implementar o limite de tentativas de login por usuário e origem, com 429. Verificar: teste excede o limite e recebe 429, e depois da janela volta a aceitar.

## 8. booking-service: casos de uso e API

- [x] 8.1 Implementar o catálogo público e a disponibilidade: `GET /api/services`, `/api/availability` e `/api/availability/days`. Verificar: testes de contrato com JSON igual ao formato do front (`lib/domain/types.ts`), sem `whatsappPhone` nem marca de estimativa.
- [x] 8.2 Implementar `POST /api/appointments` e `GET /api/appointments/{id}`, com validação, 409 `SLOT_UNAVAILABLE` e gravação de `appointment.created` no outbox na mesma transação. Verificar: testes de 201, 400 com `fields`, 409 e evento gravado só no sucesso.
- [x] 8.3 Implementar a lista do painel com filtros e `conflict`, `pending-count` e `mark-seen` (só a dona marca). Verificar: testes dos cenários de conflito e de "master não marca como visto".
- [x] 8.4 Implementar as transições confirm, reject e cancel:
  - histórico e evento no outbox;
  - checagem prévia de conflito;
  - tradução de 23P01 para 409 `SLOT_TAKEN`;
  - 409 `INVALID_TRANSITION`.

  Verificar: teste concorrente com Testcontainers confirma dois pedidos sobrepostos em paralelo e aceita só um, gerando um único evento.
- [x] 8.5 Implementar os endpoints admin de serviços, categorias e profissionais (com `whatsappPhone` só leitura; PATCH com `whatsappPhone` → 400) e `accounts` (`login` `env`/`db`/`null`, sem senha). Verificar: testes dos cenários da spec `booking-api` e `admin-auth` (duração inválida, preço congelado, conta do ambiente protegida, login persistente após reinício do contexto).
- [x] 8.6 Implementar `PATCH /api/admin/me` `{ whatsappPhone }` (id só da sessão, normalização E.164, `null` ou vazio remove, master → 403) e incluir `whatsappPhone` em `GET /api/admin/me`. Verificar: testes dos cenários "Profissional cadastra o próprio número", "remove o número", "Master não altera" e "Número inválido" da spec `booking-api`, e um evento `appointment.created` gerado depois da troca com o número novo.
- [x] 8.7 Implementar `GET /api/admin/earnings` com validação de período (até 366 dias, fim depois do início). Verificar: teste do cenário "cancelado fora da soma" e 400 para período inválido.
- [x] 8.8 Implementar o `GlobalExceptionHandler` com o formato `{code,message,fields?}` em pt-BR para todas as exceções de domínio e aplicação. Verificar: teste por código de erro.

## 9. booking-service: outbox e eventos

- [x] 9.1 Implementar o adaptador `EventRecorder`, que monta o `AppointmentEvent` com `statusUrl` (`APP_PUBLIC_FRONT_URL/agendamento/{id}`) e o WhatsApp da profissional, e grava em `outbox_event`. Verificar: teste confere o payload completo para os 4 tipos.
- [x] 9.2 Implementar o relay agendado com `FOR UPDATE SKIP LOCKED`, ordem por `created_at`, parada no agregado com falha, `published_at` após confirmação do broker e limpeza diária. Verificar: teste Testcontainers com o broker parado mostra a operação concluída, o evento pendente e a publicação depois que o broker volta.

## 10. booking-service: fotos no MinIO

- [x] 10.1 Escrever `V3__foto_do_servico.sql` (colunas `photo_key` e `photo_content_type` em serviço) e implementar a porta `PhotoStorage` com adaptador MinIO, timeout e circuit breaker, garantindo na subida o bucket `service-photos` com leitura pública de objeto. Verificar: teste Testcontainers MinIO grava, lê e remove objeto.
- [x] 10.2 Implementar `ServicePhotoUseCase` e os endpoints `PUT` e `DELETE /api/admin/services/{id}/photo` (multipart, campo `photo`, 200 com o serviço), com validação por assinatura, limite de 5 MB, mensagens do front, escopo e chave gerada pelo sistema. Incluir `photoUrl` em `GET /api/services` e nas respostas de serviços do admin, e ignorar `photoUrl` no PATCH. Verificar: testes dos cenários da spec `service-photo-storage` (JPEG válido, troca muda URL, PDF renomeado, 6 MB, campo ausente, serviço alheio, remoção, MinIO parado com 503 e catálogo respondendo).

## 11. notification-service

- [x] 11.1 Criar o módulo com camadas `core`, `application` e `infra`, teste ArchUnit, Eureka client, virtual threads e Flyway `V1__notificacoes.sql` (tabela `notification` com único `(event_id, recipient_role)`). Verificar: o serviço sobe e aplica a migration.
- [x] 11.2 Implementar `MessageTemplate` com os textos pt-BR para profissional (novo pedido, com link do painel) e cliente (confirmado, recusado, cancelado, com motivo, link e WhatsApp do salão), datas por extenso e moeda pt-BR. Verificar: testes unitários comparando o texto dos cenários da spec `whatsapp-notification`.
- [x] 11.3 Implementar os use cases `NotifyProfessionalOnCreated` e `NotifyCustomerOnStatusChange` com idempotência, `SKIPPED` sem número, classificação de erro transitório e permanente e status final. Verificar: testes com fakes (duplicado, sem WhatsApp, erro permanente → `FAILED` sem retentar).
- [x] 11.4 Implementar os adaptadores `EvolutionApiSender` (RestClient, chave de API, instância, timeout, circuit breaker) e `LogOnlySender` (ativado por `NOTIFICATION_SEND_ENABLED=false`). Verificar: testes WireMock com 200, 5xx (retenta), número inexistente (`FAILED`) e circuito aberto.
- [x] 11.5 Implementar o listener RabbitMQ com ack manual, retry por fila TTL, DLQ para malformada e esgotada e máscara de telefone e nome nos logs. Verificar: teste Testcontainers RabbitMQ com evento duplicado (uma notificação), malformado (DLQ sem travar a fila) e Evolution fora do ar e depois de volta (entrega sem duplicação).

## 12. Integração ponta a ponta e documentação

- [x] 12.1 Subir a stack completa com `docker compose up -d` e conferir todos os contêineres `healthy`. Verificar: `docker compose ps` e `GET http://localhost:8080/api/services` com 21 serviços.
- [x] 12.2 Executar o fluxo completo com `NOTIFICATION_SEND_ENABLED=false`:
  - pedido criado;
  - log da mensagem à profissional;
  - confirmação no painel;
  - log da mensagem à cliente.

  Parar o booking-service e conferir 503 `SERVICE_UNAVAILABLE` no gateway. Verificar: roteiro registrado no README com as saídas esperadas.
- [x] 12.3 Apontar o front `../beauty-studio` para o gateway e percorrer os fluxos das specs do front (agendar, painel, serviços, ganhos, contas). Registrar divergências de contrato e os ajustes de front pendentes ("Meu WhatsApp", aviso na tela de agendamento, fotos, `login: "db"`). Verificar: lista de divergências vazia ou registrada como mudança no front.
- [x] 12.4 Escrever o README com:
  - arquitetura e diagrama dos serviços e eventos;
  - como rodar, variáveis e conexão do número na Evolution API por QR Code;
  - como a profissional informa o próprio WhatsApp no painel (`PATCH /api/admin/me`) e o efeito de não informar (`SKIPPED`);
  - extensões de contrato (`whatsappPhone`, `PATCH /api/admin/me`, `login: "db"`) e o contrato de foto (`photoUrl`);
  - riscos da Evolution API.

  Verificar: um teste de leitura segue o README do zero e sobe a stack.
