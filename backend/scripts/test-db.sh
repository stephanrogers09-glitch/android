#!/usr/bin/env bash
# Runs the database tests for the backend migrations against a throwaway Postgres database.
# Creates a fresh database, applies the local Supabase stub, applies every migration with
# ON_ERROR_STOP, runs the test files, then drops the database.
#
#   PGHOST=/path/to/socket PGPORT=5433 PGUSER=postgres backend/scripts/test-db.sh
#
# Needs psql and a running Postgres 15+ (Supabase uses 15 or newer). Nothing here touches a
# Supabase project; the stub in tests/local stands in for Supabase's auth schema and roles.
set -euo pipefail

BACKEND="$(cd "$(dirname "$0")/.." && pwd)"
DB="lockedin_test_$$"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "${PGHOST:-/var/run/postgresql}" -p "${PGPORT:-5432}" -U "${PGUSER:-postgres}")

cleanup() { "${PSQL[@]}" -d postgres -c "drop database if exists \"$DB\"" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== creating $DB"
"${PSQL[@]}" -d postgres -c "create database \"$DB\""

echo "== applying local Supabase stub"
"${PSQL[@]}" -d "$DB" -f "$BACKEND/supabase/tests/local/00_supabase_stub.sql"

echo "== applying migrations"
for f in "$BACKEND"/supabase/migrations/*.sql; do
  echo "   $(basename "$f")"
  "${PSQL[@]}" -d "$DB" -f "$f"
done

echo "== running database tests"
"${PSQL[@]}" -d "$DB" -f "$BACKEND/supabase/tests/local/10_foundation_test.sql"
"${PSQL[@]}" -d "$DB" -f "$BACKEND/supabase/tests/local/20_api_role_test.sql"

echo "== database tests passed"
