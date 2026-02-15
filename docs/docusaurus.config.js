const lightCodeTheme = require("./src/prism/monokai");

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: "TypeStream",
  tagline: "The official documentation for TypeStream",
  url: "https://docs.typestream.io",
  baseUrl: "/",
  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "throw",
  favicon: "img/favicon.ico",

  i18n: {
    defaultLocale: "en",
    locales: ["en"],
  },

  presets: [
    [
      "classic",
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          sidebarCollapsed: false,
          routeBasePath: "/",
        },
        blog: false,
        theme: {
          customCss: [require.resolve("./src/css/theme.scss")],
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        logo: {
          alt: "TypeStream Logo",
          src: "/img/header_logo.svg",
          srcDark: "/img/header_logo_dark.svg",
          width: 125,
          height: 125,
        },
        items: [
          {
            type: "doc",
            docId: "getting-started",
            position: "left",
            label: "Docs",
          },
          {
            type: "doc",
            docId: "/concepts",
            position: "right",
            label: "Concepts",
          },
          {
            type: "doc",
            docId: "/how-to",
            position: "right",
            label: "How-to Guides",
          },
          {
            type: "doc",
            docId: "/reference",
            position: "right",
            label: "Reference",
          },
        ],
      },
      footer: {
        links: [
          {
            title: "Links",
            items: [
              {
                label: "GitHub",
                href: "https://github.com/typestreamio",
              },

              {
                label: "Mastodon",
                href: "https://data-folks.masto.host/@typestream",
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()}.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: lightCodeTheme,
      },
    }),
  markdown: {
    mermaid: true,
  },
  themes: ["@docusaurus/theme-mermaid"],
  plugins: ["docusaurus-plugin-sass"],

  // https://johnnyreilly.com/preload-fonts-with-docusaurus
  headTags: [
    {
      tagName: "link",
      attributes: {
        rel: "preload",
        href: "/fonts/jetbrains.ttf",
        as: "font",
        type: "font/ttf",
        crossorigin: "anonymous",
      },
    },
  ],
  scripts: [
    {
      src: "/js/script.js",
      async: true,
      defer: true,
      "data-domain": "docs.typestream.io",
    },
  ],
};

module.exports = config;
