# Kickoff prompt for Claude Code

Paste everything below the line into Claude Code in VS Code, with this repo open.

Before you do: the files in this repo are docs and data only. Claude Code's first job is to
generate the actual Gradle project around them from the RuneLite plugin template.

---

We're building **Pet Hunter**, a RuneLite Plugin Hub plugin. Read `CLAUDE.md`, then
`docs/DESIGN.md`, `docs/DATA.md`, and `docs/ROADMAP.md` before writing anything. Those documents
are the specification; if something in my instructions contradicts them, stop and flag it rather
than picking one.

## Non-negotiables, restated so they don't get lost

1. **Never invent a drop rate, base chance, or XP-per-action figure.** Unpopulated rates stay
   `null` with `verified: false`, and the UI renders them as `UNKNOWN`. I would much rather ship
   a panel full of blanks than one plausible wrong number.
2. **Zero network calls in V1.** No HTTP, no telemetry, no external links.
3. **No gameplay automation.** Read state, draw UI, nothing else.
4. **No new dependencies, no reflection, no native code.** Ask me first if you think you need
   one.
5. **Every rendered number carries its confidence tier.** A figure that loses its tier in the
   pipeline is a bug.

## What I want in this first session — P0 only

Do not start P1. Exit criteria for P0 are in `docs/ROADMAP.md`.

1. **Scaffold the project.** Set up a Gradle build matching the current RuneLite
   `example-plugin` template: Java 11 target, `runelite-client` dependency, `shadowJar`,
   a `run` task that launches RuneLite with the plugin loaded, `runelite-plugin.properties`,
   `.gitignore`, and a BSD-2-Clause LICENSE for the code. Put the dataset in its own directory
   with a separate CC BY-NC-SA notice, for the reason given in `docs/DATA.md`.

2. **Dataset model and loader.** Implement `Pet`, `PetSource`, `HuntMethod` as immutable types
   and `PetRepository` to load and index the JSON via Gson. Move `data/pets.seed.json` to
   `src/main/resources/pets.json` and `data/pets.schema.json` alongside it. The loader must
   survive null rates, missing methods, and an unrecognised `rateModel` without throwing — log
   and skip.

3. **Schema validation in the build.** A Gradle task that validates `pets.json` against the
   schema plus the six extra assertions listed in `docs/DATA.md`, wired so `./gradlew build`
   fails on violation. Prove it works by temporarily adding a bad fixture, showing the failure,
   then reverting.

4. **The math layer, fully tested.** This is the heart of the plugin and it must be pure: no
   `Client`, no Swing, no static game state. Implement:
   - `XpTable` — level to XP and XP to level for the standard curve
   - `DrynessCalculator` — binomial dryness for flat rates, computed in log space
   - `LevelBandIntegrator` — the band-by-band algorithm in `docs/DESIGN.md` section 4.2,
     including the level-99 clamp, the `minLevel` exclusion, and the 200m-XP multiplier applied
     to the final denominator rather than to the base chance
   - `Confidence` — the three-tier enum, plus a small result type that pairs every computed
     figure with its tier and a human-readable assumption string
   - Multi-source combination per section 4.3, where any `UNKNOWN` source turns the overall
     figure into an explicitly-labelled lower bound

   Cover every case in `docs/DESIGN.md` section 8. Include the hand-computed two-band example
   as a test with the arithmetic shown in a comment so I can check it myself. The
   `STATIC_IGNORES_FORMULA` entry in the seed data is a good regression test: changing the
   player's level must not move that number.

## How I want you to work

- Work in small commits, one logical change each, with real commit messages.
- After scaffolding, show me the `build.gradle` and `runelite-plugin.properties` before moving
  on, since those are what the hub reviews.
- When a design document is ambiguous, say so and propose a resolution rather than guessing
  silently. Ambiguity in the dataset model especially: it's cheaper to fix now than after 300
  rows exist.
- Don't populate any real game data this session beyond what's already in the seed file. P5 is
  for that, and doing it early with unverified numbers is the main way this project fails.
- Tell me when P0's exit criteria are met, and stop.

Start by reading the docs and telling me your plan, including anything in the design you think
is wrong or underspecified. I'd rather argue about it now.
