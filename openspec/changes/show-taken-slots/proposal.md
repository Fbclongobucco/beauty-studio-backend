## Why

Na tela de horários, o horário já ocupado por um agendamento confirmado some da lista. Para a cliente, a grade fica com buracos, e ela não entende se o salão abre naquele horário ou não. O pedido do salão é mostrar o horário marcado em cinza, sem permitir o toque, no mesmo lugar da grade.

## What Changes

- `GET /api/availability` ganha o campo `taken: string[]`: horários da grade do dia, do mesmo serviço, que não podem ser escolhidos porque se sobrepõem a um agendamento **confirmado** da profissional.
- `slots` continua igual: só os horários livres. O campo é aditivo, e quem não conhece `taken` continua funcionando.
- A mudança do front (`show-taken-slots` no `beauty-studio`) junta `slots` e `taken` em ordem e mostra os de `taken` em cinza, desabilitados.

## Capabilities

### New Capabilities
- `slot-display`: informação dos horários ocupados na disponibilidade do dia.

### Modified Capabilities
<!-- nenhuma no backend: o requisito de disponibilidade da mudança add-booking-microservices-backend ainda não foi arquivado; o comportamento novo fica nesta capacidade e não altera o cálculo de slots -->

## Impact

- **booking-service:**
  - `core/domain/Availability`: novo cálculo dos horários ocupados;
  - `AvailabilityUseCase`;
  - `Dtos.AvailabilityResponse`;
  - testes de domínio e o `PublicApiIT`.
- Sem migration e sem mudança no gateway.
