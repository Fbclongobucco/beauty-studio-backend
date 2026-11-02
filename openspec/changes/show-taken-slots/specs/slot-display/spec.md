## ADDED Requirements

### Requirement: Horários ocupados na disponibilidade do dia
`GET /api/availability?serviceId=&date=` SHALL responder também `taken: string[]` (HH:mm, em ordem crescente). Cada item é um início da grade de 30 minutos que:
- termina até 20:00 com a duração do serviço;
- não está no passado (no dia de hoje, depois do horário atual de São Paulo);
- se sobrepõe a pelo menos um agendamento `confirmed` da profissional do serviço.

`slots` MUST continuar com exatamente os horários livres de hoje.

Regras:
- Um horário MUST NOT aparecer nas duas listas.
- Pedido `pending` MUST NOT tornar um horário ocupado.
- Dia fechado ou fora da janela MUST responder `slots` e `taken` vazios.
- A resposta MUST NOT informar quem ocupa o horário nem o serviço marcado.

#### Scenario: Horário confirmado aparece como ocupado
- **WHEN** a Stéfani tem Manicure (30 min) confirmada na sexta às 11:00, e a cliente consulta Manicure nesse dia
- **THEN** `taken` traz `["11:00"]` e `slots` não traz 11:00

#### Scenario: Serviço mais longo
- **WHEN** a mesma Manicure das 11:00 está confirmada, e a cliente consulta "Manicure + Pedicure" (1 h) da Stéfani nesse dia
- **THEN** `taken` traz `["10:30", "11:00"]`

#### Scenario: Pendente não ocupa
- **WHEN** existe só um pedido `pending` às 11:00
- **THEN** 11:00 fica em `slots` e `taken` vem vazio

#### Scenario: Horário que já passou
- **WHEN** hoje às 14:10 existe um confirmado às 10:00
- **THEN** 10:00 não aparece em `taken` nem em `slots`
