# Roadmap

Each phase ends with something that builds, runs, and is worth looking at. Do not start a phase
before the previous one's exit criteria are met.

## P0 — Skeleton and math (no game state)

Scaffold from the RuneLite example plugin. Build the dataset model, loader, schema validation,
and the entire math layer with unit tests, using fixture data only. No `Client` reads yet.

Exit: `./gradlew build` green, math tests passing including the hand-computed level-band case,
schema validation wired into the build and failing on a deliberately bad fixture.

## P1 — Panel with the full pet list

Sidebar panel rendering all 71 pets from the skeleton dataset. Filters, grouping, sorting,
search, expand/collapse. Everything shows `UNKNOWN`, which is correct at this stage.

Exit: panel renders with a logged-out client and with an empty dataset without throwing.

## P2 — Ownership and kill counts

Collection log scrape, obtainment chat detection, KC capture, per-account persistence. Pets flip
to obtained; boss pets start showing `EXACT` dryness.

Exit: obtaining a pet in-game (or a simulated chat message in tests) marks it obtained and it
survives a relog. Ownership never regresses on a failed scrape.

## P3 — XP tracking and skilling estimates

Skill XP and unboosted levels, method selection with a sensible default per pet, level-banded
estimation, assumption strings surfaced in the UI.

Exit: changing the assumed method visibly changes the estimate, and the assumption text always
matches the method actually used in the calculation.

## P4 — Method comparison (cut from V1, 2026-09-17)

Dropped by the plugin's author before 1.0. Ranking methods by expected hours needs actions-per-hour
figures that vary hugely per player, so a ranking would look authoritative while being wrong for
most people. The panel keeps what the comparison was for: every activity that drops a pet, with its
rate, and a choice of which one you trained.

If it returns, it needs verified actions-per-hour data and an honest statement of how much that
varies.

## P5 — Dataset fill (partly done)

Populate per `docs/DATA.md` population strategy. Code should need no changes during this phase.
If it does, the model is wrong and the model gets fixed, not worked around.

Exit: all boss and raid pets verified; every skilling pet has at least one verified method;
special cases modelled explicitly.

Status at 1.0: every boss and raid rate verified except Scorpia's offspring (verified, no counter
split), Lil' Zik and Tumeken's guardian (rate depends on raid performance). Six skilling pets have
per-activity rates and methods. Tangleroot, Rift guardian, Soup and Mr McGroot have no estimate,
each for a stated reason.

## P6 — Hub submission

Icon, `runelite-plugin.properties`, README accuracy language, config defaults, performance pass,
manual test against a fresh profile. Submit to the plugin hub.

Exit: merged.

---

## V2 (separate design doc required before starting)

- **Prospective roll counting.** Attribute rolls to the specific activity as they happen, from
  XP drops, animations, varbits and chat, giving `EXACT` confidence on skilling pets for the
  first time. This is the genuinely novel capability and the reason to build the plugin at all,
  but it needs the dataset and the estimate path working first.
- **Sync and website.** Opt-in behind a config toggle, plugin-identifying user agent, and an
  IP warning on any external-site button, matching the precedent set by existing log-syncing
  plugins. Resolve the dataset licensing question in `docs/DATA.md` before building a site.
- **Session tracker.** Rolls this session, time on task, live probability updating.
- **Notifications.** Milestone alerts at 1x, 2x, 3x the drop rate.
