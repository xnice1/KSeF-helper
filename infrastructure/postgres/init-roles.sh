#!/bin/sh
set -eu

role_name_pattern='^[A-Za-z_][A-Za-z0-9_]*$'
for role in "$DB_MIGRATION_ROLE" "$DB_RUNTIME_ROLE" "$DB_AUDIT_MAINTENANCE_ROLE"; do
  if ! printf '%s' "$role" | grep -Eq "$role_name_pattern"; then
    echo "Invalid PostgreSQL role name: $role" >&2
    exit 1
  fi
done

psql --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=migration_role="$DB_MIGRATION_ROLE" \
  --set=migration_password="$DB_MIGRATION_PASSWORD" \
  --set=runtime_role="$DB_RUNTIME_ROLE" \
  --set=runtime_password="$DB_RUNTIME_PASSWORD" \
  --set=audit_role="$DB_AUDIT_MAINTENANCE_ROLE" \
  --set=audit_password="$DB_AUDIT_MAINTENANCE_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'migration_role', :'migration_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_role') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'migration_role', :'migration_password') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'runtime_role', :'runtime_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'runtime_role') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'runtime_role', :'runtime_password') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'audit_role', :'audit_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'audit_role') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'audit_role', :'audit_password') \gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', current_database(), :'migration_role') \gexec
SELECT format('ALTER SCHEMA public OWNER TO %I', :'migration_role') \gexec
SQL
