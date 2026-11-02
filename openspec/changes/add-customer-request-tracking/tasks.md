## 1. Cookie de acompanhamento

- [x] 1.1 `infra/security/MyRequestsCookie`:
  - emitir e ler o JWT `aud: "my-requests"`, com até 10 ids e 60 dias;
  - tratar inválido como vazio;
  - `HttpOnly`, `Path=/api/appointments`, `Secure` e `SameSite` do perfil.
- [x] 1.2 Exigir a audiência da sessão no validador do `admin_session`, para um token não servir no lugar do outro
- [x] 1.3 Testes unitários:
  - inclusão no início, sem duplicar;
  - limite de 10;
  - assinatura inválida;
  - expirado;
  - audiência trocada.

## 2. API

- [x] 2.1 Caso de uso `TrackedRequestsUseCase`: buscar por ids, filtrar "em andamento" com `Clock` e fuso de São Paulo, ordenar pelo início e devolver também os ids que ainda existem
- [x] 2.2 `PublicController`:
  - `Set-Cookie` em `POST /api/appointments` (201) e `GET /api/appointments/{id}` (200);
  - novo `GET /api/appointments/mine`;
  - ids inexistentes saem do cookie.
- [x] 2.3 Conferir que `/mine` não colide com `/{id}`: rota literal antes da variável, e `mine` não é UUID
- [x] 2.4 `SecurityConfig`: as rotas novas são públicas

## 3. Testes de integração

- [x] 3.1 `TrackingIT`:
  - criar grava o cookie;
  - GET do pedido grava o cookie;
  - `/mine` filtra pendente, confirmado futuro, recusado futuro e passado;
  - cookie adulterado conta como vazio;
  - sem cookie, a lista vem vazia.
- [x] 3.2 Pelo gateway, com o perfil local de pé: o cookie criado em uma requisição volta na seguinte, e uma requisição sem cookie recebe a lista vazia (sem vazamento entre clientes)

## 4. Documentação

- [x] 4.1 README: seção "Acompanhamento do pedido" (cookie, endpoint, validade e exigência de mesmo site)
