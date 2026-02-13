# TypeStream Website Rebuild

## Background & Reasoning

### The Feedback That Drove This

We conducted outreach to ~10 engineering leaders. The signal was clear: **the product is further along than the messaging**, and **we were targeting the wrong audience**.

People who already run Kafka and felt the stream processing gap got it immediately (Pantelis at HelloFresh, Mohammed at Clio, Tim at Clio). People without Kafka or who'd already solved data movement with Fivetran/Snowflake didn't see the value (Dui at PayNearMe, Pablo at Pexels).

The original positioning -- "stop writing sync jobs, cache invalidation, and API glue" -- targets application developers tired of glue code. But that pitch lands on deaf ears for teams that already solved data movement AND for teams that don't feel the pain. The people who responded strongest were **teams with Kafka that are underusing it**.

### Key findings

**Strongest positive signal: teams with Kafka**
- Pantelis at HelloFresh: has Kafka + Schema Registry, spent Thursday and Friday manually wiring a pipeline. Immediately understood the value. Has budget autonomy, high risk tolerance, actively willing to try it.
- Mohammed at Clio: has streaming infrastructure, immediately saw where TypeStream fits (text in DB -> embeddings -> vector store). Raised schema evolution and intermediate transforms as real needs.
- Tim at Clio: infrastructure engineer, said it "looks dope as hell." First question was about deployment workflow and config-as-code.

**Weakest signal: teams without Kafka**
- Dui at PayNearMe: "the wiring isn't hard" -- already on Fivetran/Snowflake, hard to dislodge. Real pain is messy data and lost institutional knowledge, not plumbing.
- Pablo at Pexels: doesn't see the value. "Can't trigger on every DB write." Engineers prefer config files to visual builders. Adding another service to infra is scary.
- Geoff at Fullscript: "I'm not sure what this is." Compared it to n8n. Wants an AI angle.
- Alexis at Explorance: "How is this different than n8n?"

**Recurring patterns across all feedback:**

1. **Config-as-code is table stakes** -- Tim, Pablo, Pantelis, Mohammed, and an anonymous EM all said engineers won't clickops pipelines. We built config-as-code + terraform-like plan, but it was invisible on the old site.

2. **n8n confusion from visual builder** -- Alexis and Geoff independently compared TypeStream to n8n. Leading with the visual builder signals "low-code workflow automation," which is wrong.

3. **Schema awareness is undervalued** -- Stuart (OneVest) said "the power would be in knowing what kind of operations... since you have typing, that's huge vs plain old Kafka." Pantelis's biggest pain at HelloFresh is schema evolution.

4. **"Existing systems" story doesn't come through** -- Stuart assumed greenfield-only. The additive framing ("hook it up, try one pipeline, add more") resonated once explained but wasn't on the site.

### The Pivot: Target Kafka Teams

The insight that changes everything: **most teams adopt Kafka for event streaming and stop there**. Topics are flowing, Schema Registry might be set up, maybe they have CDC via Debezium. But nobody's doing stream processing, joins, aggregations, or enrichment -- because every use case requires writing a JVM microservice.

TypeStream's real value isn't "stop writing sync jobs." It's: **you already have Kafka, and you're barely using it. TypeStream turns your topics into real-time pipelines without writing Java.**

This also makes go-to-market concrete: we can find these companies through job postings mentioning Kafka, StackShare profiles, Confluent community, Kafka meetups, and the Debezium Slack.

---

## Strategic Positioning Shifts

**Shift 1: Target teams with Kafka, not everyone with a database**
- Old: "Application developers tired of writing glue code"
- New: "Engineering teams that run Kafka but aren't getting full value from it"
- Why: Pantelis (HelloFresh, has Kafka + Schema Registry) was our strongest signal. Dui (Fivetran/Snowflake, no Kafka) and Pablo (no streaming infrastructure) didn't see the value.

