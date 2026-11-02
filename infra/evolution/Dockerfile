# FORA DE USO (envio de WhatsApp suspenso; o pareamento não concluiu no teste de 02/11/2026).
# Evolution API v2.3.7 com a correção do pareamento de aparelho novo (companion_reg_refresh).
# Motivo e origem: patch-baileys-pairing.mjs. Remover quando a Evolution publicar a correção oficial.
FROM evoapicloud/evolution-api:v2.3.7
COPY patch-baileys-pairing.mjs /tmp/patch-baileys-pairing.mjs
RUN node /tmp/patch-baileys-pairing.mjs && rm /tmp/patch-baileys-pairing.mjs
