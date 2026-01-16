#!/usr/bin/env bash
# Run TypeStream server locally with hot reload

set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load .env file if it exists (for OPENAI_API_KEY, etc.)
ENV_FILE="$PROJECT_ROOT/cli/pkg/compose/.env"
if [[ -f "$ENV_FILE" ]]; then
    echo "Loading environment from $ENV_FILE"
    set -a  # automatically export all variables
    source "$ENV_FILE"
    set +a
fi

export TYPESTREAM_CONFIG_PATH="$SCRIPT_DIR"
export TIKA_URL="http://localhost:9998"

cd "$PROJECT_ROOT"

echo "Starting TypeStream server with hot reload..."
echo "Config path: $TYPESTREAM_CONFIG_PATH"
echo ""

./gradlew server:run --continuous
