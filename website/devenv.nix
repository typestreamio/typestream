{ pkgs, lib, config, ... }:

{
  # Enable JavaScript/Node.js with npm
  languages.javascript = {
    enable = true;
    npm.enable = true;
  };

  # Development scripts
  scripts.dev.exec = ''
    npx vite --host
  '';

  scripts.build.exec = ''
    npx vite build
  '';

  scripts.preview.exec = ''
    npx vite preview --host
  '';

  scripts.format.exec = ''
    npm run prettier:write
  '';

  # Process for `devenv up`
  processes.vite = {
    exec = "npx vite --host";
    process-compose = {
      readiness_probe = {
        http_get = {
          host = "localhost";
          port = 5173;
          path = "/";
        };
        initial_delay_seconds = 2;
        period_seconds = 2;
      };
    };
  };

  enterShell = ''
    echo "TypeStream website dev environment"
    echo ""
    echo "Commands:"
    echo "  devenv up     - Start dev server with process-compose (TUI)"
    echo "  dev           - Start development server with HMR"
    echo "  build         - Build for production"
    echo "  preview       - Preview production build"
    echo "  format        - Format code with prettier"
  '';
}
