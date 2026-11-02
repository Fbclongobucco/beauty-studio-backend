-- Catalogo inicial, copiado de beauty-studio/lib/server/seed.ts. Aplicado uma unica vez pelo Flyway:
-- edicoes feitas depois no painel nao sao sobrescritas.
-- Fontes dos precos: docs/referencias/tabela-precos-unhas.jpg e tabela-precos-trancas.jpg do front;
-- depilacao (R$ 80,00, 1 h) informada pelas donas.
-- Duracoes informadas: Manicure, Pedicure, Manicure + Pedicure e Depilacao. As demais sao ESTIMATIVA autorizada
-- para teste (duration_estimated = TRUE), a revisar pelas profissionais no painel.

INSERT INTO professional (id, name, active, created_at) VALUES
    ('stefani', 'Stéfani Longo Bucco', TRUE, '2026-11-01T00:00:00Z'),
    ('lorrayne', 'Lorrayne Longobuco', TRUE, '2026-11-01T00:00:01Z'),
    ('michele', 'Michele Batista', TRUE, '2026-11-01T00:00:02Z');

INSERT INTO category (id, name, note, sort_order) VALUES
    ('unhas', 'Unhas', 'Francesinha: valor à parte. Encapsular: + R$ 15,00.', 1),
    ('trancas', 'Tranças', 'Valores somente de mão de obra.', 2),
    ('depilacao', 'Depilação', NULL, 3);

INSERT INTO service (id, name, category_id, subcategory, duration_min, duration_estimated, price_cents,
                     professional_id, active, sort_order) VALUES
    ('manicure', 'Manicure', 'unhas', 'Cuidados básicos', 30, FALSE, 2000, 'stefani', TRUE, 1),
    ('pedicure', 'Pedicure', 'unhas', 'Cuidados básicos', 30, FALSE, 2500, 'stefani', TRUE, 2),
    ('manicure-pedicure', 'Manicure + Pedicure', 'unhas', 'Cuidados básicos', 60, FALSE, 4000, 'stefani', TRUE, 3),
    ('esmaltacao-gel', 'Esmaltação em gel', 'unhas', 'Esmaltação e tratamento', 60, TRUE, 5000, 'stefani', TRUE, 4),
    ('blindagem', 'Blindagem', 'unhas', 'Esmaltação e tratamento', 60, TRUE, 4000, 'stefani', TRUE, 5),
    ('banho-gel', 'Banho de gel', 'unhas', 'Esmaltação e tratamento', 90, TRUE, 8500, 'stefani', TRUE, 6),
    ('postica-realista', 'Postiça realista', 'unhas', 'Extensão e decoração', 60, TRUE, 7000, 'stefani', TRUE, 7),
    ('molde-f1', 'Colocação molde F1', 'unhas', 'Extensão e decoração', 120, TRUE, 9000, 'stefani', TRUE, 8),
    ('gel-tip', 'Gel na tip', 'unhas', 'Extensão e decoração', 120, TRUE, 11500, 'stefani', TRUE, 9),
    ('fibra-vidro', 'Fibra de vidro', 'unhas', 'Extensão e decoração', 150, TRUE, 13500, 'stefani', TRUE, 10),
    ('manutencao-gel', 'Manutenção gel', 'unhas', 'Manutenções', 90, TRUE, 8500, 'stefani', TRUE, 11),
    ('manutencao-fibra', 'Manutenção fibra de vidro', 'unhas', 'Manutenções', 120, TRUE, 11000, 'stefani', TRUE, 12),
    ('nago-1-3', '1 a 3 tranças', 'trancas', 'Tranças nagô', 60, TRUE, 3500, 'lorrayne', TRUE, 13),
    ('nago-4-6', '4 a 6 tranças', 'trancas', 'Tranças nagô', 90, TRUE, 4500, 'lorrayne', TRUE, 14),
    ('nago-7-12', '7 a 12 tranças', 'trancas', 'Tranças nagô', 120, TRUE, 6000, 'lorrayne', TRUE, 15),
    ('box-braids', 'Box braids', 'trancas', 'Estilos', 360, TRUE, 19000, 'lorrayne', TRUE, 16),
    ('boxeadora', 'Boxeadora', 'trancas', 'Estilos', 180, TRUE, 14000, 'lorrayne', TRUE, 17),
    ('goddess-braids', 'Goddess braids', 'trancas', 'Estilos', 420, TRUE, 20000, 'lorrayne', TRUE, 18),
    ('fulani-braids', 'Fulani braids', 'trancas', 'Estilos', 300, TRUE, 16000, 'lorrayne', TRUE, 19),
    ('ghana-braids', 'Ghana braids', 'trancas', 'Estilos', 240, TRUE, 16000, 'lorrayne', TRUE, 20),
    ('depilacao', 'Depilação', 'depilacao', NULL, 60, FALSE, 8000, 'michele', TRUE, 21);
