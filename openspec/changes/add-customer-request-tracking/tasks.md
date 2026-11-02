## 1. Cookie de acompanhamento

- [ ] 1.1 `infra/security/MyRequestsCookie`:
  - emitir e ler o JWT `aud: "my-requests"`, com até 10 ids e 60 dias;
  - tratar inválido como vazio;
  - `HttpOnly`, `Path=/api/appointments`, `Secure` e `SameSite` do perfil.
- [ ] 1.2 Exigir a audiência da sessão no validador do `admin_session`, para um token não servir no lugar do outro
- [ ] 1.3 Testes unitários:
  - inclusão no início, sem duplicar;
  - limite de 10;
  - assinatura inválida;
  - expirado;
  - audiência trocada.

## 2. API

- [ ] 2.1 Caso de uso `TrackedRequestsUseCase`: buscar por ids, filtrar "em andamento" com `Clock` e fuso de São Paulo, ordenar pelo início e devolver também os ids que ainda existem
- [ ] 2.2 `PublicController`:
  - `Set-Cookie` em `POST /api/appointments` (201) e `GET /api/appointments/{id}` (200);
  - novo `GET /api/appointments/mine`;
  - novo `DELETE /api/appointments/mine/{id}`;
  - ids inexistentes saem do cookie.
- [ ] 2.3 Conferir que `/mine` não colide com `/{id}`: rota literal antes da variável, e `mine` não é UUID
- [ ] 2.4 `SecurityConfig`: as rotas novas são públicas

## 3. Testes de integração

- [ ] 3.1 `TrackingIT`:
  - criar grava o cookie;
  - GET do pedido grava o cookie;
  - `/mine` filtra pendente, confirmado futuro, recusado futuro e passado;
  - cookie adulterado conta como vazio;
  - DELETE tira o id e não muda o status;
  - sem cookie, a lista vem vazia.
- [ ] 3.2 Pelo gateway, com o perfil local de pé: o cookie criado em uma requisição volta na seguinte, e uma requisição sem cookie recebe a lista vazia (sem vazamento entre clientes)

## 4. Documentação

- [ ] 4.1 README: seção "Acompanhamento do pedido" (cookie, endpoints, validade e exigência de mesmo site)
