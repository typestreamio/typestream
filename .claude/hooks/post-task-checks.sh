#!/usr/bin/env bash
# Post-task hook: runs lint/build/test based on modified files
# This script checks git status for modified files and runs appropriate checks

set -e

cd "$(git rev-parse --show-toplevel)"

# Get list of modified files (staged, unstaged, and untracked)
MODIFIED_FILES=$(git status --porcelain | awk '{print $NF}')

if [ -z "$MODIFIED_FILES" ]; then
    exit 0
fi

FRONTEND_CHANGED=false
BACKEND_CHANGED=false

# Check which directories have changes
while IFS= read -r file; do
    if [[ "$file" == uiv2/* ]]; then
        FRONTEND_CHANGED=true
    fi
    if [[ "$file" == server/* ]]; then
        BACKEND_CHANGED=true
    fi
done <<< "$MODIFIED_FILES"

# Run frontend checks if uiv2/ was modified
if [ "$FRONTEND_CHANGED" = true ]; then
    echo "🔍 Frontend changes detected in uiv2/, running checks..."
    cd uiv2

    echo "📋 Running pnpm lint..."
    if ! pnpm lint; then
        echo "❌ Lint failed"
        echo '{"decision": "block", "reason": "pnpm lint failed in uiv2/. Please fix the lint errors."}'
        exit 0
    fi

    echo "🔨 Running pnpm build..."
    if ! pnpm build; then
        echo "❌ Build failed"
        echo '{"decision": "block", "reason": "pnpm build failed in uiv2/. Please fix the build errors."}'
        exit 0
    fi

    echo "🧪 Running pnpm test..."
    if ! pnpm test; then
        echo "❌ Tests failed"
        echo '{"decision": "block", "reason": "pnpm test failed in uiv2/. Please fix the failing tests."}'
        exit 0
    fi

    echo "✅ Frontend checks passed!"
    cd ..
fi

# Run backend checks if server/ was modified
if [ "$BACKEND_CHANGED" = true ]; then
    echo "🔍 Backend changes detected in server/, running checks..."

    echo "🧪 Running ./gradlew test..."
    if ! ./gradlew test; then
        echo "❌ Gradle tests failed"
        echo '{"decision": "block", "reason": "./gradlew test failed in server/. Please fix the failing tests."}'
        exit 0
    fi

    echo "✅ Backend checks passed!"
fi

exit 0
