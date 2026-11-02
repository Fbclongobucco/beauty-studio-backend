## Context

- **Repositório atual:** vazio. Só tem `openspec/`.
- **Regras de negócio:** vêm das specs do front em `../beauty-studio/openspec/specs/`:
  - `appointment-booking`, `admin-appointments`, `admin-access`, `admin-earnings`, `service-catalog`, `mock-api`.
  - Os contratos HTTP estão no `README.md` do front e em `lib/api-client.ts`.
- **Sessão no front:**
  - usa `credentials: "include"`;
  - o `proxy.ts` do Next redireciona para o login quando falta o cookie `admin_session`.
  - O backend precisa usar esse mesmo nome de cookie.
- **Eventos no front:** `lib/server/events.ts` já define os 4 eventos e os dados que eles carregam.
- **Projeto de referência App-adm-obras:** Spring Boot 4.1.1 e Java 25.
  - **Pacotes:**
    - `core` tem domínio puro, portas de repositório, portas de segurança e exceções de domínio;
    - `application` tem use cases, DTOs, mappers e exceções de aplicação;
    - `infra` tem controllers, entidades JPA, repositórios Spring Data, adaptadores, mappers de entidade e config.
  - **Ligação dos use cases:** são classes Java puras, ligadas em `infra/config/UseCaseConfig` por `@Bean`, sem anotação Spring no core e no application.
  - **Infra e banco:** `Clock` injetado, Flyway com `ddl-auto=none`, `spring.threads.virtual.enabled` por variável e JWT HS256.
- Motivação: ver proposal.md. Requisitos: ver `specs/`.

## Goals / Non-Goals

**Goals:**
- Seguir o mesmo padrão de camadas do App-adm-obras em booking-service, notification-service e messaging-contracts, para manter um estilo único nos projetos do usuário.
- Manter o contrato do front, com extensões só aditivas.
- Garantir entrega de eventos sem perda e sem notificação duplicada.
- Subir tudo com `docker compose up`.

**Non-Goals:**
- Telas do front: cadastro de fotos, campo "Meu WhatsApp" no painel da profissional, aviso na tela de agendamento e texto de contas. Ficam para uma mudança no repositório do front.
- Lembretes agendados (ex.: 24 h antes). A topologia já permite um consumidor futuro.
- Spring Cloud Config Server, tracing distribuído e Kubernetes.
- Notificação por push ou e-mail.
- Pagamentos.

## Decisions

### D1. Monorepo Maven multi-módulo
Estrutura:
```
beauty-studio-backend/
  pom.xml                     (parent: versões, BOMs Spring Boot e Spring Cloud)
  messaging-contracts/        (jar)
  discovery-server/           (Eureka Server)
  api-gateway/                (Spring Cloud Gateway)
  booking-service/
  notification-service/
  infra/postgres/init/        (script que cria bancos e usuários)
  compose.yaml, .env.example
```
- **Alternativa:** um repositório por serviço. Fica pesado para um projeto pequeno, com versionamento cruzado do módulo de contratos.
- **Decisão:** o monorepo garante que publicador e consumidor compilam contra o mesmo contrato.
- **Versões:**
  - Spring Boot igual ao do App-adm-obras (4.1.x);
  - release train do Spring Cloud compatível com essa versão;
  - imagens Docker com tag fixa.
  - A compatibilidade e as tags exatas MUST ser conferidas na tabela oficial do Spring Cloud e no Docker Hub na tarefa 1.1, sem tag `latest`.

### D2. Clean Architecture por serviço, no padrão App-adm-obras
Pacote base: `com.longobuccodev.beautystudio.<servico>`.

**booking-service:**
- `core/domain`:
  - `Appointment` com a máquina de estados e o cálculo de fim;
  - `Service`, `Category`, `Professional`, `Account`;
  - `Schedule`, com as regras de expediente, grade, janela e fuso;
  - `Availability`, com a sobreposição de intervalos;
  - `Earnings`;
  - value objects `PhoneE164`, `Money` (centavos) e `Duration30`.
- `core/repository`:
  - portas de persistência;
  - `OutboxRepository`.
- `core/security`:
  - `PasswordHasher`;
  - `SessionTokenIssuer`.
