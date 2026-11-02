## Purpose

Define como os serviços do backend do salão são compostos, descobertos, expostos e protegidos contra falhas em cascata. Também define como a infraestrutura sobe com um único comando no Docker Compose.

## ADDED Requirements

### Requirement: Subida da plataforma por Docker Compose
O repositório SHALL ter um arquivo Docker Compose que sobe, com um único comando, todos os componentes:
- banco PostgreSQL;
- broker RabbitMQ com interface de gestão;
- armazenamento de objetos MinIO;
- Evolution API (WhatsApp);
- servidor de descoberta;
- gateway;
- booking-service;
- notification-service.

Cada componente MUST ter healthcheck. Um serviço MUST iniciar só depois que as dependências dele estiverem saudáveis. Segredos MUST vir de variáveis de ambiente documentadas em `.env.example`. Nenhuma senha real MUST ficar versionada.

#### Scenario: Subida completa
- **WHEN** o operador copia `.env.example` para `.env`, preenche as senhas e executa `docker compose up -d`
- **THEN** todos os contêineres ficam com estado `healthy`, e o gateway responde `GET /api/services` com o catálogo inicial

#### Scenario: Senha obrigatória ausente
- **WHEN** a senha do PostgreSQL não está definida no `.env`
- **THEN** o Docker Compose recusa a subida com mensagem indicando a variável faltante

### Requirement: Gateway como única porta pública da API
O gateway SHALL ser o único ponto de entrada HTTP da aplicação. Ele SHALL rotear:
- `/api/**` para o booking-service;
- `/media/**` para as fotos públicas do catálogo.

Os serviços internos, o banco, o broker, o MinIO e a Evolution API MUST NOT ter porta publicada no host em produção. O gateway SHALL aplicar CORS só às origens configuradas, com credenciais (cookies) permitidas.

#### Scenario: Front aponta para o gateway
- **WHEN** o front-end é configurado com `NEXT_PUBLIC_API_BASE_URL` igual à URL do gateway
- **THEN** todas as telas públicas e do painel funcionam pelos mesmos caminhos `/api/*`

#### Scenario: Origem não autorizada
- **WHEN** um navegador em uma origem fora da lista configurada chama a API com credenciais
- **THEN** a resposta não traz cabeçalhos CORS que autorizem essa origem

### Requirement: Descoberta de serviços
Cada serviço de aplicação SHALL se registrar no servidor de descoberta com o próprio nome lógico. O gateway SHALL localizar o booking-service pelo nome lógico, sem endereço fixo. Uma instância que deixa de responder MUST sair do roteamento.

#### Scenario: Segunda instância do booking
- **WHEN** uma segunda instância do booking-service sobe e se registra
- **THEN** o gateway passa a distribuir requisições entre as duas instâncias sem mudança de configuração

### Requirement: Circuit breaker no gateway
Cada rota do gateway SHALL ter um circuit breaker. Quando o serviço de destino falha ou passa do tempo limite configurado, o gateway MUST responder 503 com corpo JSON `{ "code": "SERVICE_UNAVAILABLE", "message": "Serviço temporariamente indisponível. Tente de novo em instantes." }`. O formato MUST ser o mesmo de erro que o front já trata. Com o circuito aberto, o gateway MUST responder de imediato, sem esperar o serviço.

#### Scenario: Booking fora do ar
- **WHEN** o booking-service está parado e a cliente abre a lista de serviços
- **THEN** o gateway responde 503 `SERVICE_UNAVAILABLE` em JSON, sem expor detalhes internos

#### Scenario: Recuperação
- **WHEN** o booking-service volta e o tempo de espera do circuito aberto termina
- **THEN** as próximas requisições voltam a ser atendidas normalmente

### Requirement: Virtual threads
Os serviços de aplicação (gateway, booking-service, notification-service e servidor de descoberta) SHALL atender requisições HTTP e processar mensagens em virtual threads. Uma variável de ambiente SHALL permitir desligar as virtual threads para comparação. O limite de conexões com o banco SHALL ser configurável por ambiente.

#### Scenario: Virtual threads ativas
- **WHEN** o booking-service recebe uma requisição com a configuração padrão
- **THEN** a requisição é processada em uma virtual thread

### Requirement: Banco por serviço e migrations versionadas
Cada serviço com dados SHALL ter um banco lógico próprio no PostgreSQL, com usuário próprio:
- booking;
- notification;
- Evolution API.

Um serviço MUST NOT ler as tabelas de outro. O esquema de cada serviço SHALL ser criado e evoluído só por migrations versionadas e aplicadas na inicialização. A aplicação MUST NOT criar nem alterar tabelas por conta própria.

#### Scenario: Primeira subida
- **WHEN** o booking-service sobe com o banco vazio
- **THEN** as migrations criam o esquema e o catálogo inicial, e o histórico registra cada versão aplicada

#### Scenario: Migration já aplicada alterada
- **WHEN** o conteúdo de uma migration já aplicada é modificado
- **THEN** o serviço falha ao iniciar e aponta a divergência, sem alterar o banco

### Requirement: Saúde e configuração por ambiente
Cada serviço SHALL expor um endpoint de saúde usado pelos healthchecks. A saúde SHALL incluir as dependências críticas de cada serviço:
- banco, para booking e notification;
- broker, para booking e notification.

Toda configuração que muda entre ambientes MUST vir de variáveis de ambiente com padrão seguro para desenvolvimento local: URLs, credenciais, origens CORS, URL pública do front e URL pública das fotos.

#### Scenario: Broker indisponível
- **WHEN** o RabbitMQ para
- **THEN** a saúde do notification-service passa a indicar falha no broker
