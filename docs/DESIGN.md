# Pet Hunter — Design Specification (V1)

## 1. Scope

**In:** local RuneLite sidebar panel, all 71 pets, retrospective dryness estimation from
existing XP and kill counts, method comparison for missing pets, confidence labelling.

**Out:** any network request, any account sync, any website, any live per-action roll counting,
any overlay drawn on the game scene.

## 2. Pet taxonomy

Every pet belongs to exactly one `rateModel`. The model determines how an attempt count and a
per-attempt probability are derived.

| Model | Description | Attempt source | Confidence ceiling |
|---|---|---|---|
| `FLAT_PER_KILL` | Fixed 1/N per kill | Collection log page KC, chat KC | `EXACT` |
| `FLAT_PER_ROLL` | Fixed 1/N per reward roll or completion | Log page count where available | `EXACT` or `ESTIMATED` |
| `SKILL_LEVEL_SCALED` | `1 / (B − Level × 25)`, B per activity | Skill XP + assumed method | `ESTIMATED` |
| `STATIC_IGNORES_FORMULA` | Fixed rate despite being a skilling pet | Skill XP + assumed method | `ESTIMATED` |
| `CONTRIBUTION_SCALED` | Rate varies with minigame contribution | Not derivable | `UNKNOWN` unless user enters count |
| `UNIQUE_CONDITIONAL` | Rolled only when a unique/purple is hit | Requires unique count | `UNKNOWN` or user-supplied |
| `ONE_OFF` | Quest, achievement, or purchase | Ownership only | n/a — no dryness |

`ONE_OFF` pets appear in the panel for completeness but show no dryness figure. Don't try to
invent one.

## 3. Confidence tiers

```
EXACT      attempt count read from a real counter (collection log KC, chat KC)
ESTIMATED  attempt count derived from XP under a stated method assumption, or entered manually
           by the user (the assumption text says "your manually entered count of N")
UNKNOWN    no attempt count or no verified rate; show a call to action, never a number
```

`ONE_OFF` pets have no tier at all: their result is "not applicable", distinct from `UNKNOWN`.
In code, a figure exists only as a `TieredValue`, which cannot be built without a tier and an
assumption, and cannot be `UNKNOWN`.

Rendering rules:
- `EXACT` — plain text figure.
- `ESTIMATED` — figure with a tilde and a muted colour; tooltip states the assumption verbatim
  ("assumes all 14.2m Woodcutting XP came from teak trees").
- `UNKNOWN` — no figure. Show why, and what the user can do about it (open the collection log,
  enter a count manually).

The panel must never render a number without its tier. A figure whose tier is lost in the
pipeline is a bug, not a cosmetic issue.

## 4. Dryness math

### 4.1 Flat rates

Given `n` attempts at per-attempt probability `p`:

```
P(still dry)     = (1 - p)^n
P(would have it) = 1 - (1 - p)^n
expected attempts = 1 / p
```

Report `P(still dry)` as the headline ("X% of players are still without it at your count") and
`n / (1/p)` as a secondary "times the drop rate" figure. Compute in log space when `n` is large
to avoid underflow.

### 4.2 Level-banded skilling pets

The naive approach evaluates all historical XP at the player's current level, which
systematically overstates how dry they are, because early XP was earned at a worse rate. Do it
properly:

```
for each level band L in [startLevel .. currentLevel]:        # currentLevel <= 99
    bandEnd       = (L == 99) ? currentXp : min(currentXp, xpAtLevel(L+1))
    xpInBand      = bandEnd - xpAtLevel(L)
    actionsInBand = xpInBand / method.xpPerAction              # fractional, never floored
    rateInBand    = 1 / (source.baseChance - (L * 25))
    accumulate:  logPDry += actionsInBand * log1p(-rateInBand)

if actionsAt200m > 0:                                          # terminal band
    logPDry += actionsAt200m * log1p(-15 / (source.baseChance - 99 * 25))

P(still dry) = exp(logPDry)
```

Notes:
- The level used is the **unboosted** level. Temporary boosts do not affect pet rates.
- The rate stops improving above level 99, so the level-99 band runs all the way to current XP.
  (Capping it at a virtual level-100 threshold would silently drop everything from 14.39m to
  200m XP.)
- At 200m XP the **final denominator** is divided by 15: `(B − 99×25) / 15`, not `B/15 − 99×25`.
  XP stops accruing at 200m, so actions performed after it cannot be recovered from the client
  retrospectively. The integrator takes them as an explicit input that is 0 unless a real count
  exists; in V1 the 200m rate matters mainly for forward-looking method comparison.
- `startLevel` is 1 unless the source has a `minLevel`, in which case XP below that threshold
  contributed no rolls and must be excluded.
- `STATIC_IGNORES_FORMULA` sources skip banding entirely: every action is evaluated at the fixed
  `1/flatRate`, and the 200m divisor is not applied.

### 4.3 Multi-source pets

Some pets are obtainable from several activities (multiple bosses, multiple skilling methods).
Combine independent sources multiplicatively:

```
P(still dry overall) = product over sources of P(still dry | source)
```

Only include a source when its attempt count and rate are known. Leaving out a factor that is at
most 1 can only raise the product, so with any `UNKNOWN` source the shown P(still dry) is an
**upper bound**: the player is *at least* this dry. The UI must say so in those words, not "lower
bound", which reads backwards next to a percentage.

