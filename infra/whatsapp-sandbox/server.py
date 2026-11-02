"""
WhatsApp sandbox: imita o endpoint de envio da Evolution API v2 para testar notificações sem celular.

- POST /message/sendText/{instancia}  cabeçalho apikey, corpo {"number": "5521...", "text": "..."}
  Responde como a Evolution ({"key": {"id": ...}}) e guarda a mensagem em memória.
- GET  /             caixa de entrada (página que se atualiza sozinha)
- GET  /messages     mensagens em JSON (mais recente primeiro)
- DELETE /messages   limpa a caixa
- POST /mode         {"mode": "ok" | "invalid-number" | "down"} simula falhas:
    invalid-number -> 400 (o notification-service trata como falha permanente: FAILED, sem retentativa)
    down           -> 503 (falha temporária: retentativas em 10 s, 1 min e 5 min)
Só biblioteca padrão. Dados somem ao reiniciar o contêiner.
"""
import html
import json
import os
import re
import threading
import uuid
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

API_KEY = os.environ.get("SANDBOX_API_KEY", "")
INSTANCE = os.environ.get("SANDBOX_INSTANCE", "beauty-studio")
MODES = ("ok", "invalid-number", "down")

lock = threading.Lock()
messages = []  # mais recente primeiro
state = {"mode": "ok"}


def fmt_number(digits):
    d = digits[2:] if digits.startswith("55") and len(digits) in (12, 13) else digits
    if len(d) == 11:
        return f"({d[:2]}) {d[2:7]}-{d[7:]}"
    if len(d) == 10:
        return f"({d[:2]}) {d[2:6]}-{d[6:]}"
    return digits


PAGE = """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="3"><title>WhatsApp sandbox</title>
<style>
:root {{ --bg: #ece5dd; --card: #fff; --out: #dcf8c6; --ink: #111b21; --muted: #667781; --warn: #b45309; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #0b141a; --card: #1f2c33; --out: #005c4b; --ink: #e9edef; --muted: #8696a0; --warn: #fbbf24; }} }}
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: var(--bg); color: var(--ink); font: 16px/1.45 system-ui, sans-serif; }}
header {{ position: sticky; top: 0; background: #075e54; color: #fff; padding: 14px 16px; }}
header h1 {{ margin: 0; font-size: 18px; }} header p {{ margin: 4px 0 0; font-size: 14px; opacity: .85; }}
main {{ max-width: 760px; margin: 0 auto; padding: 16px; }}
.mode {{ color: var(--warn); font-weight: 700; }}
.msg {{ background: var(--out); border-radius: 12px; padding: 10px 14px; margin: 0 0 14px auto; max-width: 92%;
        white-space: pre-wrap; overflow-wrap: anywhere; box-shadow: 0 1px 1px rgba(0,0,0,.15); }}
.to {{ font-weight: 700; margin-bottom: 6px; }} .meta {{ color: var(--muted); font-size: 13px; text-align: right; margin-top: 6px; }}
.empty {{ background: var(--card); border-radius: 12px; padding: 16px; text-align: center; color: var(--muted); }}
</style></head><body>
<header><h1>WhatsApp sandbox · {count} mensagem(ns)</h1>
<p>Nada é enviado de verdade. Modo: <span class="mode">{mode}</span> · atualiza a cada 3 s</p></header>
<main>{body}</main></body></html>"""


class Handler(BaseHTTPRequestHandler):
    server_version = "WhatsAppSandbox/1"

    def log_message(self, fmt, *args):
        print("[sandbox]", fmt % args, flush=True)

    def _json(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _raw_body(self):
        # O cliente HTTP do notification-service manda o corpo em chunks (sem Content-Length)
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            data = b""
            while True:
                size = int(self.rfile.readline().strip().split(b";")[0] or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    return data
                data += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("Content-Length") or 0))

    def _body(self):
        try:
            return json.loads(self._raw_body() or b"{}")
        except ValueError:
            return None

    def do_GET(self):
        if self.path == "/health":
            return self._json(200, {"status": "UP"})
        if self.path == "/messages":
            with lock:
                return self._json(200, {"mode": state["mode"], "messages": list(messages)})
        if self.path in ("/", "/index.html"):
            with lock:
                items = list(messages)
                mode = state["mode"]
            if items:
                body = "".join(
                    f'<div class="msg"><div class="to">Para {html.escape(fmt_number(m["number"]))}</div>'
                    f'{html.escape(m["text"])}<div class="meta">{html.escape(m["at"])} · {html.escape(m["id"])}</div></div>'
                    for m in items
                )
            else:
                body = '<p class="empty">Nenhuma mensagem ainda. Faça um pedido ou confirme um agendamento.</p>'
            data = PAGE.format(count=len(items), mode=html.escape(mode), body=body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            return self.wfile.write(data)
        return self._json(404, {"error": "not found"})

    def do_DELETE(self):
        if self.path == "/messages":
            with lock:
                messages.clear()
            return self._json(200, {"cleared": True})
        return self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path == "/mode":
            body = self._body() or {}
            if body.get("mode") not in MODES:
                return self._json(400, {"error": f"mode deve ser um de {MODES}"})
            with lock:
                state["mode"] = body["mode"]
            print(f"[sandbox] modo: {body['mode']}", flush=True)
            return self._json(200, {"mode": body["mode"]})

        m = re.fullmatch(r"/message/sendText/([^/?]+)", self.path)
        if not m:
            return self._json(404, {"error": "not found"})
        if API_KEY and self.headers.get("apikey") != API_KEY:
            return self._json(401, {"status": 401, "error": "Unauthorized"})
        if m.group(1) != INSTANCE:
            return self._json(404, {"status": 404, "error": f"instância {m.group(1)} não existe"})
        body = self._body()
        if not isinstance(body, dict) or not isinstance(body.get("text"), str):
            return self._json(400, {"status": 400, "error": "corpo inválido"})
        number = re.sub(r"\D", "", str(body.get("number", "")))
        with lock:
            mode = state["mode"]
        if mode == "down":
            return self._json(503, {"status": 503, "error": "sandbox em modo down"})
        if mode == "invalid-number" or not (10 <= len(number) <= 13):
            return self._json(400, {"status": 400, "response": {"message": [{"exists": False, "number": number}]}})
        msg = {
            "id": uuid.uuid4().hex[:20].upper(),
            "at": datetime.now(timezone.utc).astimezone().strftime("%d/%m %H:%M:%S"),
            "instance": m.group(1),
            "number": number,
            "text": body["text"],
        }
        with lock:
            messages.insert(0, msg)
            del messages[200:]
        print(f"[sandbox] mensagem para {number[:4]}*****{number[-4:]}", flush=True)
        return self._json(201, {"key": {"remoteJid": f"{number}@s.whatsapp.net", "fromMe": True, "id": msg["id"]},
                                "status": "PENDING"})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    print(f"[sandbox] ouvindo em :{port}, instância {INSTANCE}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
