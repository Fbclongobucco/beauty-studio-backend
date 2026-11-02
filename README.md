# beauty-studio-backend

Backend do **Longo Bucco Beauty Studio**: agenda, catálogo, painel das profissionais e avisos por WhatsApp. São microsserviços orientados a eventos, com Java 25, Spring Boot 4.1 e Spring Cloud 2025.1, e virtual threads em todos os serviços.

Ele substitui a API de teste em memória do front-end (`../beauty-studio`), mantendo os mesmos endereços e formatos JSON de `/api/*`.

## Arquitetura

```
                 navegador (front Next.js)
                          │  cookie admin_session
                          ▼
                 ┌──────────────────┐        ┌──────────────────┐
                 │   api-gateway    │◄──────►│ discovery-server │  Eureka
                 │ CORS, circuit    │        └──────────────────┘
                 │ breaker, /media  │                 ▲
                 └───┬─────────┬────┘                 │ registro
            /api/**  │         │ /media/**            │
                     ▼         ▼                      │
         ┌─────────────────┐  ┌───────┐               │
         │ booking-service │─►│ MinIO │ fotos         │
         │  Clean Arch     │  └───────┘               │
         │  PostgreSQL     │ outbox ──► RabbitMQ ─────┼──────────────┐
         └─────────────────┘   exchange beauty.appointments (topic)  │
                                                                     ▼
                                                     ┌──────────────────────┐   ┌───────────────┐
                                                     │ notification-service │──►│ Evolution API │──► WhatsApp
                                                     │  Clean Arch          │   └───────────────┘
                                                     └──────────────────────┘
```

Módulos Maven:

- **`messaging-contracts`**: eventos de agendamento, topologia do RabbitMQ (exchange, filas de espera, DLQ), publicador com confirmação do broker e consumidor com retentativa.
- **`discovery-server`**: Eureka.
- **`api-gateway`**: Spring Cloud Gateway (WebMVC).
  - É a única porta exposta.
  - Roteia `/api/**` para o booking-service, localizado pelo Eureka, e `/media/**` para as fotos no MinIO.
  - Cuida do CORS do front e tem circuit breaker com resposta 503 padronizada.
- **`booking-service`**:
  - regras de agenda, disponibilidade, status e conflito;
  - catálogo, contas do painel e ganhos;
  - fotos dos serviços.
  - Grava os eventos numa tabela outbox, na mesma transação da mudança.
- **`notification-service`**: consome os eventos e envia WhatsApp pela Evolution API.

Os serviços de aplicação seguem a Clean Architecture do projeto App-adm-obras:
- `core` tem domínio e portas, sem framework.
- `application` tem os casos de uso em Java puro.
- `infra` tem controllers, JPA, adaptadores e configuração.
- Testes ArchUnit garantem as regras de dependência.

### Eventos

Exchange `beauty.appointments` (topic). A routing key é o tipo do evento:

- `appointment.created`: pedido novo. O notification-service avisa a profissional.
- `appointment.confirmed`, `appointment.rejected`, `appointment.cancelled`: o notification-service avisa a cliente.

O evento carrega tudo o que a mensagem precisa:
- agendamento, serviço, profissional (com o WhatsApp dela) e cliente;
- `statusUrl`, que é o link público do pedido.

Contrato: `messaging-contracts/.../core/AppointmentEvent.java`, versão em `EventContract.CONTRACT_VERSION`.

**Entrega garantida:**
- **Gravação:** o evento é gravado junto com a mudança. Com o RabbitMQ fora do ar, o pedido ou a confirmação funcionam normalmente, e o evento sai quando o broker volta.
- **Publicação:** o relay publica na ordem de gravação e só marca como publicado depois da confirmação do broker.
- **Consumo:**
  - o notification-service registra cada notificação por evento e destinatário, então evento repetido não gera mensagem duplicada;
  - falhas temporárias são retentadas em 10 s, 1 min e 5 min, até 5 tentativas;
  - depois disso, ou se a mensagem vier malformada, ela vai para a fila `notification.appointment-events.dlq`.

