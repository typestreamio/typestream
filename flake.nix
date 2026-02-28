{
  description = "A Nix-flake-based Kotlin development environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-25.11";
  };

  outputs =
    {
      self,
      nixpkgs,
    }:
    let
      javaVersion = 21;
      overlays = [
        (final: prev: rec {
          jdk = prev."jdk${toString javaVersion}";
          gradle = prev.gradle.override { java = jdk; };
          kotlin = prev.kotlin.override { jre = jdk; };
        })
      ];
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forEachSupportedSystem =
        f:
        nixpkgs.lib.genAttrs supportedSystems (
          system:
          f {
            pkgs = import nixpkgs {
              inherit system;
              overlays = overlays;
            };
          }
        );
    in
    {
      packages = forEachSupportedSystem (
        { pkgs }:
        {
          typestream = pkgs.buildGo124Module {
            pname = "typestream";
            version = "0.3.5";
            src = self;
            modRoot = "cli";
            vendorHash = "sha256-SaYkuKUEYGEX44qzl++fxlzVXyCjf3SmKiP9m8BGi5E=";
            ldflags = [
              "-s"
              "-w"
              "-X github.com/typestreamio/typestream/cli/pkg/version.Version=0.3.5"
              "-X github.com/typestreamio/typestream/cli/pkg/version.CommitHash=${self.shortRev or "dirty"}"
            ];
            postInstall = ''
              mv $out/bin/cli $out/bin/typestream
            '';
            doCheck = false;
            meta = {
              description = "TypeStream CLI - typed streaming pipelines";
              homepage = "https://github.com/typestreamio/typestream";
              license = pkgs.lib.licenses.asl20;
              mainProgram = "typestream";
            };
          };
          default = self.packages.${pkgs.system}.typestream;
        }
      );

      devShells = forEachSupportedSystem (
        { pkgs }:
        let
          patchProto = pkgs.writeShellScriptBin "patchProto" ''
            set -euo pipefail

            GRADLE_CACHE="$HOME/.gradle/caches"
            INTERPRETER="${pkgs.stdenv.cc.bintools.dynamicLinker}"
            RPATH="${pkgs.stdenv.cc.cc.lib}/lib:${pkgs.glibc.out}/lib"

            echo "🔍 Searching for protoc-gen-grpc-java binaries..."

            binaries=$(find "$GRADLE_CACHE" -name "protoc-gen-grpc-java-*-linux-x86_64.exe" 2>/dev/null || true)

            if [[ -z "$binaries" ]]; then
              echo "❌ No binaries found. Run './gradlew generateProto' first to download them."
              exit 1
            fi

            patched=0
            skipped=0

            while IFS= read -r binary; do
              current_interp=$(${pkgs.patchelf}/bin/patchelf --print-interpreter "$binary" 2>/dev/null || echo "")
              if [[ "$current_interp" == "$INTERPRETER" ]]; then
                echo "⏭️  Already patched: $(basename "$binary")"
                skipped=$((skipped + 1))
                continue
              fi

              if ${pkgs.patchelf}/bin/patchelf --set-interpreter "$INTERPRETER" --set-rpath "$RPATH" "$binary" 2>/dev/null; then
                echo "✅ Patched: $(basename "$binary")"
                patched=$((patched + 1))
              else
                echo "❌ Failed: $(basename "$binary")"
              fi
            done <<< "$binaries"

            echo ""
            echo "Done! Patched: $patched, Skipped: $skipped"
            if [[ $patched -gt 0 ]]; then
              echo "Run './gradlew generateProto' again."
            fi
          '';
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk
              gradle
              kotlin
              docker-compose
              go
              golangci-lint
              gcc
              pkg-config
              minikube
              bash
              nodePackages.pnpm
              buf
              nodejs_22
              patchelf
              protobuf
              protoc-gen-doc
              patchProto
            ];

            shellHook = ''
              export NIX_LD_LIBRARY_PATH="${pkgs.stdenv.cc.cc.lib}/lib:${pkgs.glibc.out}/lib"
              export NIX_LD="${pkgs.stdenv.cc.bintools.dynamicLinker}"

              echo "🔧 TypeStream dev environment"
              echo "   Run 'patchProto' after './gradlew generateProto' fails on NixOS"
            '';
          };
        }
      );
    };
}