- `core/event`: `DomainEvent` e a porta `EventRecorder`.
- `core/storage`: porta `PhotoStorage`.
- `core/exception`.
- `application/usecase`:
  - `BookingUseCase`, `AvailabilityUseCase`, `AdminAppointmentUseCase`, `CatalogUseCase`, `ProfessionalUseCase`, `AccountUseCase`, `AuthUseCase`, `EarningsUseCase`, `ServicePhotoUseCase`;
  - `AccessScope`: resolve o escopo master ou profissional e centraliza as regras de 403 e 404.
- `application/dto` e `application/mapper`.
- `infra`:
  - `controllers`, com os mesmos caminhos do front;
  - `entities`, `repositories` (Spring Data), `adapters/repository` e `adapters/mapper`;
  - `security`: filtro do cookie e JWT;
  - `messaging`: relay do outbox e publisher;
  - `storage`: adaptador MinIO;
  - `config`: `UseCaseConfig`, `ClockConfig` e `BootstrapAccounts`.

**notification-service:**
- `core/domain`: `Notification`, `NotificationStatus`, `Recipient`, `MessageTemplate`.
- `core/gateway`: porta `WhatsAppSender`.
- `core/repository`: `NotificationRepository`.
- `application/usecase`:
  - `NotifyProfessionalOnCreated`;
  - `NotifyCustomerOnStatusChange`.
- `infra`:
  - listener RabbitMQ;
  - adaptadores `EvolutionApiSender` e `LogOnlySender`;
  - JPA e config.

**messaging-contracts:**
- `core`: records Java do evento, sem dependência de framework:
  - `AppointmentEvent` e os records internos;
  - enum `AppointmentEventType` com as routing keys;
  - constante `CONTRACT_VERSION`.
- `application`: portas `EventPublisher` e `EventHandler<T>`.
- `infra`:
  - auto-configuração Spring AMQP com declaração de exchange, filas, DLQ e bindings;
  - conversor JSON;
  - publisher com publisher confirms;
  - base de listener com idempotência delegada.
- Os serviços dependem do módulo e usam só o que precisam.

- **Alternativa:** hexagonal com pacotes `port/adapter`. Não adotada, para manter a nomenclatura que o usuário já usa.
- **Regra de dependência:** `core` não importa nada de `application` nem de `infra`, e `application` não importa `infra`. A regra é verificada por teste ArchUnit em cada serviço.

### D3. Gateway WebMVC em vez do reativo
O Spring Cloud Gateway tem a variante Server WebMVC. Com virtual threads, ela atende alta concorrência com o modelo bloqueante e o mesmo estilo de código dos demais serviços.
- **Alternativa:** gateway reativo (WebFlux). Seria um segundo modelo de programação sem ganho na escala do salão.
- **Configuração do gateway:**
  - rotas `lb://booking-service` resolvidas pelo Eureka;
  - filtro `CircuitBreaker` (Resilience4j) com fallback local que devolve o JSON 503 `SERVICE_UNAVAILABLE`;
  - CORS global com `allowCredentials=true` e origens de `APP_CORS_ALLOWED_ORIGINS`;
  - rota `/media/**` para o MinIO.

### D4. Descoberta com Eureka
É o padrão do Spring Cloud e não exige infraestrutura extra.
- Os serviços se registram com `spring.application.name`.
- O notification-service se registra também, mesmo sem rota HTTP pública, para a saúde e para futuras rotas internas.
- **Alternativa:** Consul. Exigiria mais um contêiner externo.

### D5. Sessão: JWT em cookie `admin_session`, sem refresh token
- O spec pede sessão de 12 h com o cookie que o front já checa.
- O JWT é HS256, com segredo `APP_JWT_SECRET` e os claims `sub` (accountId), `role`, `professionalId`, `pwdVersion` e `exp` (12 h).
- O filtro valida a assinatura e compara `pwdVersion` com a conta no banco. A consulta é por chave primária, com cache curto de alguns segundos.
- Trocar a senha incrementa `pwdVersion` e derruba só as sessões daquela conta.
- **Alternativa:** access token mais refresh token, como no App-adm-obras. É mais complexo e não é exigido pelo contrato atual do front.
- **Contas do ambiente:** `BootstrapAccounts` sincroniza na subida. O hash muda só se a senha do ambiente não bater com o hash atual. Nesse caso, `pwdVersion` é incrementado.
- **Validação no gateway:** a autenticação é validada no booking-service, não no gateway. O gateway só repassa o cookie. Assim a regra de escopo fica num lugar só.
- **Limite de login:** feito em memória por instância, com janela deslizante.
  - Com mais de uma instância, o limite fica por instância. É aceitável na escala atual e está registrado em Riscos.