## Como rodar

Pré-requisito: Docker com Compose. Há dois perfis. Os dois constroem as imagens na própria máquina, sem registry.

```
perfil	arquivos	.env	perfil Spring	imagens	projeto compose
local	compose.yaml + compose.local.yaml	.env.local	local	beauty-studio/*:local	beauty-local
prod	compose.yaml + compose.prod.yaml	.env.prod	prod	beauty-studio/*:prod	beauty-prod
```

Cada perfil tem volumes próprios: os dados do local e do prod não se misturam. O `stack.sh` monta o comando do compose com os arquivos certos e repassa os argumentos.

**Local (desenvolvimento):**

```bash
./stack.sh local up -d --build   # 1ª vez: cria .env.local com segredos gerados
./stack.sh local ps              # todos "healthy" em 1 a 2 minutos
curl http://localhost:8080/api/services
```

- WhatsApp só no log (`NOTIFICATION_SEND_ENABLED=false`).
- Saúde com detalhes e log do sistema em DEBUG.
- Portas de gestão, só em `127.0.0.1`:
  - RabbitMQ: http://localhost:15672
  - Console do MinIO: http://localhost:9001
  - Evolution API e gerenciador: http://localhost:8081/manager
  - Eureka: http://localhost:8761
  - Postgres: `localhost:5432`
- Senha do master: `ADMIN_PASSWORD` em `.env.local`.

**Prod (servidor):** passo a passo completo, com Vercel, DNS e HTTPS, em [DEPLOY.md](DEPLOY.md).


```bash
./stack.sh prod ps               # 1ª vez: cria .env.prod com segredos gerados e para
# preencha em .env.prod: APP_PUBLIC_FRONT_URL, APP_PUBLIC_MEDIA_URL (https), APP_CORS_ALLOWED_ORIGINS,
# EVOLUTION_SERVER_URL, SALON_WHATSAPP, ADMIN_USERS
./stack.sh prod up -d --build
```

Diferenças em relação ao local:
- **Perfil Spring `prod`:**
  - cookie de sessão sempre `Secure`;
  - o booking-service não sobe com URL pública sem HTTPS (localhost é aceito, para testar o perfil na máquina);
  - saúde e erros sem detalhes internos;
  - painel do Eureka desligado.
- **Compose:**
  - URLs públicas, CORS e senha do master obrigatórias;
  - envio real de WhatsApp ligado por padrão;
  - nenhuma porta de gestão publicada; só a Evolution, em `127.0.0.1:${EVOLUTION_LOCAL_PORT}`, para ler o QR Code por túnel SSH (`ssh -L 8081:127.0.0.1:8081 servidor`);
  - rotação de log (10 MB × 5 arquivos por contêiner).
- HTTPS pelo Caddy (`infra/caddy/Caddyfile`), única entrada pública; o gateway fica só na rede interna. Testado nesta máquina com `API_DOMAIN=localhost` (certificado local); ainda não testado em servidor real.

Front-end apontando para o backend:

