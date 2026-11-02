# Subir em produção

Arquitetura de produção:

```
quem	onde	endereço (exemplo)
front (Next.js)	Vercel	https://salao.com.br
API (gateway, serviços, Postgres, RabbitMQ, MinIO)	VPS Linux com Docker	https://api.salao.com.br
HTTPS da API	Caddy, na mesma VPS (certificado automático)	portas 80 e 443
```

Os dois endereços precisam estar **no mesmo domínio** (`salao.com.br` e `api.salao.com.br`). A sessão do painel e o acompanhamento do pedido dependem disso. O endereço `*.vercel.app` não serve.

O envio de WhatsApp está **suspenso**: os avisos ficam só no log (veja "WhatsApp" no README).

## 1. Servidor (VPS)

- Linux com Docker Engine e o plugin Docker Compose (v2.24 ou mais novo, por causa do `!reset` no `compose.prod.yaml`).
- Memória: os limites do compose somam cerca de 3 GB. Esses valores são iniciais, ainda não medidos em servidor real; meça o consumo nos primeiros dias (`docker stats`).
- Firewall: abertas só as portas 22 (SSH), 80 e 443.

## 2. DNS

No painel do domínio:
- `api.salao.com.br`: registro A (e AAAA, se a VPS tiver IPv6) com o IP da VPS.
- `salao.com.br` (e `www`, se usar): o que a Vercel indicar ao adicionar o domínio no projeto.

O Caddy só consegue emitir o certificado depois que o registro da API estiver apontando para a VPS.

## 3. Backend na VPS

```bash
git clone git@github.com:Fbclongobucco/beauty-studio-backend.git
cd beauty-studio-backend
./stack.sh prod ps          # 1ª vez: cria .env.prod com as senhas geradas e para
```

Preencha em `.env.prod`:

```
variável	exemplo
API_DOMAIN	api.salao.com.br
APP_PUBLIC_FRONT_URL	https://salao.com.br
APP_PUBLIC_MEDIA_URL	https://api.salao.com.br
APP_CORS_ALLOWED_ORIGINS	https://salao.com.br (com www: https://salao.com.br,https://www.salao.com.br)
APP_COOKIE_DOMAIN	salao.com.br
ADMIN_USERS	stefani:senha1;lorrayne:senha2;michele:senha3
SALON_WHATSAPP	+5521968117976
EVOLUTION_SERVER_URL	http://localhost:8081
```

`ADMIN_PASSWORD` (senha do master) e as demais senhas já vêm geradas. Guarde uma cópia do `.env.prod` fora do servidor: sem ele, perdem-se as senhas do banco, do RabbitMQ e do MinIO, e trocar o `APP_JWT_SECRET` encerra todas as sessões do painel.

```bash
./stack.sh prod up -d --build
./stack.sh prod ps            # todos "healthy" em alguns minutos
curl https://api.salao.com.br/api/services
```

O booking-service se recusa a subir com configuração de desenvolvimento e diz no log o que falta, por exemplo URL sem `https` ou `APP_COOKIE_DOMAIN` ausente com front e API em hosts diferentes (`./stack.sh prod logs booking-service`).

## 4. Front na Vercel

1. Importe o repositório `beauty-studio-front` na Vercel (framework: Next.js; comandos padrão).
2. Em **Settings → Environment Variables** (Production), defina:
   - `NEXT_PUBLIC_API_BASE_URL` = `https://api.salao.com.br`
   - `ADMIN_PASSWORD` e `ADMIN_USERS` **não** são necessárias: o login é feito pelo backend.
3. Em **Settings → Domains**, adicione `salao.com.br` (e `www`, se usar) e siga as instruções de DNS.
4. Faça um novo deploy depois de mudar a variável: `NEXT_PUBLIC_*` é fixada no build.

Com `NEXT_PUBLIC_API_BASE_URL` definido, a API de teste em memória do front (`/api/*` no domínio da Vercel) responde 404.

## 5. Conferência depois de subir

- `https://salao.com.br`: catálogo com os 21 serviços.
- Fazer um pedido; voltar à página inicial e ser levada ao pedido.
- `https://salao.com.br/admin`: entrar como master e como uma profissional; confirmar o pedido.
- No navegador, o cookie `admin_session` deve aparecer com `Domain=salao.com.br`, `Secure` e `HttpOnly`.
- `http://api.salao.com.br` redireciona para `https://`.

## 6. Operação

- **Atualizar:** `git pull && ./stack.sh prod up -d --build`. As migrations do banco rodam sozinhas na subida.
- **Logs:** `./stack.sh prod logs -f booking-service` (rotação automática: 10 MB × 5 arquivos por contêiner).
- **Backup do banco** (agendar, por exemplo, no cron diário):
  ```bash
  ./stack.sh prod exec -T postgres pg_dumpall -U postgres | gzip > backup-$(date +%F).sql.gz
  ```
  As fotos ficam no volume do MinIO (`beauty-prod_minio-data`); copie o volume ou use `mc mirror`.
- **Painéis de gestão** (RabbitMQ, MinIO, Eureka) não são publicados em produção. Para acesso pontual, use túnel SSH até a porta interna do contêiner.
