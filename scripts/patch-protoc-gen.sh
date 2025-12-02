#!/usr/bin/env bash

# This script patches the protoc-gen-grpc-java binary to work on NixOS
# Run this after gradle has downloaded but failed to run the binary

set -euo pipefail

GRPC_JAVA_PATH="$HOME/.gradle/caches/modules-2/files-2.1/io.grpc/protoc-gen-grpc-java"

# Check if we're in a Nix shell with needed environment variables
if [ -z "${NIX_LD:-}" ] || [ -z "${NIX_LD_LIBRARY_PATH:-}" ]; then
  echo "⚠️  Warning: NIX_LD or NIX_LD_LIBRARY_PATH not set."
  echo "   Make sure you're in the Nix dev shell: nix develop"
fi

if ! command -v patchelf &>/dev/null; then
  echo "❌ patchelf not found. Please ensure you're in the Nix shell (nix develop)."
  exit 1
fi

echo "🔧 Found patchelf, patching protoc-gen-grpc-java binaries..."

# Find all protoc-gen-grpc-java binaries in gradle cache
found=0
patched=0

while read -r binary; do
  found=$((found + 1))
  echo "  Patching $binary"

  # Try to patch the binary
  if patchelf --set-interpreter "${NIX_LD}" \
             --set-rpath "${NIX_LD_LIBRARY_PATH}" \
             "$binary" 2>/dev/null; then
    echo "    ✅ Successfully patched"
    patched=$((patched + 1))
  else
    echo "    ⚠️  Failed to patch (may already be patched or not compatible)"
  fi
done < <(find "$GRPC_JAVA_PATH" -name "protoc-gen-grpc-java-*.exe" -type f 2>/dev/null || true)

if [ "$found" -eq 0 ]; then
  echo "❌ No protoc-gen-grpc-java binaries found in Gradle cache."
  echo "   Run './gradlew generateProto' first to trigger download, then run this script again."
  exit 1
fi

echo ""
echo "✅ Patching complete! Found $found binaries, patched $patched."
echo "   Try running './gradlew generateProto' again."