**Shift 2: Lead with what's unique, not generic CDC**
- Old: "A row changes in your database. Everything else updates automatically."
- New: "You already run Kafka. TypeStream turns it into a real-time data platform."
- Why: The old headline describes Debezium, Fivetran, and every CDC tool. Chris Cranford (Debezium core team) literally told us Debezium is adding cache invalidation and search index features. Our differentiator is the compilation + config + plan workflow on top of Kafka, not the data movement itself.

**Shift 3: Config-as-code is the primary interface**
- Old: Visual builder front and center, config buried
- New: Config file first, CLI second, visual builder third
- Why: Five people independently said engineers want config files. Two people confused the visual builder with n8n. Leading with config signals "infrastructure tooling for engineers."

**Shift 4: Schema awareness is a headline feature**
- Old: Not mentioned on the site
- New: Prominently featured -- "TypeStream reads your Schema Registry and propagates types through every node"
- Why: Stuart and Pantelis both flagged this as the real differentiator. For Kafka teams, automatic schema propagation vs. manual serde management is a genuine "wow" moment.

**Shift 5: Compare against writing Kafka Streams by hand, not n8n**
- Old: No competitive comparison (then: n8n comparison on rebuilt site)
- New: Primary comparison is TypeStream vs. writing Kafka Streams code. Secondary comparison vs. workflow tools (n8n).
- Why: Our buyer's alternative isn't n8n -- it's "spend two weeks writing a Kafka Streams microservice." That's the comparison that makes them convert.

**Shift 6: terraform-like plan/apply workflow is the wedge**
- Old: Mentioned as a bullet point
- New: Shown in the hero flow, the "how it works" steps, and the production section
- Why: Nobody else in the Kafka ecosystem has this. It's genuinely unique. Tim, Pablo, and Mohammed all wanted CI/CD and safe deployments.

**Shift 7: CDC is one source type, not the whole story**
- Old: "TypeStream watches your Postgres or MySQL" -- CDC is the premise
- New: "TypeStream works with any Kafka topic" -- CDC is one option among many
- Why: Our target audience already has topics flowing. Some are CDC, some are application events, some are external feeds. Leading with CDC narrows the appeal.

---

## Target Personas

### Primary: "Kafka-Rich, Pipeline-Poor" Engineer
- Works at a company that runs Kafka (and probably Schema Registry)
- Topics are flowing -- events, CDC, external feeds
- Wants to do stream processing but building a Kafka Streams app is a multi-week project
- Might not even know what Kafka Streams is -- just knows "I have data in topics and I want to do more with it"
- Cares about: config-as-code, deployment safety, schema handling, not having to write Java
- Example: Pantelis at HelloFresh. Has Kafka + Schema Registry. Spent two days wiring a pipeline manually. Would rather write a config file.
- **What they need to see on the site:** "This works with my existing Kafka. It's a config file, not a Java project. I can try it today."

### Secondary: "Streaming Architect" (Evaluator/Champion)
- Senior engineer or architect who understands the Kafka ecosystem deeply
- Knows what Kafka Streams is, knows it's painful to operationalize
- Evaluating tools for their team, will champion internally
- Probing boundaries: schema evolution, exactly-once semantics, state management
- Example: Mohammed at Clio. Immediately identified where it works and where it doesn't.
- **What they need to see on the site:** "This is real Kafka Streams under the hood, not a toy. It handles production concerns. The config compiles to real topologies."

### Secondary: "No Kafka Yet" Builder
- Has the problems Kafka solves but hasn't adopted it
- Running cron jobs, sync scripts, background workers
- TypeStream's bundled mode gives them the whole stack without the ops burden
- Example: Teams that would benefit from streaming but find the Kafka learning curve too steep
- **What they need to see on the site:** "I don't need to learn Kafka. TypeStream bundles everything."

### Explicitly NOT targeting (on this page):
- **Teams on Fivetran/Snowflake** -- They solved data movement. "Stop writing sync jobs" doesn't land.
- **Workflow automation seekers** -- n8n/Zapier users. We address the confusion but don't court them.

