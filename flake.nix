{
  # Adapted from: https://github.com/the-nix-way/dev-templates/blob/main/kotlin/flake.nix
  description = "A Nix-flake-based Kotlin development environment";

  inputs = { nixpkgs.url = "github:NixOS/nixpkgs/nixos-23.11"; };

  outputs = { self, nixpkgs }:
    let
      javaVersion = 20;
      # Some overlay to inject your dependencies (pkgs.jdk, pkgs.gradle, pkgs.kotlin) should be defined
      overlays = [
        (final: prev: rec {
          jdk = prev."jdk${toString javaVersion}";
          gradle = prev.gradle.override { java = jdk; };
          kotlin = prev.kotlin.override { jre = jdk; };
        })
      ];
      supportedSystems =
        [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f:
        nixpkgs.lib.genAttrs supportedSystems
        (system: f { pkgs = import nixpkgs { inherit system; }; });
    in {
      devShells = forEachSupportedSystem ({ pkgs }: {
        # https://ryantm.github.io/nixpkgs/builders/special/fhs-environments/#sec-fhs-environments
        # WARNING! As I understand, with this configuration your shell will be running in a namespace.
        # It isolates the filesystem (or at least a part of the filesystem) to behave as a standard
        # FHS system. gradlew should work normally, thus handling all your dependencies by itself.
        # With this configuration, your only required dependency is the jdk17.
        # Thus it is not a packaging flake, but rather a flake to enable development for your tool.
        default = (pkgs.buildFHSUserEnv {
          name = "java-dev-env";
          targetPkgs = pkgs:
            with pkgs; [
              jdk17
              # Gradle for building containers
              gradle
              # For the cli
              docker-compose
            ];
          runScript = "bash";
        }).env;
      });
    };
}
