{ pkgs, lib, config, ... }:

{
  # Enable JavaScript/Node.js with npm and yarn
  languages.javascript = {
    enable = true;
    npm.enable = true;
    yarn = {
      enable = true;
      install.enable = true;
    };
  };

  # Development scripts
  scripts.dev.exec = ''
    npx gulp
  '';

  scripts.build.exec = ''
    npx gulp build
  '';

  scripts.format.exec = ''
    npm run prettier:write
  '';

  # Run dev server with `devenv up`
  processes.dev.exec = "npx gulp";

  enterShell = ''
    echo "TypeStream website dev environment"
    echo ""
    echo "Commands:"
    echo "  devenv up     - Start dev server with process-compose (TUI)"
    echo "  dev           - Start development server with live reload"
    echo "  build         - Build for production"
    echo "  format        - Format code with prettier"
  '';
}