---

## Page Structure

### 1. HERO

**Reasoning:** The hero has ~5 seconds to answer "what is this and should I care?" Our buyer has Kafka. They need to see immediately that this is for them and that it solves the "building stream processing is too hard" problem.

**Headline:**
> You already run Kafka.
> Start building on it.

**Subheadline:**
> TypeStream compiles pipeline configs into Kafka Streams topologies. Filter, join, enrich, and route your topics -- defined in a config file, previewed with `typestream plan`, deployed in seconds. No Java. No new microservices. Runs on the Kafka you already have.

**CTA:**
- Primary: "See it in action" -> demo link
- Secondary: "Talk to us" -> mailto
- Tertiary: "or view on GitHub ->"

**Visual:** Simple animated diagram:
```
[Your Kafka Topics] --> [TypeStream] --> [Enriched Topics]
                                     --> [Elasticsearch]
                                     --> [ClickHouse]
```

No database in the diagram. The starting point is Kafka topics, because that's what our buyer already has. Destinations include both new Kafka topics (enriched/filtered) and external sinks.

**Below the hero (trust signal):**
> Works with your existing Kafka cluster and Schema Registry. Or bundles everything for teams starting fresh.

This one line handles both personas: Kafka teams see "works with what I have" and non-Kafka teams see "I can start from scratch too."

---

### 2. "THE PROBLEM" -- Why Your Kafka Is Underused

**Reasoning:** Before showing the solution, name the problem the buyer already feels. Most Kafka teams know they're underusing it but can't articulate why. This section puts words to their frustration.

**Headline:**
> Your Kafka topics are full of data. Getting value out of them shouldn't require a Java project.

**The five pain points** (short, punchy, one sentence each):

1. **Every pipeline is a microservice** -- A new repo, a new deployment, a new thing to monitor. The overhead kills small use cases before they start.
2. **Stream processing means writing Java** -- Kafka Streams is powerful, but most teams don't have JVM expertise on every squad.
3. **Schema management is manual** -- You have Schema Registry but every consumer has to wire up the right serde for the right topic.
4. **No preview before deploy** -- You deploy your topology and hope. There's no `terraform plan` for Kafka Streams.
5. **Sink configuration is its own specialization** -- Kafka Connect can reach 200+ destinations. Configuring it correctly is a different skill entirely.

**Closing line:**
> TypeStream addresses all five. Config replaces code. One engine replaces N microservices. Schema Registry integration is automatic. And `typestream plan` shows you what will change before anything runs.

**Reasoning for including this section:** This is the "I feel seen" moment. If the visitor has Kafka and recognizes these five problems, they're hooked. If they don't recognize them, they self-select out -- which is what we want. We'd rather have 100 qualified visitors than 1,000 confused ones.

---

### 3. "HOW IT WORKS" -- Three Steps

**Reasoning:** After naming the problem, show the solution as three concrete steps. Lead with what's unique (define a pipeline, preview it) not what's generic (connect to things).

**Step 1: Define your pipeline**
> Write a config file that says which topics to read, how to transform the data, and where to send it. Or prototype with CLI pipes that read like Unix.

Show a CLI one-liner (punchy, immediate):
```
cat /dev/kafka/local/topics/page_views | filter .country == "US" | > /dev/kafka/local/topics/us_views
```

Then: "Or as a config file -- version-controlled, CI/CD friendly, reviewable in a PR."

**Step 2: Preview with `typestream plan`**
> See exactly what will change before anything runs. Like terraform for data pipelines.

Show terminal output:
```
$ typestream plan us-traffic.json

  us-traffic:
    + CREATE pipeline "us-traffic"
      source: /dev/kafka/local/topics/page_views
      steps:  filter(.country == "US") -> us_views

  1 pipeline to create, 0 to update, 0 to delete.
  Run `typestream apply` to execute.
```

