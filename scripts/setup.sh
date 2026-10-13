#!/usr/bin/env bash
# One-time (and safe to repeat) setup for macOS/Linux: checks tools, creates .env, starts Postgres, builds the jar.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v java >/dev/null || fail "Java not found. Install JDK 25."
major=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
[ "${major:-0}" -ge 25 ] || fail "JDK 25 or newer is required (found ${major:-unknown})."
docker info >/dev/null 2>&1 || fail "Docker is not running. Start Rancher Desktop (dockerd/moby backend) or Docker Desktop."

if [ ! -f .env ]; then
  cp .env.example .env
  sed -i.bak "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 16)|; s|^JWT_SIGNING_KEY=.*|JWT_SIGNING_KEY=$(openssl rand -hex 32)|" .env
  rm -f .env.bak
  echo "Created .env with a database password and signing key."
fi
echo "Add ANTHROPIC_API_KEY and OPENAI_API_KEY to .env (needed for the real model; 'run.sh mock' needs neither)."

docker compose up -d --wait postgres
./mvnw -B -q -DskipTests package
echo "Setup done. Start the app with scripts/run.sh"
