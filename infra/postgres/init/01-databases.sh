#!/bin/bash
# Cria um banco e um usuario por servico (booking, notification, evolution).
# Roda so na primeira subida do volume do PostgreSQL. Senhas vem do ambiente do conteiner.
set -euo pipefail

: "${BOOKING_DB_PASSWORD:?defina BOOKING_DB_PASSWORD}"
# notification e evolution são opcionais: o perfil enxuto (compose.lite.yaml) só usa o banco booking

create_db() {
  local name="$1" password="$2"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v name="$name" -v password="$password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'name', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'name') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'name', :'name')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'name') \gexec
SQL
}

create_db booking "$BOOKING_DB_PASSWORD"
if [[ -n "${NOTIFICATION_DB_PASSWORD:-}" ]]; then create_db notification "$NOTIFICATION_DB_PASSWORD"; fi
if [[ -n "${EVOLUTION_DB_PASSWORD:-}" ]]; then create_db evolution "$EVOLUTION_DB_PASSWORD"; fi

# A extensao btree_gist (exclusion constraint de agendamentos) exige superusuario para criar
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname booking -c 'CREATE EXTENSION IF NOT EXISTS btree_gist'
