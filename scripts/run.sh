#!/usr/bin/env bash
# Starts Postgres if needed and runs the packaged app. Usage: scripts/run.sh [mock]
# Default profile is "demo" (real Anthropic + OpenAI calls); "mock" needs no API keys.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "ERROR: .env is missing. Run scripts/setup.sh first." >&2; exit 1; }
set -a; . ./.env; set +a
docker compose up -d --wait postgres

jar=$(ls target/spring-AI-app-*.jar 2>/dev/null | head -n 1 || true)
[ -n "$jar" ] || { echo "ERROR: no jar in target/. Run scripts/setup.sh first." >&2; exit 1; }

export SPRING_PROFILES_ACTIVE="${1:-demo}"
echo "Starting on http://localhost:8080 (profile: $SPRING_PROFILES_ACTIVE)"
exec java -jar "$jar"
