#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${1:-.env.production}"
NETWORK="pixel-ledger-prod"
[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE" >&2; exit 1; }
docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK" >/dev/null

if ! docker container inspect mysql >/dev/null 2>&1; then
  docker run -d --name mysql --restart unless-stopped \
    --network "$NETWORK" --env-file "$ENV_FILE" \
    -v mysql-data:/var/lib/mysql \
    --health-cmd='mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD"' \
    --health-interval=10s --health-timeout=5s --health-retries=12 \
    mysql:9.7 --default-time-zone=+08:00
else
  docker start mysql >/dev/null 2>&1 || true
fi

if ! docker container inspect redis >/dev/null 2>&1; then
  docker run -d --name redis --restart unless-stopped \
    --network "$NETWORK" \
    -v pixel-ledger-redis-prod-data:/data \
    --health-cmd='redis-cli ping | grep -q PONG' \
    --health-interval=10s --health-timeout=5s --health-retries=12 \
    redis:8.8 redis-server --appendonly yes
else
  docker start redis >/dev/null 2>&1 || true
fi

echo "Waiting for production MySQL and Redis..."
for container in mysql redis; do
  for _ in {1..30}; do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container")" == "healthy" ]] && break
    sleep 2
  done
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container")" == "healthy" ]] || {
    echo "$container did not become healthy" >&2
    exit 1
  }
done

echo "Production MySQL and Redis are healthy on private network $NETWORK."
