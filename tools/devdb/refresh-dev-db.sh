#!/usr/bin/env bash
#
# Rebuild the local dev database as a copy of prod.
#
# READ-ONLY against prod. The only thing this script runs against Supabase is
# pg_dump. It never writes there, and it never runs DDL there. Everything
# destructive targets the local container only.
#
# Why a copy of prod at all: the server tests run on H2, which infers parameter
# types that PostgreSQL will not. A query missing the `cast(:p as timestamp)`
# idiom passes every test and then 500s in production (SQLState 42P18). A
# local PostgreSQL of the same major version is the only place that shows up
# before a deploy.
#
# Usage:
#   tools/devdb/refresh-dev-db.sh              # dump prod, rebuild local db
#   tools/devdb/refresh-dev-db.sh --keep-dump  # also leave the .sql on disk
#   tools/devdb/refresh-dev-db.sh --schema-only
#
set -euo pipefail

# Overridable so the script can be exercised against a throwaway container
# without disturbing the one a running server is attached to.
CONTAINER="${FEEDS_PG_CONTAINER:-feeds-pg}"
HOST_PORT="${FEEDS_PG_PORT:-5433}"
LOCAL_USER=feeds
LOCAL_PASS=feeds
LOCAL_DB=feeds

KEEP_DUMP=0
DUMP_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --keep-dump)   KEEP_DUMP=1 ;;
    --schema-only) DUMP_ARGS+=(--schema-only) ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

repo_root() { cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd; }
ROOT="$(repo_root)"