### D6. Concorrência de confirmação: exclusion constraint no Postgres
- Extensão `btree_gist`.
- Constraint em `appointment`:
  - `EXCLUDE USING gist (professional_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)`;
  - vale só `WHERE status = 'confirmed'`.
- Além disso, o use case faz a checagem prévia para responder com a mensagem certa. A violação da constraint (SQLState 23P01) é traduzida para 409 `SLOT_TAKEN`.
- **Por quê:** é a garantia correta mesmo com várias instâncias e confirmações simultâneas.
- **Fila por profissional:** antes da checagem prévia, a confirmação pega um advisory lock da transação (`pg_advisory_xact_lock`) por profissional. O teste concorrente mostrou que, sem ele, duas confirmações simultâneas se bloqueiam mutuamente dentro da exclusion constraint: o Postgres aborta uma com deadlock (40P01), não com 23P01. Com o lock, a segunda espera a primeira e recebe `SLOT_TAKEN` na checagem prévia. A constraint continua como garantia final.
- **Alternativas:**
  - lock pessimista por profissional: funciona, mas depende de disciplina de código;
  - verificação sem constraint: tem condição de corrida.

### D7. Outbox transacional com relay agendado
- **Tabela `outbox_event`:**
  - colunas `id` (eventId), `aggregate_id`, `type`, `payload jsonb`, `created_at`, `published_at` e `attempts`;
  - índice parcial `WHERE published_at IS NULL`.
- **Gravação:** o use case chama a porta `EventRecorder` dentro da mesma transação.
- **Relay:**
  - `@Scheduled` com intervalo configurável, padrão de 1 s;
  - lê lotes com `FOR UPDATE SKIP LOCKED`, ordenados por `created_at`, para ser seguro com várias instâncias;
  - publica com publisher confirms e marca `published_at` após o ack.
- **Ordem:** o relay processa em ordem e para o lote de um agregado na primeira falha, preservando a ordem por agendamento.
- **Limpeza:** job diário apaga eventos publicados há mais de N dias (configurável).
- **Alternativas:**
  - publicar direto após o commit: perde evento se o broker estiver fora;
  - Debezium/CDC: infraestrutura desproporcional.

### D8. Topologia RabbitMQ
- **Exchange principal:** `beauty.appointments` (topic, durável).
- **Fila do notification-service:** `notification.appointment-events`, ligada a `appointment.*`.
- **Retentativa com backoff:**
  - via fila de espera `notification.appointment-events.retry`, com TTL por mensagem e dead-letter de volta à fila principal;
  - intervalos de 10 s, 1 min e 5 min (configuráveis), com no máximo 5 tentativas (configurável).
- **DLQ:** depois das tentativas, a mensagem vai para `notification.appointment-events.dlq` pela exchange `beauty.appointments.dlx`.
- **Mensagens:**
  - entrega persistente;
  - `content-type: application/json`;
  - header `x-event-version`;
  - `message-id = eventId`.
- **Consumo:** `prefetch` baixo e ack manual após o processamento.
- **Alternativa:** retry só em memória do Spring Retry. Bloqueia a thread do consumidor durante quedas longas da Evolution API.

### D9. Notification-service: idempotência e status
- Tabela `notification` com chave única `(event_id, recipient_role)`.
- **Fluxo:**
  - insere `PENDING`;
  - em caso de conflito na chave e status final, faz ack e ignora;
  - envia;
  - atualiza para `SENT`, `LOGGED`, `SKIPPED` ou `FAILED`.
- **Classificação dos erros da Evolution API:**
  - 5xx, timeout ou circuito aberto: transitório, retentar;
  - número inexistente ou 4xx de validação: permanente, `FAILED` sem retentativa.
- **Proteção do adaptador:** `@CircuitBreaker` e `@TimeLimiter`/timeout do `RestClient` (Resilience4j).
- **Textos:** templates pt-BR em classes do domínio (`MessageTemplate`), com formatação de data e moeda em `Locale("pt","BR")` e fuso America/Sao_Paulo.
- **Links:** vêm de `APP_PUBLIC_FRONT_URL`:
  - `statusUrl` já chega no evento;
  - link do painel = `APP_PUBLIC_FRONT_URL + /admin`.

