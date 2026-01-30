import { resolve } from "path";
import { defineConfig } from "vite";
import handlebars from "vite-plugin-handlebars";

export default defineConfig({
  root: "src",
  publicDir: "../public",
  build: {
    outDir: "../dist",
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: resolve(__dirname, "src/index.html"),
        about: resolve(__dirname, "src/about.html"),
        contact: resolve(__dirname, "src/contact.html"),
        error: resolve(__dirname, "src/error.html"),
        integrations: resolve(__dirname, "src/integrations.html"),
        privacy: resolve(__dirname, "src/privacy.html"),
        terms: resolve(__dirname, "src/terms.html"),
      },
    },
  },
  plugins: [
    handlebars({
      partialDirectory: resolve(__dirname, "src/partials"),
      context: {
        currentYear: new Date().getFullYear(),
      },
    }),
  ],
  css: {
    preprocessorOptions: {
      scss: {
        api: "modern-compiler",
        // TODO: Migrate SCSS files from @import to @use/@forward syntax.
        // The @import rule is deprecated in Sass and will be removed in Dart Sass 3.0.
        // See: https://sass-lang.com/documentation/at-rules/import
        silenceDeprecations: ["import"],
      },
    },
  },
});