# supabase.connection is gitignored, so it exists in the main checkout only - a
# git worktree will not have its own copy. Look there too before giving up.
find_conn() {
  if [ -n "${SUPABASE_CONNECTION:-}" ]; then echo "$SUPABASE_CONNECTION"; return; fi
  if [ -f "$ROOT/supabase.connection" ]; then echo "$ROOT/supabase.connection"; return; fi
  local common main
  common="$(git -C "$ROOT" rev-parse --git-common-dir 2>/dev/null)" || return
  case "$common" in /*) ;; *) common="$ROOT/$common" ;; esac
  main="$(cd "$(dirname "$common")" && pwd)"
  [ -f "$main/supabase.connection" ] && echo "$main/supabase.connection"
}

CONN_FILE="$(find_conn || true)"
if [ -z "$CONN_FILE" ] || [ ! -f "$CONN_FILE" ]; then
  echo "cannot find supabase.connection (it is gitignored)." >&2
  echo "Set SUPABASE_CONNECTION=/path/to/supabase.connection, or run from the main checkout." >&2
  exit 1
fi
echo "credentials: $CONN_FILE"
command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }

CONN="$(cat "$CONN_FILE")"
PGPASSWORD="$(printf '%s' "$CONN" | sed -E 's/.*[?&]password=([^&]*).*/\1/')"
PROJECT_REF="$(printf '%s' "$CONN" | sed -E 's#jdbc:postgresql://db\.([^.]+)\.supabase\.co.*#\1#')"
export PGPASSWORD

# The direct host db.<ref>.supabase.co publishes an AAAA record only. Docker's
# bridge network has no IPv6, so a container cannot reach it. The session
# pooler is the IPv4 route, and it is the same one the k8s cluster uses (see
# k8s/README.md). The transaction pooler on :6543 would break pg_dump, so this
# must stay on the session pooler's 5432.
POOL_HOST=aws-0-eu-central-1.pooler.supabase.com
POOL_USER="postgres.${PROJECT_REF}"
POOL_PORT=5432

# This machine's Docker cannot resolve DNS from inside containers, so pin the
# hostname to a resolved address. Using the hostname (not the bare IP) also
# keeps TLS SNI correct.
POOL_IP="$(dig +short "$POOL_HOST" A | tail -1)"
[ -n "$POOL_IP" ] || { echo "could not resolve $POOL_HOST" >&2; exit 1; }

pooler() {
  docker run --rm --add-host "$POOL_HOST:$POOL_IP" -e PGPASSWORD \
    "$PG_IMAGE" "$@"
}

# --- version match ------------------------------------------------------------
# pg_dump refuses to dump a server newer than itself, so the client major
# version must be >= prod's. Ask prod, then use a matching image.
PROBE_IMAGE=postgres:17-alpine
PG_IMAGE="$PROBE_IMAGE"
SERVER_VERSION="$(pooler psql \
  "host=$POOL_HOST port=$POOL_PORT dbname=postgres user=$POOL_USER sslmode=require" \
  -tAc "show server_version;" | tr -d '[:space:]')"
SERVER_MAJOR="${SERVER_VERSION%%.*}"
echo "prod PostgreSQL: $SERVER_VERSION"

WANTED="postgres:${SERVER_MAJOR}-alpine"
if docker image inspect "$WANTED" >/dev/null 2>&1; then
  PG_IMAGE="$WANTED"
else
  echo "note: $WANTED not present locally; using $PG_IMAGE"
  CLIENT_MAJOR="${PG_IMAGE#postgres:}"; CLIENT_MAJOR="${CLIENT_MAJOR%%-*}"
  if [ "$CLIENT_MAJOR" -lt "$SERVER_MAJOR" ]; then
    echo "ERROR: client major $CLIENT_MAJOR < server major $SERVER_MAJOR." >&2
    echo "pg_dump will refuse. Run: docker pull $WANTED" >&2
    exit 1
  fi
fi

# --- dump prod (read-only) ----------------------------------------------------
DUMP="$(mktemp -t feeds-prod-dump)"
cleanup() { [ "$KEEP_DUMP" -eq 1 ] || rm -f "$DUMP"; }
trap cleanup EXIT

echo "dumping prod public schema via the session pooler..."
pooler pg_dump \
  "host=$POOL_HOST port=$POOL_PORT dbname=postgres user=$POOL_USER sslmode=require" \
  --schema=public --no-owner --no-privileges "${DUMP_ARGS[@]+"${DUMP_ARGS[@]}"}" > "$DUMP"
echo "dump: $(wc -c < "$DUMP" | tr -d ' ') bytes"

# --- rebuild the local container ---------------------------------------------
if [ -n "$(docker ps -aq -f "name=^${CONTAINER}$")" ]; then
  echo "removing the old $CONTAINER container"
  docker rm -f "$CONTAINER" >/dev/null
fi

echo "starting $CONTAINER on :$HOST_PORT"
docker run -d --name "$CONTAINER" \
  -e POSTGRES_USER="$LOCAL_USER" \
  -e POSTGRES_PASSWORD="$LOCAL_PASS" \
  -e POSTGRES_DB="$LOCAL_DB" \
  -p "${HOST_PORT}:5432" \
  "$PG_IMAGE" >/dev/null

printf "waiting for postgres"
until docker exec "$CONTAINER" pg_isready -U "$LOCAL_USER" -d "$LOCAL_DB" >/dev/null 2>&1; do
  printf "."
  sleep 1
done
echo

echo "restoring..."
# "schema public already exists" is expected and harmless - the fresh database
# ships with one. Anything else is worth reading.
docker exec -i "$CONTAINER" psql -U "$LOCAL_USER" -d "$LOCAL_DB" -q < "$DUMP" 2>&1 \
  | grep -v 'schema "public" already exists' \
  | grep -iE "error" || true

# --- report -------------------------------------------------------------------
echo
echo "local dev database ready:"
docker exec "$CONTAINER" psql -U "$LOCAL_USER" -d "$LOCAL_DB" -tAc "
  select table_name || ' = ' || (
    xpath('/row/c/text()',
          query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name),
                       false, true, ''))
  )[1]::text
  from information_schema.tables
  where table_schema = 'public' and table_type = 'BASE TABLE'
  order by table_name;"

[ "$KEEP_DUMP" -eq 1 ] && echo && echo "dump kept at: $DUMP (contains real personal data)"

cat <<EOF

Run the server against it with the same env vars prod uses:

  DB_URL=localhost:${HOST_PORT}/${LOCAL_DB} \\
  DB_USERNAME=${LOCAL_USER} \\
  DB_PASSWORD=${LOCAL_PASS} \\
  GOOGLE_CLIENT_ID=<the shared Google Web client id> \\
  ./gradlew bootRun

Do NOT pass SPRING_PROFILES_ACTIVE=localdev - that switches to in-memory H2 and
throws this copy away.
EOF
