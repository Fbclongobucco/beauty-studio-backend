## Purpose

Guarda a foto de cada serviço do catálogo em armazenamento de objetos e a mostra às clientes. Segue o contrato de foto definido pelo front-end (mudança `add-service-photo` em `beauty-studio`): uma foto opcional por serviço, no campo `photoUrl`.

## ADDED Requirements

### Requirement: Envio e troca da foto pelo painel
`PUT /api/admin/services/{id}/photo` SHALL receber `multipart/form-data` com o arquivo no campo `photo`. A foto enviada substitui a anterior, se houver. A resposta SHALL ser 200 com o serviço atualizado, no mesmo formato de `GET /api/admin/services`, com `photoUrl` preenchido.

Validação do arquivo:
- tipo JPEG, PNG ou WebP, conferido pela assinatura do conteúdo, não pelo nome nem pelo cabeçalho enviado;
- tamanho máximo de 5 MB.

Falhas MUST responder 400 `VALIDATION_ERROR` com `fields.photo`:
- tipo inválido: "Envie uma foto em JPEG, PNG ou WebP.";
- tamanho acima do limite: "A foto deve ter no máximo 5 MB.";
- campo ausente: "Escolha uma foto.".

O escopo é o mesmo da edição de serviço. O master envia para qualquer serviço. A profissional envia só para os próprios serviços, e serviço alheio responde 404 `NOT_FOUND`. Sem sessão, a resposta é 401.

#### Scenario: Envio válido
- **WHEN** a sessão da Lorrayne envia `PUT /api/admin/services/{id de Box braids}/photo` com um JPEG de 800 KB no campo `photo`
- **THEN** a resposta é 200 com o serviço e `photoUrl` preenchido, e `GET /api/services` traz a mesma `photoUrl` em "Box braids"

#### Scenario: Troca muda a URL
- **WHEN** a foto de "Manicure" é substituída por outra
- **THEN** o novo `photoUrl` é diferente do anterior, e a URL anterior deixa de servir a imagem

#### Scenario: Tipo inválido com nome de imagem
- **WHEN** o `PUT` envia um PDF renomeado para `foto.jpg`
- **THEN** a resposta é 400 `VALIDATION_ERROR` com `fields.photo` "Envie uma foto em JPEG, PNG ou WebP.", e nada é guardado

#### Scenario: Arquivo grande demais
- **WHEN** o `PUT` envia uma imagem de 6 MB
- **THEN** a resposta é 400 `VALIDATION_ERROR` com `fields.photo` "A foto deve ter no máximo 5 MB."

#### Scenario: Serviço de outra profissional
- **WHEN** a sessão da Michele envia `PUT` para a foto de "Manicure"
- **THEN** a resposta é 404 `NOT_FOUND` e a foto de "Manicure" não muda

### Requirement: Remoção da foto
`DELETE /api/admin/services/{id}/photo` SHALL remover a foto do serviço e o objeto do armazenamento. A resposta SHALL ser 200 com o serviço e `photoUrl: null`. Serviço sem foto também responde 200. O escopo é o mesmo do envio.

#### Scenario: Remoção
- **WHEN** o master envia `DELETE` para a foto de "Manicure"
- **THEN** a resposta é 200 com `photoUrl: null`, e a URL antiga deixa de servir a imagem

### Requirement: Foto no catálogo
Cada serviço de `GET /api/services`, `GET /api/admin/services`, `POST /api/admin/services` e `PATCH /api/admin/services/{id}` SHALL trazer `photoUrl`. O valor é uma URL absoluta, ou `null` quando o serviço não tem foto.

- `PATCH /api/admin/services/{id}` MUST ignorar `photoUrl` no corpo. A foto só muda pelos endpoints de foto.
- As URLs SHALL ser públicas, servidas pelo gateway em `/media/**`, sem login.
- As URLs MUST NOT expor credenciais nem o endereço interno do armazenamento.
- O nome do objeto MUST ser gerado pelo sistema, sem usar o nome original do arquivo.

#### Scenario: Serviço sem foto
- **WHEN** a cliente consulta o catálogo e "Pedicure" não tem foto
- **THEN** "Pedicure" vem com `photoUrl: null`

#### Scenario: Foto acessível pela cliente
- **WHEN** a cliente abre, sem login, a `photoUrl` retornada no catálogo
- **THEN** a resposta é 200 com a imagem e o `Content-Type` do formato

#### Scenario: PATCH não altera a foto
- **WHEN** o painel envia `PATCH /api/admin/services/{id}` com `photoUrl` no corpo
- **THEN** a foto do serviço não muda

### Requirement: Armazenamento indisponível
As chamadas ao armazenamento de objetos SHALL ter tempo limite e circuit breaker. Com o armazenamento fora do ar, o envio e a remoção MUST responder 503 `SERVICE_UNAVAILABLE`, sem alterar a foto gravada no serviço. O catálogo MUST continuar respondendo, com as URLs já gravadas.

#### Scenario: MinIO fora do ar
- **WHEN** o MinIO está parado e a profissional envia uma foto
- **THEN** a resposta é 503 `SERVICE_UNAVAILABLE`, o serviço mantém a foto anterior, e `GET /api/services` continua respondendo normalmente
