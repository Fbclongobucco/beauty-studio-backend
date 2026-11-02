# Subir em produção

```
quem	onde	endereço (exemplo)
front (Next.js)	Vercel	https://salao.com.br
API (booking-service, Postgres, MinIO)	VPS Linux com Docker	https://api.salao.com.br
HTTPS da API	Caddy, na mesma VPS (certificado automático)	portas 80 e 443
```

Os dois endereços precisam estar **no mesmo domínio** (`salao.com.br` e `api.salao.com.br`). A sessão do painel e o acompanhamento do pedido dependem disso. O endereço `*.vercel.app` não serve.

## Qual perfil usar

```
perfil	servidor	o que roda	comando
lite (enxuto)	1 GB de RAM	booking-service sozinho, Postgres, MinIO, Caddy	./stack.sh lite ...
prod (completo)	4 GB ou mais	tudo: gateway, Eureka, RabbitMQ, notification-service, Evolution, Caddy	./stack.sh prod ...
```

- **Consumo do lite:** medido nesta máquina de desenvolvimento, depois de 600 requisições, ficou em cerca de 475 MiB (booking 364, Caddy 37, Postgres 43, MinIO 31).
- **Consumo do completo:** em repouso já passa de 1 GiB só com os contêineres.
- **Margem:** as duas medições foram feitas fora do servidor real. Meça de novo lá nos primeiros dias, com `docker stats`.
- **WhatsApp:** o envio está **suspenso** nos dois perfis. No lite, os eventos de notificação ficam gravados no banco (tabela `outbox_event`) e saem quando o perfil completo voltar.

Os passos abaixo são do **lite**, o perfil do servidor atual de 1 GB.

## 1. Servidor (VPS de 1 GB)

- Linux com Docker Engine e o plugin Docker Compose.
- Firewall: abertas só as portas 22 (SSH), 80 e 443.
- Swap de 1 GB como reserva, para picos não derrubarem o booking:
  ```bash
  sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```
- Confira a arquitetura com `uname -m`. A imagem é gerada na sua máquina (passo 4); se o servidor for `aarch64` e a sua máquina `x86_64`, gere com `docker buildx build --platform linux/arm64`.

## 2. DNS

No painel do domínio:
- `api.salao.com.br`: registro A (e AAAA, se a VPS tiver IPv6) com o IP da VPS.
- `salao.com.br` (e `www`, se usar): o que a Vercel indicar ao adicionar o domínio no projeto.

O Caddy só consegue emitir o certificado depois que o registro da API estiver apontando para a VPS.

## 3. Configuração na VPS

```bash
git clone git@github.com:Fbclongobucco/beauty-studio-backend.git
cd beauty-studio-backend
./stack.sh lite ps          # 1ª vez: cria .env.lite com as senhas geradas e para
```

Preencha em `.env.lite`:

```
variável	exemplo
API_DOMAIN	api.salao.com.br
APP_COOKIE_DOMAIN	salao.com.br
APP_CORS_ALLOWED_ORIGINS	https://salao.com.br (com www: https://salao.com.br,https://www.salao.com.br)
APP_PUBLIC_FRONT_URL	https://salao.com.br
APP_PUBLIC_MEDIA_URL	https://api.salao.com.br
ADMIN_USERS	stefani:senha1;lorrayne:senha2;michele:senha3
```

`ADMIN_PASSWORD` (senha do master) e as demais senhas já vêm geradas. Guarde uma cópia do `.env.lite` fora do servidor: sem ele, perdem-se as senhas do banco e do MinIO, e trocar o `APP_JWT_SECRET` encerra todas as sessões do painel.

## 4. Imagem do booking-service

Compilar o booking (Maven + JDK) dentro de um servidor de 1 GB tende a esgotar a memória. Gere a imagem na sua máquina e envie pronta:

```bash
# na sua máquina, dentro do beauty-studio-backend
./stack.sh lite build booking-service
docker save beauty-studio/booking-service:lite | gzip | ssh usuario@IP-DA-VPS 'gunzip | docker load'
```

## 5. Subir

```bash
# na VPS
./stack.sh lite up -d       # sem --build: usa a imagem enviada
./stack.sh lite ps          # postgres, minio e booking "healthy"
curl https://api.salao.com.br/api/services
```

O booking-service se recusa a subir com configuração de desenvolvimento e diz no log o que falta. Exemplos: URL sem `https`, ou `APP_COOKIE_DOMAIN` ausente com front e API em hosts diferentes (`./stack.sh lite logs booking-service`).

## 6. Front na Vercel

1. Importe o repositório `beauty-studio-front` na Vercel (framework: Next.js; comandos padrão).
2. Em **Settings → Environment Variables** (Production), defina `NEXT_PUBLIC_API_BASE_URL` = `https://api.salao.com.br`. As variáveis `ADMIN_PASSWORD` e `ADMIN_USERS` não são necessárias, porque o login é feito pelo backend.
3. Em **Settings → Domains**, adicione `salao.com.br` (e `www`, se usar) e siga as instruções de DNS.
4. Faça um novo deploy depois de mudar a variável: `NEXT_PUBLIC_*` é fixada no build.

Com `NEXT_PUBLIC_API_BASE_URL` definido, a API de teste em memória do front (`/api/*` no domínio da Vercel) responde 404.

## 7. Conferência depois de subir

- `https://salao.com.br`: catálogo com os 21 serviços.
- Fazer um pedido; abrir a página inicial numa aba nova e ser levada ao pedido.
- `https://salao.com.br/admin`: entrar como master e como uma profissional; confirmar o pedido; enviar a foto de um serviço e ver a foto no site.
- No navegador, o cookie `admin_session` deve aparecer com `Domain=salao.com.br`, `Secure` e `HttpOnly`.
- `http://api.salao.com.br` redireciona para `https://`.
- `docker stats --no-stream` para conferir a memória.

## 8. Operação

- **Atualizar:** na VPS, `git pull`. Na sua máquina, gere e envie a imagem de novo (passo 4). Depois, na VPS, `./stack.sh lite up -d`. As migrations do banco rodam sozinhas na subida.
- **Logs:** `./stack.sh lite logs -f booking-service` (rotação automática: 10 MB × 5 arquivos por contêiner).
- **Backup do banco:** agende, por exemplo, no cron diário:
  ```bash
  ./stack.sh lite exec -T postgres pg_dumpall -U postgres | gzip > backup-$(date +%F).sql.gz
  ```
  As fotos ficam no volume do MinIO (`beauty-lite_minio-data`).
- **Console do MinIO** e acesso direto ao Postgres não são publicados. Para acesso pontual, use túnel SSH até a porta interna do contêiner.

## Perfil completo (servidor de 4 GB ou mais)

Mesmos passos, com `prod` no lugar de `lite` e `.env.prod` no lugar de `.env.lite` (o `.env.prod.example` lista as variáveis). Nesse perfil, o Caddy fica na frente do gateway (`infra/caddy/Caddyfile`), que por sua vez distribui para os serviços. Com mais memória, a imagem pode ser gerada no próprio servidor: `./stack.sh prod up -d --build`.

Para passar do lite para o completo sem perder dados, faça o backup do banco e das fotos do lite e restaure no completo. Os volumes dos dois perfis são separados.
