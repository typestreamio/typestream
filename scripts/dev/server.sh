#!/usr/bin/env bash
# Run TypeStream server locally with hot reload

set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

export TYPESTREAM_CONFIG_PATH="$SCRIPT_DIR"

cd "$PROJECT_ROOT"

echo "Starting TypeStream server with hot reload..."
echo "Config path: $TYPESTREAM_CONFIG_PATH"
echo ""

./gradlew server:run --continuous
