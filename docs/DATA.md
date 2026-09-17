# Dataset: sourcing, schema, and licensing

The dataset is the critical path of this project. 71 pets is roughly 250–350 rate rows once
every per-activity base chance is accounted for. Budget accordingly.

## Golden rule

**No rate enters this repository without a source URL.**

Every rate-bearing object carries:

```json
"baseChance": 211886,
"verified": true,
"sources": ["https://oldschool.runescape.wiki/w/Rock_golem"]
```

If you cannot find the figure, set the value to `null`, `verified` to `false`, and leave the
`sources` array empty. The UI renders that as `UNKNOWN`. That is the correct outcome. A wrong
number sends someone on a thousand-hour grind against bad math, which is a far worse failure
than a blank cell.

Do not copy rates from third-party calculator sites. They are frequently derived, stale, or
wrong. Use the wiki article for the pet, or Jagex's own published figures where the wiki cites
them.

## What makes this dataset unusual

For skilling pets, the base chance is **per activity**, not per skill. The mining pet has a
distinct base chance for every rock type; the woodcutting pet for every tree; the fishing pet
for every fish. The wiki publishes these as tables on each pet's article, generally framed as
"subtract your level times 25 from these numbers for chance per resource."

Several pets break the general formula entirely and must be modelled as their own cases:

- A fishing method with a static rate that the level formula does not modify at all
- A minigame where the rate scales with the player's contribution across a range
- A thieving minigame where only one specific chest type rolls the pet
- A farming pet that rolls on check-health or final harvest depending on which is available
- A hunter pet that only rolls when catching one specific creature family
- A raid pet rolled off the unique drop rather than off raid completion
- Reward-roll pets from minigame supply crates and hunter loot sacks

Model each of these explicitly. Do not force them into `SKILL_LEVEL_SCALED` with a fudged base.

## Schema

See `src/main/resources/com/pethunter/data/pets.schema.json` for the machine-readable version. Shape:

```json
{
  "schemaVersion": 1,
  "gameUpdateChecked": "2026-09-16",
  "pets": [
    {
      "id": "rock_golem",
      "name": "Rock golem",
      "itemId": null,
      "category": "SKILLING",
      "skill": "MINING",
      "collectionLogPage": "Pets",
      "obtainableOn": ["MAIN", "IRONMAN", "HARDCORE", "ULTIMATE"],
      "sources": [
        {
          "id": "rock_golem.gem_rocks_shilo",
          "label": "Gem rocks (Shilo Village, underground)",
          "rateModel": "SKILL_LEVEL_SCALED",
          "baseChance": null,
          "flatRate": null,
          "minLevel": 40,
          "counterKey": null,
          "verified": false,
          "sources": [],
          "notes": "Commonly cited as the fastest method for this pet."
        }
      ],
      "methods": [
        {
          "id": "rock_golem.gem_rocks_shilo",
          "actionsPerHour": null,
          "xpPerAction": null,
          "requirements": ["40 Mining", "Karamja Hard Diary"],
          "xpEfficient": false,
          "verified": false,
          "sources": []
        }
      ]
    }
  ]
}
```

Field notes:

- `id` — stable snake_case key. Never change one; persisted state references it.
- `category` — `SKILLING | BOSS | MINIGAME | CLUE | RAID | SLAYER | QUEST | OTHER`. Drives panel
  grouping only.
- `source.id` and `method.id` share a namespace so a method can be matched to the rate row it
  applies to.
- `counterKey` — the key under which an attempt count is stored (typically the collection log
  page whose KC line applies). `null` means no automatic counter exists for this source, which
  forces `UNKNOWN` or a manual entry.
- `countWarning` — set when the counter can include attempts that never rolled the pet: MVP-only
  rolls, contribution thresholds, group drops, or a rule change that altered what the counter
  means. The panel marks the figure with `!` and shows the warning. If the mismatch is large
  enough to make the count misleading (Scurrius: only the top damage dealer rolls), leave
  `counterKey` null and keep the warning, which then explains why there is no count.
- `flatRate` — a denominator for flat models (`1/flatRate`). Mutually exclusive with
  `baseChance`.