### D10. Evolution API no compose
- Contêiner da Evolution API v2, com banco próprio no mesmo Postgres e chave de API em `EVOLUTION_API_KEY`.
- **Instância:** o nome é configurável. A conexão do número é feita uma vez, por QR Code, no gerenciador da Evolution, publicado só em ambiente local.
- As variáveis exatas de ambiente da imagem (banco, cache, chave) MUST ser conferidas na documentação oficial da versão fixada, na tarefa de infra. Não estão definidas aqui para não fixar nome de variável não verificado.
- **Alternativa:** Meta Cloud API. Descartada pelo usuário nesta fase. A porta `WhatsAppSender` mantém a troca barata.

### D11. Fotos no MinIO
- **Imagem:** o repositório oficial `minio/minio` foi arquivado no GitHub em abr/2026, e a imagem saiu do Docker Hub. Escolha do usuário: `chainguard/minio`, que no plano gratuito só tem a tag `latest`, fixada pelo digest `sha256` no compose.
- **Bucket:** `service-photos`, garantido pelo booking-service na subida (cria se não existir e aplica a política de leitura pública só para `GetObject`). Dispensa o contêiner `minio-init` e serve também aos testes com Testcontainers.
- **Contrato:** o da mudança `add-service-photo` do front. Uma foto opcional por serviço, `photoUrl`, `PUT`/`DELETE /api/admin/services/{id}/photo` e campo multipart `photo`. A versão anterior deste design previa várias fotos; foi alinhada ao front por decisão do usuário.
- **Chave do objeto:** `services/{serviceId}/{uuid}.{ext}`. Um uuid novo a cada troca faz a URL mudar e invalida caches.
- **Metadados:** colunas `photo_key` e `photo_content_type` na tabela de serviço. Não há tabela separada, porque é uma foto por serviço.
- **Validação do tipo:** pelos magic bytes, com Apache Tika ou leitura manual dos cabeçalhos JPEG, PNG e WebP.
- **Ordem das operações:**
  - troca: grava o novo objeto, depois atualiza o serviço e remove o objeto antigo. Se o banco falhar, remove o objeto novo;
  - remoção: apaga o objeto, depois limpa o campo no serviço. Com o MinIO fora do ar, responde 503 e o serviço mantém a foto, como exige a spec `service-photo-storage`.
- **URL pública:** `${APP_PUBLIC_MEDIA_URL}/media/service-photos/{key}`, servida pelo gateway, que encaminha `/media/**` ao MinIO reescrevendo o caminho.
- **Alternativa:** URLs pré-assinadas. Expiram e não servem a um catálogo público em cache.
- **Limite** (configurável):
  - 5 MB por arquivo, igual ao `PHOTO_MAX_BYTES` do front.

### D12. Banco e migrations
- **Bancos:** um contêiner Postgres com bancos `booking`, `notification` e `evolution` e usuários próprios, criados por `infra/postgres/init/01-databases.sh`.
- **Flyway por serviço:** em `src/main/resources/db/migration`, com nomes `V{n}__descricao_em_portugues.sql`, como no App-adm-obras.
- **Migrations do booking:**
  - `V1__esquema_inicial.sql`: profissional, categoria, serviço, agendamento, histórico de status, conta, `outbox_event` e `btree_gist`;
  - `V2__catalogo_inicial.sql`: profissionais `stefani`, `lorrayne` e `michele`, categorias, 21 serviços, observações e a marca `duration_estimated`. Valores copiados de `../beauty-studio/lib/server/seed.ts`.
  - `V3__fotos_dos_servicos.sql`.
- **Ids:** profissionais, categorias e serviços com id texto (slug, como `box-braids` no seed do front), para manter o contrato e os links já usados. Novos itens recebem slug gerado do nome, com sufixo `-2`, `-3` em caso de repetição, como na API de teste. Agendamentos com UUID.
- **JPA:** `open-in-view=false`, `ddl-auto=none` e `default_batch_fetch_size`, como no App-adm-obras.

### D13. Testes
- **Domínio:** testes unitários puros, portando os casos de `lib/domain/*.test.ts` do front (horários, disponibilidade, status, validação, ganhos).
- **Use cases:** com fakes das portas.
- **Integração com Testcontainers:**
  - Postgres: constraint de exclusão, migrations e outbox;
  - RabbitMQ: publicação, consumo, retentativa e DLQ;
  - MinIO.
