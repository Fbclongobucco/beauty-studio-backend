#!/usr/bin/env bash
# Testes de WhatsApp no perfil local. Uso: ./whatsapp.sh <comando>
#   status     modo do notification-service e estado do número na Evolution API
#   conectar   cria a instância (se preciso), salva o QR Code em whatsapp-qrcode.png e espera a leitura
#   reiniciar  apaga a instância na Evolution para recomeçar o pareamento do zero
#   real       envio real pela Evolution API (exige número conectado)
#   sandbox    envio para o sandbox: nada sai de verdade; mensagens em http://localhost:8099
#   log        só registra as mensagens no log do notification-service (padrão do perfil local)
# Depois de "real" ou "sandbox", use "Enviar mensagem de teste" em Serviços > Meu WhatsApp, no painel da profissional.
set -euo pipefail
cd "$(dirname "$0")"

ENV_FILE=.env.local
[[ -f "$ENV_FILE" ]] || { echo "Rode antes: ./stack.sh local up -d --build" >&2; exit 1; }
get() { { grep -E "^$1=" "$ENV_FILE" || true; } | tail -1 | cut -d= -f2- | sed 's/[[:space:]]*#.*$//'; }
set_env() {
  if grep -qE "^$1=" "$ENV_FILE"; then sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"; else echo "$1=$2" >> "$ENV_FILE"; fi
}
KEY="$(get EVOLUTION_API_KEY)"
INSTANCE="$(get EVOLUTION_INSTANCE)"; INSTANCE="${INSTANCE:-beauty-studio}"
EVO=http://localhost:8081
evo() { curl -s -H "apikey: $KEY" -H 'Content-Type: application/json' "$@"; }
state() { evo "$EVO/instance/connectionState/$INSTANCE" | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("instance",{}).get("state","inexistente"))
except Exception: print("indisponível")'; }
recreate() { ./stack.sh local up -d notification-service >/dev/null 2>&1; echo "notification-service reiniciado."; }

case "${1:-}" in
  status)
    send="$(get NOTIFICATION_SEND_ENABLED)"; url="$(get EVOLUTION_API_URL)"
    if [[ "$send" != "true" ]]; then echo "Modo: log (nada é enviado)";
    elif [[ "$url" == *whatsapp-sandbox* ]]; then echo "Modo: sandbox (mensagens em http://localhost:8099)";
    else echo "Modo: real (Evolution API)"; fi
    echo "Número na Evolution (instância $INSTANCE): $(state)   (open = conectado)"
    ;;
  conectar)
    if [[ "$(state)" == "inexistente" ]]; then
      evo -X POST "$EVO/instance/create" -d "{\"instanceName\":\"$INSTANCE\",\"integration\":\"WHATSAPP-BAILEYS\",\"qrcode\":true}" >/dev/null
      echo "Instância $INSTANCE criada."
    fi
    if [[ "$(state)" == "open" ]]; then echo "O número já está conectado."; exit 0; fi
    # O QR muda durante o pareamento (o WhatsApp pede a troca do segredo): a página se recarrega a cada 2 s
    save_qr() { evo "$EVO/instance/connect/$INSTANCE" | python3 -c 'import sys,json,base64
d=json.load(sys.stdin); b=d.get("base64","")
if b: open("whatsapp-qrcode.png","wb").write(base64.b64decode(b.split(",",1)[-1]))'; }
    save_qr
    [[ -f whatsapp-qrcode.png ]] || { echo "A Evolution não devolveu QR Code." >&2; exit 1; }
    cat > whatsapp-qrcode.html <<'HTML'
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta http-equiv="refresh" content="2"><title>Conectar WhatsApp</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f1f1f1;font:16px system-ui,sans-serif;color:#252525;text-align:center}
img{width:320px;height:320px;image-rendering:pixelated;background:#fff;padding:16px;border-radius:12px}p{max-width:340px}</style></head>
<body><div><img src="whatsapp-qrcode.png" alt="QR Code para conectar o WhatsApp"><p>WhatsApp &gt; Aparelhos conectados &gt; Conectar aparelho. A imagem se atualiza sozinha.</p></div></body></html>
HTML
    echo "QR Code em $(pwd)/whatsapp-qrcode.html (atualiza sozinho)"
    echo "No celular: WhatsApp > Aparelhos conectados > Conectar aparelho, e leia o QR Code."
    command -v xdg-open >/dev/null && xdg-open whatsapp-qrcode.html >/dev/null 2>&1 || true
    for _ in $(seq 1 90); do
      if [[ "$(state)" == "open" ]]; then
        rm -f whatsapp-qrcode.png whatsapp-qrcode.html
        echo "Conectado. Agora rode: ./whatsapp.sh real"; exit 0
      fi
      save_qr; sleep 2
    done
    echo "Ainda não conectou. Rode ./whatsapp.sh conectar de novo; se o QR tiver esgotado, use ./whatsapp.sh reiniciar antes." >&2
    exit 1
    ;;
  reiniciar)
    # Apaga a instância (sessão do pareamento) e cria outra: útil quando o pareamento travou
    evo -X DELETE "$EVO/instance/logout/$INSTANCE" >/dev/null || true
    evo -X DELETE "$EVO/instance/delete/$INSTANCE" >/dev/null || true
    echo "Instância $INSTANCE apagada. Rode ./whatsapp.sh conectar."
    ;;
  real)
    [[ "$(state)" == "open" ]] || echo "Atenção: o número ainda não está conectado (rode ./whatsapp.sh conectar). Os envios vão falhar e ser retentados." >&2
    set_env NOTIFICATION_SEND_ENABLED true
    set_env EVOLUTION_API_URL http://evolution-api:8080
    recreate
    echo "Modo real: as mensagens saem pelo número conectado na Evolution."
    ;;
  sandbox)
    ./stack.sh local --profile whatsapp-sandbox up -d whatsapp-sandbox >/dev/null 2>&1
    set_env NOTIFICATION_SEND_ENABLED true
    set_env EVOLUTION_API_URL http://whatsapp-sandbox:8080
    recreate
    echo "Modo sandbox: abra http://localhost:8099 para ver as mensagens."
    echo "Simular falhas: curl -X POST localhost:8099/mode -d '{\"mode\":\"invalid-number\"}'  (ou \"down\", \"ok\")"
    ;;
  log)
    set_env NOTIFICATION_SEND_ENABLED false
    set_env EVOLUTION_API_URL http://evolution-api:8080
    recreate
    ./stack.sh local --profile whatsapp-sandbox stop whatsapp-sandbox >/dev/null 2>&1 || true
    echo "Modo log: as mensagens só aparecem em ./stack.sh local logs -f notification-service"
    ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
