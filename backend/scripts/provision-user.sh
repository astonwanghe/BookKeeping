#!/usr/bin/env sh
set -eu

# Usage: API_URL=https://api.example.com ADMIN_KEY='...' sh scripts/provision-user.sh 13800138000 'temporary-password'
if [ "$#" -ne 2 ]; then
  echo "Usage: API_URL=https://api.example.com ADMIN_KEY='...' sh scripts/provision-user.sh PHONE PASSWORD" >&2
  exit 64
fi
: "${API_URL:?Set API_URL first}"
: "${ADMIN_KEY:?Set ADMIN_KEY first}"

curl --fail-with-body --silent --show-error \
  -X POST "$API_URL/admin/users" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: $ADMIN_KEY" \
  --data "{\"phone\":\"$1\",\"password\":\"$2\"}"
echo