- **Controllers:** testes de contrato comparando JSON com os formatos do front.
- **Arquitetura:** ArchUnit nas camadas.
- **Evolution API:** stub HTTP (WireMock) para os casos de erro transitório e permanente.

### D14. WhatsApp da profissional por autoatendimento
- **Endpoint:** a profissional informa o próprio número em `PATCH /api/admin/me` `{ whatsappPhone }`. O id vem só da sessão, nunca do corpo nem da URL, então não há como alterar o número de outra pessoa.
- **Normalização:** o número passa pelo mesmo value object `PhoneE164` do telefone da cliente.
- **Leitura:**
  - `GET /api/admin/me` devolve `whatsappPhone`, para o front mostrar um aviso quando estiver vazio;
  - `GET /api/admin/professionals` devolve o número só para leitura;
  - `PATCH /api/admin/professionals/{id}` com `whatsappPhone` responde 400.
- **Alternativa:** o master editar pelo cadastro de profissionais. Descartada pelo usuário: o número é dado pessoal da profissional e quem confirma os pedidos é ela.
- **Eventos:** o número vai no evento no momento em que ele é gravado no outbox (D7). Uma troca de número não altera eventos já gravados. É o comportamento esperado de transferência de estado por evento.
- **Cliente:** nada muda no fluxo. O `customerPhone` já obrigatório no `POST /api/appointments` é guardado em E.164 e vai no evento como destino dos avisos de status.

## Risks / Trade-offs

- **Bloqueio do número pelo WhatsApp (Evolution API não oficial):** usar volume baixo e só mensagens transacionais. A porta `WhatsAppSender` permite migrar para a Meta Cloud API. O status `FAILED` fica visível no banco.
- **Compatibilidade de versões entre Spring Boot 4.1 e o release train do Spring Cloud:** conferir na tarefa 1.1. Se não houver train compatível com 4.1, alinhar o Boot à versão suportada mais recente e registrar a diferença em relação ao App-adm-obras.
- **Cookie entre front e gateway em domínios diferentes:**
  - em produção, servir front e API sob o mesmo site (proxy reverso ou subdomínios do mesmo domínio) com `SameSite=Lax`;
  - `SameSite=None; Secure` fica configurável para o caso de sites diferentes.
- **Limite de tentativas de login por instância (D5):** aceitável com uma instância. Mover para Redis ou banco se houver escala horizontal.
- **Profissional sem número configurado não recebe aviso de pedido novo:** o evento fica `SKIPPED`, e o painel continua mostrando o pedido como "Novo". O front deve mostrar um aviso no painel enquanto `whatsappPhone` for nulo (registrado como ajuste no front).
- **Entrega ao menos uma vez:** mitigada pela idempotência por `eventId` e destinatário (D9).
- **Muitos contêineres para um servidor pequeno:**
  - definir `mem_limit` e heap por serviço no compose, como no App-adm-obras;
  - medir o consumo real na primeira subida, sem prometer número antes disso.
- **Extensões de contrato quebrarem o front:** são só aditivas.
  - O valor `login: "db"` exige ajuste de tipo no front. Até lá, o front mostra o texto genérico.
  - O ajuste deve ser registrado como mudança no repositório do front.

## Migration Plan

1. Subir a stack com o compose em ambiente local. Conectar o número na Evolution API por QR Code. Cada profissional informa o próprio WhatsApp no painel.
2. Apontar o front local para o gateway (`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`) e validar os fluxos das specs do front.
3. Produção:
   - definir `.env` com segredos fortes, `APP_CORS_ALLOWED_ORIGINS`, `APP_PUBLIC_FRONT_URL` e `APP_PUBLIC_MEDIA_URL`;
   - publicar só a porta do gateway, atrás de HTTPS.
4. **Rollback:**
   - basta voltar `NEXT_PUBLIC_API_BASE_URL` para vazio, o que volta a usar a API em memória do front;
   - não há migração de dados, porque a API de teste não persiste nada;
   - os dados do backend ficam preservados nos volumes.

## Open Questions

- **Número do salão a conectar na Evolution API e `SALON_WHATSAPP`:** dados do salão, definidos no `.env`. Não mudam o desenho. Os números das profissionais são informados por elas no painel.
- **Hospedagem de produção** (VPS única ou outra): não muda o compose. Afeta só a definição de `mem_limit` final.