- `contributionRange` — `[denominator at minimum contribution, denominator at maximum
  contribution]` for `CONTRIBUTION_SCALED`. With no contribution data, estimates use the rarer
  of the two rates (the larger denominator), so they never overstate dryness, and the
  assumption text quotes the other end of the range.
- `minLevel` — XP earned before this level produced no rolls for this source and must be
  excluded from band integration.
- `xpPerAction` — required to convert XP into an action count. Without it, a
  `SKILL_LEVEL_SCALED` source cannot be estimated at all, so this is as important as the rate.
- `obtainableOn` — suppresses pets a given account type cannot get.

## Validation

The `validateDataset` Gradle task (part of `check`, so part of `build`) validates `pets.json`
against `pets.schema.json` and additionally asserts:

1. Pet ids are unique. Source ids are unique across the dataset, as are method ids. Every
   source and method id is prefixed with its own pet's id (`<petId>.`). A method id equalling a
   source id is required by rule 5, so ids are *not* unique across kinds.
2. No object has `verified: true` with an empty `sources` array, except `ONE_OFF` sources,
   which carry no rate.
3. No object has a non-null rate with `verified: false`. (Populate the rate and the source
   together, or neither.) "Rate" means `baseChance`, `flatRate` and `contributionRange` on
   sources, and `xpPerAction` and `actionsPerHour` on methods.
4. Each source uses only the rate field its `rateModel` defines, and all other rate fields are
   null. A `verified: true` source must populate that field. `ONE_OFF` sources have every rate
   field null.

   | rateModel | Rate field |
   |---|---|
   | `FLAT_PER_KILL`, `FLAT_PER_ROLL`, `STATIC_IGNORES_FORMULA`, `UNIQUE_CONDITIONAL` | `flatRate` (for `UNIQUE_CONDITIONAL`, 1/N per unique) |
   | `SKILL_LEVEL_SCALED` | `baseChance` |
   | `CONTRIBUTION_SCALED` | `contributionRange` |
   | `ONE_OFF` | none |
5. Every `method.id` matches an existing `source.id` on the same pet.
6. A pet with any `SKILL_LEVEL_SCALED` or `STATIC_IGNORES_FORMULA` source declares `skill`, and
   `skill` names a RuneLite `Skill` enum constant (e.g. `MINING`).
7. `baseChance` is greater than 2475 (99 × 25), so the denominator stays positive at every
   level. Every `contributionRange` value is at least 1.

The validator reads JSON strictly and rejects duplicate keys, and it fails on any JSON Schema
keyword it does not implement, so a schema edit can never silently skip a check.

Failing validation fails the build. This is the mechanism that enforces the golden rule, so do
not weaken it to get a commit through.

## Population strategy

Do not try to fill all 71 in one pass. Work in this order, committing after each:

1. **Skeleton** — all 71 pets with ids, names, categories, and `obtainableOn`, every rate
   `null`. The panel should render the full list immediately, mostly `UNKNOWN`. This proves the
   model before any data entry.
2. **Boss and raid pets** — mostly single flat rates. Fastest wins, and they're the tier where
   automatic KC gives `EXACT` confidence.
3. **Skilling pets, primary methods only** — one or two popular methods per pet, so every
   skilling pet has at least one usable estimate.
4. **Skilling pets, full activity tables** — the long tail. Ongoing work, ideal for outside
   contributions.
5. **Special cases** — the models that break the formula, each individually.

## Licensing constraint (matters for V2)

Old School RuneScape Wiki content is licensed **CC BY-NC-SA 3.0**. Two consequences:

- **Attribution is required.** Keep the `sources` arrays populated and surface an attribution
  line in the plugin and in any future website.
- **Non-commercial only.** A wiki-derived rate dataset cannot be redistributed as part of a
  commercially monetised product. If the V2 website ever carries ads or a paid tier, the rates
  must be sourced independently from Jagex's published figures, or licensed separately. Decide
  this before building the site, not after.

`ShareAlike` also means a derivative dataset should carry a compatible licence. Keeping the
dataset in its own directory with its own LICENSE file, separate from the BSD-2-Clause code,
keeps this clean.
