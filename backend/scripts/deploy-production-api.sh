#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE="${1:-.env.production}"
IMAGE="${2:?Usage: $0 [env-file] <api-image:tag>}"
NETWORK="pixel-ledger-prod"
NAME="pixel-ledger-api"

[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE" >&2; exit 1; }
docker network inspect "$NETWORK" >/dev/null 2>&1 || { echo "Run scripts/start-production-datastores.sh first" >&2; exit 1; }
docker pull "$IMAGE"
docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" --restart unless-stopped \
  --network "$NETWORK" \
  --env-file "$ENV_FILE" \
  -e DB_HOST=mysql \
  -e REDIS_HOST=redis \
  -p 127.0.0.1:8080:8080 \
  "$IMAGE" >/dev/null

echo "API deployed as $NAME. Check logs with: docker logs -f $NAME"
