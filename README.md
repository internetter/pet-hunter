# Pet Hunter

A RuneLite plugin that lists every pet in Old School RuneScape, marks the ones you have and the
ones you still need, and gives a rough dryness estimate for the ones you're missing.

That's it. It's a list, a percentage, and a bit of detail about where each pet comes from.

## What you get

- **The full pet list**, with the ones you've obtained marked and the rest sorted by how dry you
  are. Filter, group and search it however you like.
- **A dryness percentage** per missing pet: the share of players who would still be without it at
  your kill count or XP.
- **A bit of detail** when you expand a pet: every activity that can drop it and its rate, so you
  can see your options if you fancy hunting it. For skilling pets you can say which activity you
  trained, which sharpens the estimate.

## Where the numbers come from

- **Kill counts** read from your collection log pages, and from kill count chat messages.
- **Skill XP**, read from the client.
- **Your own counts**, typed in, where the game keeps no counter (Chambers of Xeric purples, for
  example).
- **Drop rates** from the Old School RuneScape Wiki, each checked against a second wiki page.

Everything is stored per character. Nothing leaves your machine.

## Be realistic about the estimates

A percentage here is a rough guide, not a fact. The plugin can only see what the client tells it,
and plenty of what decides your real chance is invisible to it:

- It can't tell how you actually trained. Nobody mines one rock type for 10m XP. With no method
  chosen it assumes the worst rate for that pet, so the figure never says you're drier than you
  are, and picking a method only makes it a better guess.
- A kill count can include kills that never rolled the pet, like group kills at a boss where only
  the top damage dealer rolls. Those are marked with `!`.
- Some pets depend on things no counter records, such as how well a raid went, or how many
  purples you've had. Those either need a count from you, or say `UNKNOWN`.
- Some pets say `UNKNOWN` because there's no honest number to give: either the wiki doesn't
  publish the rate, or nobody agrees on what counts as one attempt. Each one tells you which.

The markers on each figure say how much to trust it: plain text is an exact count from the game,
`~` is an estimate, `<=` means you're at least that dry, and `!` means the count is probably
generous.

RuneLite's Plugin Hub review doesn't check whether a plugin's information is accurate, so treat
all of this as a guide.

## What it doesn't do

- No account sync, no website, no network requests at all.
- No advice on which method is fastest, and no expected time to the pet.
- No live roll counting; it works from your totals.

## Setup

1. Install from the Plugin Hub, or build locally with `./gradlew shadowJar`.
2. Click the paw icon in the RuneLite sidebar.
3. Open your collection log once, including the All Pets page under Other, so it can read what
   you already have. Opening a boss page records that boss's kill count.

## Data and licensing

Rates come from the Old School RuneScape Wiki, which is licensed CC BY-NC-SA 3.0. The dataset
therefore lives in `src/main/resources/com/pethunter/data/` under its own licence, separate from
the BSD-2-Clause code, and every rate lists the pages it came from. See `docs/DATA.md`.

## Contributing

The data is the hard part, not the code. If a rate is missing or wrong, a pull request editing
`src/main/resources/com/pethunter/data/pets.json` with a source URL is the most useful thing you
can send. `./gradlew build` validates the dataset and fails if a rate has no source.

## License

BSD-2-Clause for the code. The dataset is CC BY-NC-SA 3.0.
