# CLAUDE.md

Working agreement for Claude Code on this repository. Read this before writing any code.

## What this is

**Pet Hunter** is a RuneLite Plugin Hub plugin. It adds a sidebar panel that shows a player's
progress toward every obtainable pet in Old School RuneScape, estimates how "dry" they are on
each one, and lets them compare hunting methods for pets they don't have yet.

V1 is **local only**. No backend, no website, no outbound network requests of any kind.

## Hard rules

These are not preferences. Violating any of them breaks the project.

1. **Never invent a drop rate, base chance, or XP-per-action value.** Every numeric rate in
   `src/main/resources/pets.json` must carry a `sources` array with at least one URL and a
   `verified` boolean. If a value is not verified, it stays `null` and the UI renders it as
   `UNKNOWN`. A plausible-looking wrong number is worse than a blank, because users will grind
   thousands of hours against it.
2. **No network calls in V1.** No HTTP client, no `OkHttp` usage, no telemetry, no analytics,
   no external links that open a browser. This keeps the Plugin Hub review surface near zero.
   Sync and the collection website are V2 and live behind a separate design doc.
3. **No automation of gameplay.** No synthetic clicks, no menu invocation, no pathing, no
   input injection. Read game state and draw UI. Nothing else. This is a Jagex third-party
   client rule, not a style choice.
4. **No reflection, no native code, no unusual dependencies.** Plugin Hub review restricts
   these. Stick to what `runelite-client` already brings in transitively. If you think a new
   dependency is needed, stop and ask.
5. **Never present an estimate as a fact.** Every number rendered in the panel must be tagged
   with its confidence tier and, on hover, the assumption behind it.
6. **Per-account state.** All persisted state is keyed by account hash. Never let one
   character's progress leak into another's view.

## Stack

- Java 11 (Plugin Hub target), Gradle
- Based on the `runelite/example-plugin` template
- Swing for the panel (`PluginPanel` + `NavigationButton`)
- Gson for dataset loading (already a RuneLite transitive dep)
- JUnit 4 + Mockito for tests (as used by other hub plugins)

## Layout

```
src/main/java/com/pethunter/
  PetHunterPlugin.java          # lifecycle, event subscriptions, wiring
  PetHunterConfig.java          # @ConfigGroup settings
  data/
    PetRepository.java          # loads + indexes pets.json
    Pet.java  PetSource.java  HuntMethod.java   # immutable records/POJOs
  state/
    AccountState.java           # per-account-hash persisted progress
    OwnershipTracker.java       # collection log scrape + chat detection
    KillCountTracker.java       # KC from log pages + chat parsing
    SkillXpTracker.java         # XP snapshots per skill
  math/
    DrynessCalculator.java      # binomial dryness, percentile
    LevelBandIntegrator.java    # skilling-pet rolls across level bands
    XpTable.java                # level <-> XP lookups
    Confidence.java             # EXACT | ESTIMATED | UNKNOWN
  ui/
    PetHunterPanel.java         # root panel, filters, grouping
    PetRow.java                 # collapsed row
    PetDetailPanel.java         # expanded: sources, methods, assumptions
src/main/resources/
  pets.json                     # the dataset (see docs/DATA.md)
  pets.schema.json              # validated in CI
src/test/java/...               # math is the priority for test coverage
```

## Conventions

- Dataset is **data, not code**. Adding a pet or a method must never require a Java change.
  If it does, the model is wrong — fix the model.
- The math layer is pure: no `Client` dependency, no Swing, fully unit-testable. All game
  state enters through plain value objects. This is where the test coverage goes.
- Widget IDs, varbits, script IDs and chat message strings go in one constants file, each with
  a comment naming what it was verified against. Assume every ID in this repo's docs is
  **unverified** until checked against the current `runelite-api`.
- Prefer `@Subscribe` on narrow events. Do nothing on `GameTick` that could run on a less
  frequent event.
- No work on the client thread that touches Swing, and no Swing thread work that touches the
  client. Use `clientThread.invoke` / `SwingUtilities.invokeLater` deliberately.

## Commands

```bash
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew shadowJar      # local jar
./gradlew run            # launch RuneLite with the plugin loaded
```

## Definition of done for any task

- Compiles, `./gradlew build` green
- Math changes covered by unit tests, including the degenerate cases (zero rolls, rate of
  null, level 1, level 99, 200m XP)
- No new dependency, no new network call, no new reflection
- Any rate touched has a source URL and a `verified` flag
- Panel still renders with an empty dataset and with a logged-out client

## Things to ask about rather than decide

- Adding a dependency
- Anything that sends data off the machine
- Changing the dataset schema
- Any feature that reads or writes game state beyond what's listed in `docs/DESIGN.md`