**Step 3: Apply and it runs**
> TypeStream compiles your config into a Kafka Streams topology, reads schemas from your Schema Registry, and starts processing. Change the config, plan again, apply again.

**Reasoning for step order:** Leading with "define your pipeline" immediately shows the buyer what they'd actually do, not infrastructure setup. The plan step is our biggest differentiator and comes second to build confidence. "Apply and it runs" closes the loop.

---

### 4. "DEFINE PIPELINES YOUR WAY" -- Three Interfaces

**Reasoning:** Show all three entry points, ordered by what our buyer cares about.

**Tab 1: Config file** (highlighted as primary)
> Version-controlled. CI/CD friendly. Review in a PR.

Show the real `.typestream.json` format. Since our buyer has Kafka, the `/dev/kafka/local/topics/` paths make sense to them -- they know their topics.

**Tab 2: CLI**
> Pipe commands together. Great for prototyping and exploration.

Show the CLI one-liner with Unix pipe syntax.

**Tab 3: Visual builder**
> Drag, drop, connect. Good for exploring what's possible.

Screenshot or video of the graph builder.

**Key line:**
> All three produce the same pipeline. Config files for production, CLI for exploration, visual builder for discovery.

---

### 5. PATTERNS -- What You Can Build

**Reasoning:** Problem-oriented recipe cards. But now framed for Kafka teams, not generic "every developer" problems.

**Section header:**
> What teams build on their Kafka

**Row 1 -- Stream Processing Basics:**

| Filter and route | Join two topics | Real-time aggregations |
|---|---|---|
| Route events from one topic to many based on content. Region splits, priority routing, conditional fanout. Today this is a custom consumer or a Kafka Streams app. | Combine data from two topics by key. Enrich orders with user data, match events with metadata. One of the hardest things in Kafka Streams -- joins, windowed joins, serde alignment. | Counts, windowed counts, grouped metrics from your event stream. No state store boilerplate, no KTable management. Real-time analytics from config, not code. |
| TypeStream: one config file. | TypeStream: one config file. | TypeStream: one config file. |

**Row 2 -- Enrichment & AI:**

| Enrich with AI in-flight | Add semantic search | Geo-enrich your events |
|---|---|---|
| Run every event through an LLM as it flows. Classify support tickets, score sentiment, extract entities, summarize content. The enriched result lands in a new topic or a downstream sink. | Generate embeddings from your topic data and push to Weaviate or any vector store. As your data changes, embeddings update automatically. Semantic search without building an embedding pipeline. | Convert IP addresses to country, city, region. Every event arrives geo-tagged. Feed it into aggregations for real-time analytics by geography. |
| TypeStream: add an OpenAI node to your pipeline config. | TypeStream: add an embedding node. | TypeStream: add a GeoIP node. |

**Row 3 -- Kafka Connect Made Easy:**

| Sink to Elasticsearch | Replicate to your warehouse | Route CDC to anywhere |
|---|---|---|
| Keep a search index in sync with your topic data. Extract text, filter what gets indexed, handle backpressure when ES is slow. | Stream topic data to ClickHouse, Snowflake, BigQuery, S3. Every event arrives in seconds, not the next batch run. | If you have Debezium CDC topics, filter, transform, and route them to any of 200+ Kafka Connect destinations. CDC topics are just Kafka topics -- TypeStream treats them the same. |
| TypeStream: config file with an Elasticsearch sink. | TypeStream: config file with a warehouse sink. | TypeStream: config file with transform + sink. |

**Why these recipes:** Every card assumes the buyer has Kafka topics. Row 1 covers the stream processing gap (the main pain point). Row 2 covers the AI/enrichment angle (timely, differentiating). Row 3 covers Kafka Connect (they know it exists but find it painful to configure). No recipe requires the buyer to set up CDC or a database -- it's all Kafka-native.

**Design note:** Keep the pattern from the previous iteration -- technical, monospace-feeling, no flashy icons. Call them "patterns" not "templates." Link to config examples.

---