```bash
cd ../beauty-studio
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

Testes (exigem Docker, por causa do Testcontainers):

```bash
./mvnw test
```

### Variáveis

Todas estão em `.env.local.example` e `.env.prod.example`, com comentários. Os arquivos `.env.local` e `.env.prod` ficam fora do git. As principais:

- **`POSTGRES_PASSWORD`, `BOOKING_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD`, `EVOLUTION_DB_PASSWORD`**: um banco e um usuário por serviço. Obrigatórias.
- **`RABBITMQ_PASSWORD`, `MINIO_ROOT_PASSWORD`, `EVOLUTION_API_KEY`**: obrigatórias.
- **`APP_JWT_SECRET`**: segredo da sessão do painel, com 32 caracteres ou mais. Obrigatória.
- **`ADMIN_USERNAME` / `ADMIN_PASSWORD`**: conta master. Sem senha, o master fica desabilitado.
- **`ADMIN_USERS`**: contas das profissionais definidas no ambiente, no formato `stefani:senha1;lorrayne:senha2`. Prevalecem sobre o painel.
- **`NOTIFICATION_SEND_ENABLED`**: `false` só registra a mensagem no log; `true` envia pela Evolution API.
- **`SALON_WHATSAPP`**: WhatsApp do salão em E.164. Vai nas mensagens de recusa e cancelamento. Vazio = a linha de contato não aparece.
- **`APP_PUBLIC_FRONT_URL`**: base dos links do painel e do status do pedido nas mensagens.
- **`APP_PUBLIC_MEDIA_URL`**: URL pública do gateway, base das fotos.
- **`APP_CORS_ALLOWED_ORIGINS`**: origens do front.
- **`APP_COOKIE_SECURE`, `APP_COOKIE_SAME_SITE`**: no perfil prod, `Secure` é sempre ligado; `SameSite` padrão `Lax`.
- **`LOGIN_TRUSTED_PROXY_HOPS`**: padrão `1`, só o gateway. Use `2` se houver um proxy reverso (HTTPS) na frente do gateway. Define qual entrada do `X-Forwarded-For` vale como origem no limite de tentativas de login.

## Acompanhamento do pedido

A cliente acompanha o pedido sem login, no mesmo aparelho:

- **Cookie `my_requests`:** gravado pela API ao criar o pedido (`POST /api/appointments`) e ao abrir o link do pedido (`GET /api/appointments/{id}`), inclusive o link do WhatsApp aberto em outro aparelho.
  - `HttpOnly`, caminho `/api/appointments`, validade de 60 dias renovada a cada gravação; `Secure` e `SameSite` iguais aos do cookie do painel.
  - Guarda só os ids de até 10 pedidos, num token assinado com `APP_JWT_SECRET` (audiência própria, que não vale como sessão do painel). Nenhum dado da cliente.
  - Valor adulterado, expirado ou inválido conta como vazio.
- **`GET /api/appointments/mine`:** pedidos do cookie em andamento, do início mais próximo para o mais distante, no formato público de `GET /api/appointments/{id}`: pendentes, confirmados que ainda não terminaram, e recusados ou cancelados com horário ainda no futuro. Sem cookie, `{ "appointments": [] }`.
- Como no painel, o navegador só envia o cookie com front e API no mesmo site.
- Trocar o `APP_JWT_SECRET` invalida esses cookies e as sessões do painel.

## WhatsApp

### Situação: envio suspenso

O envio real de WhatsApp está **desligado** (`NOTIFICATION_SEND_ENABLED=false` nos dois perfis). Os avisos continuam sendo gerados e registrados no log do notification-service e na tabela `notification`, mas nada é enviado.

Motivo: a Evolution API v2.3.7 não consegue parear aparelho novo desde uma mudança do WhatsApp em 07/2026 (defeito aberto, sem correção oficial; [issue #2696](https://github.com/evolution-foundation/evolution-api/issues/2696), [PR #2727](https://github.com/evolution-foundation/evolution-api/pull/2727)). Uma adaptação dessa correção está em `infra/evolution/`, **fora de uso** e não validada (o pareamento não concluiu no teste).

Para retomar:
- `./whatsapp.sh status | conectar | reiniciar | real | log` no perfil local;
- `./whatsapp.sh sandbox` mostra as mensagens que seriam enviadas em http://localhost:8099, sem enviar nada, e permite simular falhas (`POST localhost:8099/mode` com `{"mode": "invalid-number" | "down" | "ok"}`).

### Números das profissionais e das clientes

- **Profissional:** informa o próprio WhatsApp no painel dela. A API é `PATCH /api/admin/me` com `{ "whatsappPhone": "(21) 99999-0000" }`; `null` ou vazio remove o número.
  - O master só vê o número, em `GET /api/admin/professionals`, e não consegue alterar.
  - Enquanto a profissional não informar o número, os avisos de pedido novo dela ficam como `SKIPPED` na tabela `notification`, e o pedido continua aparecendo como "Novo" no painel.
- **Cliente:** recebe os avisos de status no WhatsApp que informou ao agendar.

### Consultar o que foi enviado

Banco `notification`, tabela `notification`:

```sql
select event_type, recipient_role, status, attempts, last_error, updated_at
from notification order by updated_at desc limit 20;
```

Status:
- `SENT`: enviado;
- `LOGGED`: só log;
- `SKIPPED`: sem número;
- `FAILED`: erro permanente ou tentativas esgotadas.

Nos logs, telefones e nomes aparecem mascarados (`+55219****7976`, `M***`). Só o modo somente-log mostra o texto completo.

### Riscos da Evolution API

- **Não é oficial:** o WhatsApp pode bloquear o número conectado. Use volume baixo e só mensagens transacionais (é o que o sistema envia).
- **Sem perda de mensagem:** com a Evolution API fora do ar, as mensagens são retentadas. Esgotadas as tentativas, ficam `FAILED` e vão para a DLQ.
- **Troca de provedor:** o envio passa pela porta `WhatsAppSender`. Migrar para a API oficial da Meta exige só um novo adaptador.

## Roteiro de verificação ponta a ponta

Com `NOTIFICATION_SEND_ENABLED=false`, `ADMIN_USERS=stefani:stefani1;...` e o front em `http://localhost:3000`:

