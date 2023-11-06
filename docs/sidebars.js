// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  sidebar: [
    { type: "doc", id: "getting-started" },
    {
      type: "category",
      label: "Tutorials",
      link: {
        type: "generated-index",
        title: "Tutorials",
        description:
          "Learn about the most important TypeStream via a series of steps to complete a project!",
        slug: "/tutorial",
        keywords: ["tutorials"],
      },
      items: ["tutorial/installation", "tutorial/configuration"],
    },
    {
      type: "category",
      label: "How-to Guides",
      link: {
        type: "generated-index",
        title: "How-to Guides",
        description:
          "Practical step-by-step guides to help you achieve a specific goa",
        slug: "/how-to",
        keywords: ["how to"],
      },
      items: [
        "how-to/filtering",
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
        description: "In-depth technical descriptions of how TypeStream works",
        slug: "/reference",
        keywords: ["reference"],
      },
      items: [
        "reference/glossary",
        "reference/language/specs",
        "reference/language/operators",
        "reference/language/shell",
        "reference/language/experiments",
      ],
    },
    {
      type: "category",
      label: "Concepts",
      link: {
        type: "generated-index",
        title: "Concepts",
        description: "Learn about the most important TypeStream concepts!",
        slug: "/concepts",
        keywords: ["concepts"],
      },
      items: ["concepts/components", "concepts/filesystem"],
    },
    { type: "doc", id: "roadmap" },
    { type: "doc", id: "changelog" },
  ],
};

module.exports = sidebars;
