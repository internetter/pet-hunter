# Pet Hunter

A RuneLite plugin that shows every pet in Old School RuneScape in one list, marks the ones you
have, and estimates how dry you are on the ones you don't.

## What it does

- **Every pet in one place.** All 71 collection log pets, filterable by obtained or missing,
  grouped by source type, skill or dryness, searchable, and sortable by how dry you are.
- **Dryness per pet**, as the share of players who would still be without it at your count:
  "36.6% of players would still be without this pet at your count".
- **Confidence on every figure**, so you always know what it rests on:
  - plain text means an exact count read from the game, such as a boss kill count;
  - `~` means an estimate, from your skill XP or a count you entered yourself;
  - `<=` means you are at least that dry, because something could not be counted;
  - `!` means the count includes attempts that may never have rolled the pet, such as group
    kills at a boss where only the MVP rolls;
  - `UNKNOWN` means there is no honest number to show, and the pet says why.
- **The ways to hunt each pet.** Expanding a pet lists every activity that can drop it, with its
  rate and, for skilling pets, a dropdown to say which one you trained.
- **Your own counts** where the game keeps none, such as Chambers of Xeric uniques.

## How it reads your progress

- **Collection log:** open it and the plugin records which pets you have and the kill counts on
  each page. Ownership only ever gets added, so a half-drawn page can never un-obtain a pet.
- **Chat:** the collection log item message, the ogre bow's chompy kill check, and kill or
  harvest count messages.
- **Skill XP**, read at login and as it changes.

Everything is stored per character and game mode, and nothing leaves your machine.

## How the estimates work

Boss and activity pets are a flat chance per kill or per reward roll, so after `n` attempts at
rate `p` the chance you would still be without the pet is `(1 - p)^n`.

Most skilling pets use `1 / (B - level x 25)`, where `B` is a base chance that varies by
**activity**, not by skill. Your XP is split into level bands and each band is evaluated at the
rate that actually applied then, because early XP was earned at a worse rate.

Nobody trains one activity exclusively, so with no method chosen the plugin evaluates your whole
XP at that pet's **worst-rate** activity. That can never claim you are drier than you are, and
the tooltip gives the best case too. Choosing the method you trained gives a sharper figure.

## Accuracy

RuneLite's Plugin Hub review does not check whether a plugin's information is correct, so treat
every figure here as an estimate.

Every rate in the dataset was taken from the Old School RuneScape Wiki and checked against a
second wiki page before being used, and each entry carries the pages it came from. Where a rate
or a count could not be verified, the plugin shows `UNKNOWN` and says why rather than guessing.
Known gaps at 1.0:

- **Theatre of Blood and Tombs of Amascut**: the pet chance depends on raid performance and raid
  level, which a completion count cannot capture.
- **Tangleroot and Rift guardian**: whether a roll happens per harvest, per check-health, per
  essence or per rune is not settled, so no estimate is made.
- **Soup and Mr McGroot**: the wiki does not publish their base chances yet.
- **Scorpia's offspring, Lil' Zik, Tumeken's guardian**: rate shown, no estimate.
- Kill counts read from the collection log can include attempts that never rolled the pet in
  groups; those figures are marked `!` and explain themselves.

Estimates are also weaker when you trained a skill with a mix of methods, or when your kill count
for a boss is not recorded anywhere the client can read.

## Not in this version

- No account sync, no website, no outbound network requests of any kind.
- No comparison of which method is fastest, and no expected hours to the pet.
- No live per-action roll counting; estimates come from your totals.

## Setup

1. Install from the Plugin Hub, or build locally with `./gradlew shadowJar`.
2. Open the Pet Hunter icon in the RuneLite sidebar.
3. Open your in-game collection log once, including the All Pets page under Other, so ownership
   and kill counts can be read.

## Data sources

Rates come from the Old School RuneScape Wiki and from Jagex figures the wiki cites. Every rate
carries its sources. Wiki content is licensed CC BY-NC-SA 3.0, which is why the dataset lives in
`src/main/resources/com/pethunter/data/` under its own licence, separate from the BSD-2-Clause
code. See `docs/DATA.md`.

## Contributing

The dataset is the bottleneck, not the code. If a rate is missing or wrong, a pull request
editing `src/main/resources/com/pethunter/data/pets.json` with a source URL is the most useful
contribution. `./gradlew build` validates the dataset and fails on an unsourced rate.

## License

BSD-2-Clause for the code; the dataset is CC BY-NC-SA 3.0.