```bash
G=http://localhost:8080
# 1. A Stéfani entra e informa o próprio WhatsApp
curl -s -c jar -H 'Content-Type: application/json' -d '{"username":"stefani","password":"stefani1"}' $G/api/admin/login
curl -s -b jar -X PATCH -H 'Content-Type: application/json' -d '{"whatsappPhone":"(21) 99999-0000"}' $G/api/admin/me
# 2. Uma cliente pede Manicure
curl -s -H 'Content-Type: application/json' \
  -d '{"serviceId":"manicure","date":"2026-11-06","time":"11:00","customerName":"Maria da Silva","customerPhone":"(21) 98888-7777"}' \
  $G/api/appointments
# 3. Log do notification-service
./stack.sh local logs notification-service | grep -A10 "WhatsApp somente log"
# 4. A Stéfani confirma (use o id da resposta do passo 2)
curl -s -b jar -X POST -H 'Content-Type: application/json' -d '{}' $G/api/admin/appointments/<id>/confirm
# 5. Booking parado: o gateway responde 503 padronizado
./stack.sh local stop booking-service && curl -s $G/api/services; ./stack.sh local start booking-service
```

Saídas esperadas:

- **Passo 1:** `{"ok":true}` e a sessão com `"whatsappPhone":"+5521999990000"`.
- **Passo 2:** 201 com `"status":"pending"`, `"priceCents":2000` e `"startISO":"2026-11-06T11:00:00-03:00"`.
- **Passo 3:** a mensagem para `+5521999990000`:
  ```
  Novo pedido de agendamento - Longo Bucco Beauty Studio

  Cliente: Maria da Silva
  Serviço: Manicure
  Data: sexta-feira, 6 de novembro
  Horário: 11:00
  Valor: R$ 20,00

  Confirme ou recuse no painel: http://localhost:3000/admin
  ```
  e a linha `Notificacao LOGGED ... destinatario=PROFESSIONAL telefone=+55219****0000`.
- **Passo 4:** `"status":"confirmed"`. O log mostra a mensagem para `+5521988887777`, com `Situação do seu agendamento: Confirmado` e o link `http://localhost:3000/agendamento/<id>`.
- **Passo 5:** `{"code":"SERVICE_UNAVAILABLE","message":"Serviço temporariamente indisponível. Tente de novo em instantes."}` com HTTP 503.

## Contrato com o front-end

