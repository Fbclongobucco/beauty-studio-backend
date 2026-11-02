-- Uma foto opcional por servico (contrato add-service-photo do front). O objeto fica no MinIO;
-- aqui so a chave, que muda a cada troca (a URL publica muda junto e caches nao mostram a foto antiga).
ALTER TABLE service ADD COLUMN photo_key TEXT;
ALTER TABLE service ADD COLUMN photo_content_type TEXT;
