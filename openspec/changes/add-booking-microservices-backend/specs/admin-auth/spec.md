## Purpose

Garante que só pessoas autorizadas usem o painel do salão, com contas persistentes, senhas em hash e sessão compatível com o front. Cada profissional fica restrita ao próprio trabalho, e o master gerencia tudo.

## ADDED Requirements

### Requirement: Contas persistentes com senha em hash
As contas do painel SHALL ficar no banco do booking-service. As senhas MUST ser guardadas só como hash com algoritmo adaptativo e sal, nunca em texto puro nem em log. A API MUST NOT devolver senha nem hash em nenhuma resposta.

#### Scenario: Senha não exposta
- **WHEN** o master chama `GET /api/admin/accounts`
- **THEN** a resposta lista profissionais e o tipo de login, sem senha nem hash

### Requirement: Contas iniciais vindas do ambiente
Na inicialização, o serviço SHALL criar ou atualizar a conta master a partir de `ADMIN_USERNAME` (padrão `master`) e `ADMIN_PASSWORD`. Também SHALL criar ou atualizar as contas das profissionais listadas em `ADMIN_USERS` (formato `id:senha;id:senha`). Essas contas SHALL aparecer com `login: "env"` e MUST NOT ser alteradas pelo painel. Sem `ADMIN_PASSWORD`, o master MUST ficar desabilitado. Entrada de `ADMIN_USERS` com profissional inexistente MUST ser ignorada com aviso no log. Sem nenhuma conta ativa, o login MUST responder 503 `LOGIN_DISABLED`.

#### Scenario: Master configurado
- **WHEN** o serviço sobe com `ADMIN_PASSWORD` definida
- **THEN** o usuário `master` entra no painel com essa senha

#### Scenario: Conta do ambiente protegida
- **WHEN** o master tenta trocar pelo painel a senha da `stefani`, definida em `ADMIN_USERS`
- **THEN** a resposta é 400 `VALIDATION_ERROR` e a senha continua a do ambiente

### Requirement: Logins criados pelo painel persistem
O master SHALL criar ou trocar o login de uma profissional cadastrada que não esteja em `ADMIN_USERS`, com senha de no mínimo 6 caracteres. Esse login MUST persistir entre reinícios e SHALL aparecer como `login: "db"`. Profissional sem login SHALL aparecer como `login: null`.

#### Scenario: Login sobrevive ao reinício
- **WHEN** o master cria o login `ana` com senha `ana123` e o serviço reinicia
- **THEN** a Ana continua entrando com esse usuário e senha

### Requirement: Login e sessão por cookie
`POST /api/admin/login` SHALL comparar o usuário sem diferenciar maiúsculas e sem espaços nas pontas. Usuário inexistente e senha errada MUST responder 401 com a mesma mensagem, "Usuário ou senha incorretos".

No login válido, a resposta SHALL definir o cookie `admin_session` com estes atributos:
- `HttpOnly`;
- `SameSite` configurável;
- `Secure` configurável;
- validade de 12 h.

O cookie carrega uma credencial assinada com perfil, id da profissional e versão da senha. `POST /api/admin/logout` SHALL apagar o cookie. Toda rota `/api/admin/*`, exceto login, sem sessão válida MUST responder 401 `UNAUTHORIZED`.

#### Scenario: Usuário inexistente
- **WHEN** alguém envia o usuário `maria`, que não existe
- **THEN** a resposta é 401 "Usuário ou senha incorretos" e nenhum cookie é criado

#### Scenario: Sessão expirada
- **WHEN** a sessão tem mais de 12 h
- **THEN** a próxima chamada ao painel responde 401

### Requirement: Troca de senha encerra sessões da conta
Quando a senha de uma conta muda, pelo painel ou pelo ambiente, as sessões abertas dessa conta MUST deixar de valer. As sessões das outras contas MUST continuar válidas.

#### Scenario: Troca pelo painel
- **WHEN** o master troca a senha da `ana` enquanto ela está logada
- **THEN** a próxima chamada da Ana responde 401, e a sessão da Stéfani continua válida

### Requirement: Escopo por perfil
Com perfil profissional, toda leitura e ação do painel MUST se limitar aos agendamentos, serviços e ganhos da própria profissional. Filtro `professionalId` de outra profissional MUST ser ignorado. Ação sobre agendamento ou serviço alheio MUST responder 404 `NOT_FOUND`.

Só o master SHALL poder:
- criar ou editar categorias;
- cadastrar ou editar profissionais;
- gerenciar logins;
- trocar a profissional de um serviço.

Com perfil profissional, essas operações MUST responder 403 `FORBIDDEN`.

Exceção: o WhatsApp da profissional SHALL ser alterado só pela própria profissional, por `PATCH /api/admin/me` (capacidade `booking-api`). Nem o master altera esse campo.

`GET /api/admin/me` SHALL retornar `{ role, professionalId, name, whatsappPhone }`. Para o master, `whatsappPhone` é `null`.

#### Scenario: Profissional altera só o próprio número
- **WHEN** a Michele envia `PATCH /api/admin/me` com um novo `whatsappPhone`
- **THEN** só o número da Michele muda. Não há parâmetro que permita alterar o de outra profissional

#### Scenario: Ação em agendamento alheio
- **WHEN** a Michele tenta confirmar um pedido da Lorrayne pelo id
- **THEN** a resposta é 404 e o pedido não muda

#### Scenario: Profissional cadastra profissional
- **WHEN** uma sessão de profissional chama `POST /api/admin/professionals`
- **THEN** a resposta é 403 `FORBIDDEN`

### Requirement: Limite de tentativas de login
O login SHALL limitar tentativas falhas por usuário e por origem em uma janela de tempo configurável. Acima do limite, MUST responder 429 com mensagem em pt-BR, sem revelar se o usuário existe.

#### Scenario: Muitas tentativas
- **WHEN** uma origem erra a senha do `master` acima do limite configurado dentro da janela
- **THEN** as próximas tentativas dessa origem respondem 429 até a janela expirar