Os endpoints, campos, códigos HTTP e códigos de erro são os da API de teste (`../beauty-studio/README.md` e `lib/api-client.ts`). Uma varredura de todos os endpoints do `api-client` pelo gateway não encontrou campo faltando.

### Extensões aditivas

O front atual funciona sem mudar, mas precisa de ajuste para aproveitar estas extensões:

- **WhatsApp da profissional:**
  - `PATCH /api/admin/me` `{ whatsappPhone }` é novo;
  - `whatsappPhone` aparece em `GET /api/admin/me` e nas profissionais do painel;
  - `PATCH /api/admin/professionals/{id}` (nome e ativo, só master) é novo.
- **Contas:** em `GET /api/admin/accounts`, `login` ganha o valor `"db"`. Logins criados pelo painel agora persistem e não somem ao reiniciar.
- **Erros novos:** `TOO_MANY_ATTEMPTS` (429, limite de tentativas de login) e `SERVICE_UNAVAILABLE` (503, gateway ou MinIO). O front já mostra a `message` do corpo.
- **Horários ocupados:** `GET /api/availability` traz também `taken`, os horários da grade do dia ocupados por agendamento confirmado da profissional, para o front mostrar em cinza. Não informa quem ocupa.
- **Foto do serviço:** segue o contrato da mudança `add-service-photo` do front: `photoUrl`, `PUT` e `DELETE /api/admin/services/{id}/photo`, campo `photo`, 5 MB. A `photoUrl` é absoluta, servida pelo gateway em `/media/...`.

### Ajustes no front

Feitos no repositório `beauty-studio-front`, na mudança `adapt-front-to-backend`:
- tipos com `whatsappPhone`, login `"db"` e os erros `TOO_MANY_ATTEMPTS` e `SERVICE_UNAVAILABLE`;
- bloco "Meu WhatsApp" da profissional e aviso enquanto o número estiver vazio;
- número só para leitura na tela do master;
- aviso no agendamento de que as atualizações do pedido chegam no WhatsApp informado.

## Estrutura

```
pom.xml                 parent (versões de Spring Boot e Spring Cloud conferidas em 2026-11-02)
messaging-contracts/    eventos, topologia RabbitMQ, publicador e consumidor
discovery-server/       Eureka
api-gateway/            rotas, CORS, circuit breaker, /media
booking-service/        core / application / infra + migrations Flyway (db/migration)
notification-service/   core / application / infra + migration Flyway
infra/postgres/init/    cria os bancos e usuários na primeira subida
compose.yaml            base comum (só o gateway publica porta)
compose.local.yaml      perfil local: portas de gestão, perfil Spring local, sandbox do WhatsApp (perfil whatsapp-sandbox)
compose.prod.yaml       perfil prod: Caddy (HTTPS), variáveis obrigatórias, rotação de log, perfil Spring prod
infra/caddy/Caddyfile   proxy HTTPS da API em produção
DEPLOY.md               roteiro de subida no servidor (VPS + Vercel)
stack.sh                atalho: ./stack.sh local|prod <comando do compose>
whatsapp.sh             modos de envio do WhatsApp no perfil local (log, sandbox, real) e conexão do número
infra/whatsapp-sandbox  sandbox que imita o envio da Evolution API
.env.*.example          variáveis de cada perfil
openspec/               especificações e plano das mudanças
```

## Observações

- **MinIO:** o repositório oficial `minio/minio` foi arquivado e a imagem saiu do Docker Hub. O compose usa `chainguard/minio`, fixado por digest. Para atualizar, troque o digest em `compose.yaml` e nos testes.
- **`mem_limit` do compose:** valores iniciais, ainda não medidos em servidor real. Meça o consumo na primeira subida antes de dimensionar a máquina.
- **Limite de tentativas de login:** fica em memória, por instância do booking-service.
- **Logout:** apaga o cookie. A credencial (JWT, 12 h) não é revogada no servidor, como na API de teste. Trocar a senha da conta derruba todas as sessões dela.
