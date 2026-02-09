#!/usr/bin/env bash
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-typestream-demo}"
REMOTE_DIR="${REMOTE_DIR:-~/typestream}"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.demo.yml"

echo "==> Deploying demo to ${REMOTE_HOST}:${REMOTE_DIR}"

ssh "$REMOTE_HOST" bash -s "$REMOTE_DIR" <<'REMOTE_SCRIPT'
set -euo pipefail
REPO_DIR="$1"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.demo.yml"
cd "$REPO_DIR"

echo "==> Pulling latest code..."
git pull --ff-only

echo "==> Pulling latest images from GHCR..."
$COMPOSE pull server kafka-connect

echo "==> Rebuilding and starting all services..."
$COMPOSE up -d --build --remove-orphans

echo ""
echo "==> Deploy complete! Watchtower will auto-update on new releases."
$COMPOSE ps
REMOTE_SCRIPT
