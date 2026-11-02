## Context

`Availability.availableSlots` filtra a grade (`gridStarts`) pelo passado e depois pelas sobreposições com confirmados. O resultado da sobreposição já existe no meio do cálculo; hoje ele é descartado.

## Goals / Non-Goals

**Goals:** expor o que já é calculado, sem mudar `slots` nem `days`.

**Non-Goals:**
- Mostrar pendentes como "em análise".
- Dizer quem ocupa o horário.
- Mudar `GET /api/availability/days`.

## Decisions

### D1. Uma função de domínio para as duas listas
`Availability.daySlots(...)` devolve `{ free, taken }` a partir da mesma grade filtrada pelo passado. `availableSlots` passa a ser `daySlots(...).free`, então `slots` não muda por construção.

### D2. Nome `taken`
É curto e não colide com `Busy`, que é o intervalo confirmado no domínio. O nome é o mesmo no front e na API de teste.

## Risks / Trade-offs

- **Expor que o horário está ocupado:** é o que a grade com buracos já revelava, sem identificar a cliente. Aceito.
