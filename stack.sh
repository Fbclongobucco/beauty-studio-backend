#!/usr/bin/env bash
# Sobe a stack em um dos perfis, com imagens construidas nesta maquina.
#   ./stack.sh local up -d --build
#   ./stack.sh prod  up -d --build
#   ./stack.sh lite  up -d --build   (servidor de 1 GB: só booking, Postgres, MinIO e Caddy)
#   ./stack.sh <perfil> ps | logs -f <servico> | down ...
# Na primeira execucao, cria .env.<perfil> a partir de .env.<perfil>.example e gera os segredos vazios.
set -euo pipefail
cd "$(dirname "$0")"

profile="${1:-}"
case "$profile" in
  local|prod|lite) shift ;;
  *) echo "uso: $0 local|prod|lite <comando do docker compose>" >&2; exit 2 ;;
esac

env_file=".env.$profile"
if [[ ! -f "$env_file" ]]; then
  # Segredos: preenchidos so quando vazios. Hex evita caracteres especiais na URI do Postgres.
  secrets='POSTGRES_PASSWORD|BOOKING_DB_PASSWORD|NOTIFICATION_DB_PASSWORD|EVOLUTION_DB_PASSWORD|RABBITMQ_PASSWORD|MINIO_ROOT_PASSWORD|EVOLUTION_API_KEY|APP_JWT_SECRET|ADMIN_PASSWORD'
  while IFS= read -r line; do
    if [[ "$line" =~ ^($secrets)=$ ]]; then
      echo "${BASH_REMATCH[1]}=$(openssl rand -hex 24)"
    else
      echo "$line"
    fi
  done < "$env_file.example" > "$env_file"
  chmod 600 "$env_file"
  echo "Criado $env_file com segredos gerados. Senha do master: ADMIN_PASSWORD em $env_file." >&2
  if [[ "$profile" == prod || "$profile" == lite ]]; then
    echo "Preencha os dominios, as URLs publicas, o CORS e as contas em $env_file antes de subir." >&2
    exit 1
  fi
fi

if [[ "$profile" == lite ]]; then
  # Arquivo independente: não herda os serviços do compose.yaml
  exec docker compose --env-file "$env_file" -f compose.lite.yaml "$@"
fi
exec docker compose --env-file "$env_file" -f compose.yaml -f "compose.$profile.yaml" "$@"
