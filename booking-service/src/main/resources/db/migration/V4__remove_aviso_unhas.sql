-- O salão pediu para tirar o aviso de Francesinha/Encapsular da categoria Unhas.
-- Só limpa se o texto ainda for o original da V2: aviso editado no painel é preservado.
UPDATE category
SET note = NULL
WHERE id = 'unhas'
  AND note = 'Francesinha: valor à parte. Encapsular: + R$ 15,00.';