### 6. "THE COMPARISON THAT MATTERS"

**Reasoning:** Our buyer's real alternative isn't n8n or Fivetran. It's "spend weeks writing a Kafka Streams microservice." That's the comparison that drives conversion.

**Headline:**
> What used to take a microservice now takes a config file.

**Primary comparison table:**

| | Writing Kafka Streams by hand | TypeStream |
|---|---|---|
| Define a pipeline | Java/Kotlin project, weeks of work | Config file, minutes |
| Add a new use case | Another microservice to build, deploy, monitor | Another config file |
| Schema handling | Manual serde management per topic | Auto-reads Schema Registry, propagates through pipeline |
| Preview changes | Deploy and hope | `typestream plan` shows the diff |
| Sink to external systems | Kafka Connect config is its own specialization | Declarative in the pipeline config |
| Deploy changes safely | Build, test, deploy a JAR | `typestream plan` then `typestream apply` |

**Secondary comparison (smaller, below):**

> Not a workflow tool. If you need "when a Slack message arrives, create a Jira ticket" -- use n8n. TypeStream is streaming data infrastructure that runs on your Kafka cluster.

Keep the n8n disambiguation but make it a short callout, not a full section. The n8n confusion came from the visual builder being front-and-center; with config-first positioning, it's less likely to recur. But still worth a one-liner.

---

### 7. "SCHEMA-AWARE PIPELINES"

**Reasoning:** This is a genuine differentiator that Stuart and Pantelis both flagged. For Kafka teams, the difference between "manual serde management" and "automatic schema propagation" is significant. It deserves its own callout.

**Headline:**
> Your Schema Registry does more than you think.

**Copy:**
> TypeStream reads your Schema Registry and propagates types through every node in your pipeline. Source schemas are resolved automatically. Each transform declares how it changes the schema -- filter passes through, join merges, enrichment adds a field. Schema errors are caught at compile time, before any topology runs.
>
> When an upstream schema evolves, your pipeline knows. No runtime surprises. No silent data corruption.

**Reasoning for giving schema its own section:** Every other stream processing tool (including hand-written Kafka Streams) requires you to manage schemas manually. TypeStream doing this automatically is a "wait, really?" moment for anyone who's wrestled with Avro serde configuration.

---

### 8. "BUILT FOR PRODUCTION"

Three-column layout (same as before, these are still right):

**Plan before you deploy**
> Every change goes through `typestream plan` first. See exactly what pipelines will be created, updated, or deleted -- before anything runs. Like terraform for data pipelines. Review in CI, approve in a PR.

**Backpressure and delivery guarantees**
> If Elasticsearch is slow or Redis is down, TypeStream queues and retries automatically. No data loss, no duplicate processing. Built on Kafka Streams exactly-once semantics.

**Observability included**
> Metrics, logs, and traces out of the box. Know how many events per second each pipeline processes, where bottlenecks are, and when something fails.

---

### 9. "RUNS ALONGSIDE YOUR KAFKA"

**Reasoning:** Reframe from "runs in your infrastructure" to "runs alongside your existing Kafka." The self-hosted story matters, but for Kafka teams the key message is: this doesn't replace anything, it adds to what you have.

**Headline:**
> Connects to your Kafka cluster. Doesn't replace it.

**Two columns:**

**Already have Kafka?**
> Point TypeStream at your existing cluster and Schema Registry. It reads your topics, reads your schemas, and runs pipelines on the infrastructure you already operate. Nothing to migrate.

**Starting fresh?**
> TypeStream bundles Kafka, Schema Registry, and Kafka Connect in a single `docker compose up`. Get the full streaming stack without learning to operate it.

**Source available (BSL)**
> Read every line of code. Business Source License means no vendor lock-in. If TypeStream disappears tomorrow, you still have the code.

---

### 10. BOTTOM CTA

**Headline:**
> Your topics are already flowing. Start building on them.

**CTAs:**
- "See it in action" -> demo
- "Talk to us" -> mailto
- GitHub link

