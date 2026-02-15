// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  sidebar: [
    { type: "doc", id: "getting-started" },
    { type: "doc", id: "installation" },
    {
      type: "category",
      label: "Concepts",
      link: {
        type: "generated-index",
        title: "Concepts",
        description: "Understand how TypeStream works under the hood.",
        slug: "/concepts",
        keywords: ["concepts"],
      },
      items: [
        "concepts/how-typestream-works",
        "concepts/three-ways",
        "concepts/virtual-filesystem",
        "concepts/schema-propagation",
        "concepts/cdc-and-debezium",
        "concepts/components",
      ],
    },
    {
      type: "category",
      label: "How-to Guides",
      link: {
        type: "generated-index",
        title: "How-to Guides",
        description: "Step-by-step recipes for common tasks.",
        slug: "/how-to",
        keywords: ["how to"],
      },
      items: [
        "how-to/filter-and-route",
        "how-to/geo-enrich-events",
        "how-to/setup-postgres-cdc",
        "how-to/join-two-topics",
        "how-to/real-time-aggregations",
        "how-to/pipeline-as-code",
        "how-to/enrich-with-ai",
        "how-to/add-semantic-search",
        "how-to/sink-to-elasticsearch",
        "how-to/enriching",
        "how-to/export",
        "how-to/run",
      ],
    },
    {
      type: "category",
      label: "Reference",
      link: {
        type: "generated-index",
        title: "Reference",
        description: "Detailed specifications and API docs.",
        slug: "/reference",
        keywords: ["reference"],
      },
      items: [
        "reference/node-reference",
        "reference/cli-commands",
        "reference/pipeline-file-format",
        "reference/configuration",
        "reference/api",
        "reference/glossary",
        "reference/language/operators",
        "reference/language/shell",
      ],
    },
  ],
};

module.exports = sidebars;
