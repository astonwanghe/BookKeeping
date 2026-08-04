#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${1:-.env.test}"
NETWORK="pixel-ledger-local"
MYSQL_CONTAINER="mysql"
REDIS_CONTAINER="redis"

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE" >&2; exit 1; }
docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK" >/dev/null

start_or_run() {
  local name="$1"; shift
  if docker container inspect "$name" >/dev/null 2>&1; then
    docker start "$name" >/dev/null 2>&1 || true
  else
    docker run -d --name "$name" "$@" >/dev/null
  fi
}

start_or_run "$MYSQL_CONTAINER" \
  --restart unless-stopped \
  --network "$NETWORK" \
  --env-file "$ENV_FILE" \
  -p 127.0.0.1:3306:3306 \
  -v pixel-ledger-mysql-local-data:/var/lib/mysql \
  --health-cmd='mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD"' \
  --health-interval=10s --health-timeout=5s --health-retries=12 \
  mysql:9.7 --default-time-zone=+08:00

start_or_run "$REDIS_CONTAINER" \
  --restart unless-stopped \
  --network "$NETWORK" \
  -p 127.0.0.1:6379:6379 \
  -v pixel-ledger-redis-local-data:/data \
  --health-cmd='redis-cli ping | grep -q PONG' \
  --health-interval=10s --health-timeout=5s --health-retries=12 \
  redis:8.8 redis-server --appendonly yes

echo "Waiting for local MySQL and Redis..."
for container in "$MYSQL_CONTAINER" "$REDIS_CONTAINER"; do
  for _ in {1..30}; do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container")" == "healthy" ]] && break
    sleep 2
  done
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container")" == "healthy" ]] || { echo "$container did not become healthy" >&2; exit 1; }
done
echo "Local data stores are ready on 127.0.0.1:3306 and 127.0.0.1:6379."