Sources are only independent if they consume different attempts. All sources derived from one
skill's XP draw on the same XP, and the client cannot tell how that XP was split between
activities. So per pet, **at most one XP-derived source is used**: the method the player chose
(`methodOverrides`). The other XP-derived sources are alternative explanations of the same XP,
not missing sources, and do not make the figure a bound. In code every figure carries an attempt
pool (`counter:<key>`, `source:<id>`, `xp:<SKILL>`) and combining two figures from the same pool
is an error.

The combined tier is the weakest tier among the combined figures.

## 5. Game state reads

All of the following must be treated as unverified until checked against the current
`runelite-api`. Put each in a constants file with a comment recording what it was verified
against and when.

| Need | Approach |
|---|---|
| Pet ownership | Scrape the collection log widget when opened; also catch the obtainment chat messages ("funny feeling like you're being followed" / "something weird sneaking into your backpack") |
| Boss kill counts | Read the KC line on each collection log page when scraped; also parse the chat KC message after a kill |
| Skill XP and levels | `client.getSkillExperience(Skill)` and `getRealSkillLevel(Skill)`, refreshed on `StatChanged` |
| Account identity | Account hash, for keying persisted state |
| Membership / game mode | Only to suppress pets that are unobtainable on the current account type |

Verified in-game on 2026-09-17 (details and IDs in `state/GameIds.java`): the collection log item
grid is `Collection.ITEMS_CONTENTS`, obtained items draw at opacity 0 and missing ones at 175, and
each page header lists `<Label>: n` counters such as `Callisto kills`. Counter keys are that label
in snake case (`callisto_kills`), and kill count chat messages map to the same key. A dataset
source's `counterKey` should only be set once its label has been seen in-game.

Ownership detection must be **additive and sticky**. Once a pet is recorded as obtained for an
account, a later failed scrape never un-obtains it.

The collection log is only readable when the player opens it. On first run the panel will have
no ownership data. This is expected: show a clear "open your collection log to sync" state
rather than an empty list that looks broken.

## 6. Persistence

Keyed by account hash, stored through `ConfigManager` under the plugin's config group. In practice
this is RuneLite's RS profile configuration (`setRSProfileConfiguration`, group `pethunter`), which
RuneLite keys by account hash and game mode, so a Leagues or Deadman character is also kept apart
from the main account. State reloads on `RuneScapeProfileChanged`.

```
obtainedPetIds        set of pet ids
counters              map: counterKey -> { value, lastSeenEpoch }
lastLogSyncEpoch      when the collection log was last successfully scraped
methodOverrides       map: petId -> methodId   (user's choice of assumed method)
manualAttemptCounts   map: sourceId -> int     (user-entered, for UNKNOWN sources; keyed by
                                                source id because those sources usually have
                                                a null counterKey)
```

Persisted values are a cache of game state, never the source of truth. On conflict, live game
state wins, except for `obtainedPetIds`, which is sticky per above.

## 7. UI

Single `PluginPanel` behind a `NavigationButton`.

**Header:** obtained count out of total, overall completion bar, last-sync timestamp.

**Controls:** filter (All / Missing / Obtained), group-by (Source type / Skill / Dryness),
sort (Dryness descending / Alphabetical), and a search box. The chosen view is remembered.

**Collapsed row:** pet icon, name, source summary, and either a dryness figure with its
confidence marker, an obtained checkmark, or an unknown indicator.

**Expanded detail:**
- Every source for the pet, each with its attempt count, per-attempt rate, and confidence.
- The method assumption in use, with a dropdown to change it. Changing it recomputes live.
- An "assumptions" line stating in plain words what the estimate rests on.
- A manual attempt-count input where the source is `UNKNOWN`.

**Method comparison was cut from V1** (2026-09-17). The panel's job is: see every pet, see how
likely you are to still be without it, and see the ways it can be hunted. Ranking methods by
expected hours needs actions-per-hour figures that vary too much per player to state honestly, so
the expanded view lists each activity with its rate and lets the player say which one they trained,
without ranking them.

**Empty and error states:** no collection log sync yet; logged out; dataset entry unpopulated;
pet unobtainable on this account type. Each needs a specific message.

## 8. Testing priorities

The math layer is pure and deserves real coverage:

- Flat dryness at n=0, n=1, n = 1/p, n = 5/p
- Log-space stability at very large n
- Level-band integration against a hand-computed two-band example
- Level clamping at 99
- The 200m XP multiplier applied to the denominator, not the base
- Multi-source combination, including one known and one unknown source
- `ONE_OFF` pets producing no dryness figure rather than zero or NaN
- Dataset loading with a null rate, a missing method, and an unknown `rateModel`

## 9. Plugin Hub review readiness

Review focuses on security and Jagex rule compliance, and is increasingly handled by an
automated reviewer for simple plugins. Keep the submission boring:

- Zero network calls, zero reflection, zero native code, no added dependencies
- No gameplay automation of any kind
- Config toggles default to the least surprising behaviour
- `runelite-plugin.properties` with accurate description and tags
- An icon, and a README that sets accuracy expectations honestly

If V2 introduces sync, the precedent from existing log-syncing plugins is that outbound
connections must be **opt-in behind a config toggle**, the user agent must identify the plugin,
and any button opening an external site must warn that the user's IP is being sent to a server
RuneLite does not operate. Design for that now so it isn't a rewrite later.
