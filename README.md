# Pet Hunter

A RuneLite plugin that tracks your progress toward every pet in Old School RuneScape, estimates
how dry you are on the ones you're missing, and helps you decide what to hunt next.

## Why

There are 71 obtainable pets. Existing tools cover pieces of this:

- The in-game collection log tells you **which** pets you have, not how close you are.
- Collection-log luck plugins can calculate luck for items where a kill count exists, which
  leaves every skilling pet largely uncovered, because a skilling pet's rate depends on the
  specific activity you performed, not just the skill.
- Web calculators can do the probability math, but you have to hand-feed them a level, a
  method, and an action count you don't actually know.

Pet Hunter reads what your client already knows — your levels, your XP, your kill counts, your
collection log — and does the bookkeeping for you.

## Features (V1)

- **Sidebar panel** listing all pets, grouped by source type, filterable by obtained / missing.
- **Dryness estimate per pet**, with the probability that a player with your stats would still
  be without it.
- **Confidence tiers** on every figure, so you always know whether a number is derived from a
  real kill count or from an assumption about how you trained.
- **Method comparison** for pets you're missing: expected rolls per hour, per-roll rate at your
  level, and expected hours to the pet for each viable method.
- **Level-banded estimation** for skilling pets. Because a skilling pet's rate improves as your
  level rises, historical XP is split into level bands and each band is evaluated at the rate
  that actually applied.

## Not in V1

- No account sync, no website, no shareable collection page.
- No outbound network requests at all.
- No prospective per-action roll counting. V1 estimates from totals; exact live roll attribution
  is a V2 feature (see `docs/ROADMAP.md`).

## How the math works

Boss and activity pets are a flat chance per kill or per reward roll. After `n` rolls at rate
`p`, the chance you'd still be without the pet is `(1 - p)^n`.

Most skilling pets use the wiki formula `1 / (B - Level x 25)`, where `B` is a base chance that
varies **by activity**, not by skill. The rate stops improving after level 99, with one
exception: 200 million XP in the matching skill makes you 15 times more likely, applied by
dividing the final denominator.

Several pets follow neither pattern and are modelled individually, including static rates that
ignore the formula, rates that scale with minigame contribution, rates that only roll on a
specific chest or interaction, and pets rolled off a unique drop rather than off a completion.

## Accuracy and limitations

RuneLite's Plugin Hub review process explicitly does not verify that information displayed by a
plugin is factually accurate. Treat every figure here as an estimate. Where the plugin cannot
determine a real attempt count, it says so rather than guessing.

Estimates degrade in these situations, and the UI flags each one:

- You trained a skill with a method other than the one assumed.
- Your kill count for a boss isn't tracked anywhere the client can read.
- You obtained XP from sources that don't roll for the pet at all.
- You have not opened your collection log since installing, so no ownership data exists yet.

## Setup

1. Install from the Plugin Hub (or build locally with `./gradlew shadowJar`).
2. Open the Pet Hunter icon in the RuneLite sidebar.
3. Open your in-game collection log once and click through the Pets page so ownership and kill
   counts can be read.

## Data sources

Drop rates and base chances are sourced from the Old School RuneScape Wiki and from Jagex's
published figures. Every rate in the dataset carries its source. Wiki content is licensed
CC BY-NC-SA 3.0; see `docs/DATA.md` for how that constrains redistribution and any future
website.

## Contributing

The dataset is the bottleneck, not the code. If a rate is missing or wrong, a PR editing
`src/main/resources/com/pethunter/data/pets.json` with a source URL is the single most useful contribution.

## License

BSD-2-Clause, matching the Plugin Hub convention.
