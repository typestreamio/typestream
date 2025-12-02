{
  # Adapted from: https://github.com/the-nix-way/dev-templates/blob/main/kotlin/flake.nix
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
      # Some overlay to inject your dependencies (pkgs.jdk, pkgs.gradle, pkgs.kotlin) should be defined
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
      devShells = forEachSupportedSystem (
        { pkgs }:
        {
          # Standard Nix development shell using mkShell.
          # Packages are added to PATH and available for development.
          # The gradle wrapper will use the JDK from this shell as the bootstrap JDK.
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk
              gradle
              kotlin
              docker-compose
              go
              gcc
              pkg-config
              minikube
              bash
              nodePackages.pnpm
              buf
              nodejs_24
              nix-ld
              patchelf
              protobuf
            ];

            shellHook = ''
              export NIX_LD_LIBRARY_PATH="${pkgs.stdenv.cc.cc.lib}/lib:${pkgs.glibc.out}/lib"
              export NIX_LD="${pkgs.stdenv.cc.bintools.dynamicLinker}"

              # Use nixpkgs protoc instead of Gradle-downloaded version to avoid NixOS linking issues
              export PROTOC_BINARY_PATH="${pkgs.protobuf}/bin/protoc"
            '';
          };
        }
      );
    };
}
