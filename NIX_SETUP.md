# NixOS Development Setup Guide

This guide explains how to set up TypeStream development on NixOS.

**This is only needed for NixOS or Nix environment users** - non-Nix users (macOS, Ubuntu, etc.) don't need any special setup and can run `./gradlew build` directly.

## Quick Start

```bash
# Enter the Nix development shell
nix develop

# First build attempt (will fail, but downloads protoc plugins)
./gradlew generateProto

# Patch the downloaded binaries for NixOS
patchProto

# Build the project (now succeeds)
./gradlew build
```

## Problem: NixOS Protobuf Generation Errors

When building TypeStream on NixOS, you may encounter:

```
Could not start dynamically linked executable: protoc-gen-grpc-java-1.57.2-linux-x86_64.exe
NixOS cannot run dynamically linked executables intended for generic linux environments out of the box.
```

### Root Cause

Gradle's protobuf plugin downloads dynamically-linked `protoc-gen-grpc-java` binaries that don't work on NixOS's non-FHS system.

### Solution

The flake includes a `patchProto` script that uses `patchelf` to fix the binary's interpreter and library paths.

## Workflow

1. **Enter Nix shell**: `nix develop`
2. **Trigger download**: `./gradlew generateProto` (will fail)
3. **Patch binaries**: `patchProto`
4. **Build**: `./gradlew build`

The `patchProto` script:
- Finds all `protoc-gen-grpc-java` binaries in `~/.gradle/caches`
- Uses `patchelf` to set the correct interpreter and rpath
- Skips already-patched binaries
- Only needs to run once per new protoc-gen version

## Troubleshooting

### "No binaries found"

Run `./gradlew generateProto` first to trigger the download, then run `patchProto`.

### Version mismatch errors

```bash
./gradlew clean generateProto
patchProto
./gradlew build
```

## What the Flake Provides

- JDK 21, Gradle, Kotlin
- Node.js 22, pnpm, buf (for frontend/proto generation)
- Go, GCC, Docker Compose, Minikube
- `patchProto` script for NixOS protobuf fix
- NIX_LD environment variables for dynamic linking compatibility
