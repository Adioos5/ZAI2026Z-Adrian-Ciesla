#!/usr/bin/env bash
# Builds and (re)starts the whole stack with the public HTTPS proxy. Run on the VM from the repo root:
#   bash deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "Missing .env - copy .env.example to .env and fill it in." >&2; exit 1; }
set -a; . ./.env; set +a

fail() { echo "Refusing to deploy: $1" >&2; exit 1; }
[ -n "${DOMAIN:-}" ] && [ "$DOMAIN" != "zai-example.polandcentral.cloudapp.azure.com" ] || fail "set DOMAIN in .env"
case "${POSTGRES_PASSWORD:-}" in ""|zai|change-me*) fail "set a real POSTGRES_PASSWORD in .env" ;; esac
case "${ADMIN_PASSWORD:-}" in ""|admin|change-me*) fail "set a real ADMIN_PASSWORD in .env" ;; esac
[ "${#ADMIN_PASSWORD}" -ge 8 ] || fail "ADMIN_PASSWORD must have at least 8 characters"

git pull --ff-only || echo "git pull skipped (not on a tracking branch or local changes)"
docker compose --profile prod up -d --build --remove-orphans

echo "Waiting for https://$DOMAIN/api/health ..."
for _ in $(seq 1 40); do
    if curl -fsS "https://$DOMAIN/api/health" >/dev/null 2>&1; then
        echo "Up: https://$DOMAIN/api/health"
        exit 0
    fi
    sleep 5
done
echo "Not reachable over HTTPS yet. Check: docker compose --profile prod logs caddy backend" >&2
exit 1
