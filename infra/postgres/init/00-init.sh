#!/usr/bin/env sh
set -eu

psql -v ON_ERROR_STOP=1 \
  -v db_name="$POSTGRES_DB" \
  -v app_admin_pwd="$POSTGRES_APP_ADMIN_PASSWORD" \
  -v ai_pwd="$POSTGRES_AI_READONLY_PASSWORD" \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -f /docker-entrypoint-initdb.d/00-roles-and-schemas.sql