---

## What Changes from Previous Rebuild

1. **Hero: "You already run Kafka" replaces "A row changes in your database"** -- targets our actual buyer, not a generic developer
2. **New "The Problem" section** -- names the five reasons teams underuse Kafka. This is the "I feel seen" moment.
3. **Starting point is Kafka topics, not databases** -- the hero diagram, the examples, the language all assume topics exist
4. **Primary comparison is vs. writing Kafka Streams by hand** -- not vs. n8n (which becomes a one-liner disclaimer)
5. **Schema awareness gets its own section** -- it's a genuine differentiator that was completely absent
6. **CDC becomes one source type, not the premise** -- "Route CDC to anywhere" is one recipe card, not the whole pitch
7. **Recipes reframed for Kafka teams** -- "Filter and route" instead of "Keep search results fresh." The buyer thinks in topics, not in application problems.
8. **"Runs alongside your Kafka" framing** -- not "runs in your infrastructure." The message is additive, not standalone.

## What's Kept from Previous Rebuild

1. **Config-as-code first, visual builder third** -- still correct
2. **`typestream plan` prominently featured** -- still our strongest differentiator
3. **Three interfaces (config, CLI, visual builder)** -- still valuable
4. **Production qualities section** -- still necessary for the evaluator persona
5. **Overall design system** -- Tailwind, typography, color scheme
6. **Integration logos** -- sources, transforms, destinations

## What's Removed

1. **PII detection recipe** -- not built, not relevant to Kafka-first positioning
2. **"Generate REST APIs" recipe** -- not fully built, most contested in feedback
3. **"Every developer" framing** -- we're not targeting every developer, we're targeting Kafka teams
4. **Full n8n comparison table** -- replaced by one-liner. Config-first positioning reduces the confusion that caused this.
5. **Database-first hero diagram** -- replaced by Kafka-first diagram
6. **"No Kafka to operate" messaging** -- our buyer already operates Kafka. This line told them "not for me."

---

## Open Questions

### Config format on the website

The real `.typestream.json` uses `/dev/kafka/local/topics/...` paths. For a Kafka audience, this is fine -- they know what topics are. But the paths are our internal virtual filesystem abstraction. Options:
- **Show real paths** -- honest, and Kafka teams understand topic references
- **Show simplified paths** -- `"topic": "page_views"` is cleaner but technically inaccurate
- **Lead with CLI, expand to config** -- the CLI one-liner hooks, the JSON earns trust

**Recommendation:** Lead with CLI (punchy), show full config in the "Define Pipelines" tab and recipe detail pages. The `/dev/kafka/local/topics/` paths will make Kafka engineers nod ("they have a filesystem abstraction, cool") rather than be confused.

### Do we mention Kafka Streams by name?

The user insight was "assume most Kafka people don't know what Kafka Streams are." Two options:
- **Mention it** as "the stream processing engine built into Kafka" -- educates the buyer and positions TypeStream as the easy way to use it
- **Don't mention it** -- just say "stream processing" and let TypeStream be the interface

**Recommendation:** Mention it sparingly with a parenthetical explanation. The hero says "compiles pipeline configs into Kafka Streams topologies" -- this earns credibility with the Streaming Architect persona. The problem section says "Stream processing means writing Java" without assuming the reader knows what Kafka Streams is.

### Navigation changes?

Current nav: Home, Integrations, About Us. Consider adding:
- **Docs** -- link to documentation site
- **Patterns** -- dedicated page expanding the recipe cards with full config examples

### Hero video/animation?

Options:
- **(a) Animated terminal** showing `typestream plan` -> `typestream apply`
- **(b) Simple SVG/CSS diagram** of topics -> TypeStream -> outputs
- **(c) Both** -- diagram above the fold, terminal animation in the "how it works" section

**Recommendation:** (c). The diagram gives instant visual understanding. The terminal animation in step 2/3 of "how it works" shows the actual workflow.
