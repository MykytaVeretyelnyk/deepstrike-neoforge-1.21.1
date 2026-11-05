# Colony Journal (MineColonies addon, NeoForge 1.21.1)

A journal that shows how the buildings of a MineColonies colony depend on each other and what is missing to make
the colony self-sufficient. License: GPL-3.0-or-later (it builds on MineColonies, which is GPL-3.0).

Open it with the **Colony Journal** item (right click; recipe: book + compass) or the **J** key. It reads the colony
around the player, so it works on servers with the mod installed on the client only for the key, and on both sides
for the item.

## Window

The journal looks like the vanilla Advancements book: a framed window with tabs along the top (hover a tab for its
name and a one-line description), the stone background and framed item icons. Under the tab strip a plain line says what
the current tab shows, and a **?** button next to it opens the Guide at the matching topic. Esc or E closes it. It works
at every GUI scale; the window shrinks to fit small screens (the eight tabs squeeze together and stay clickable) and long
pages scroll with the mouse wheel.

Colours mean the same everywhere: green = fine, yellow = needs attention, red = a problem.

## Learning the journal

The first time the journal is opened it shows the first-steps checklist of the Guide once (a flag file
`colonyjournal-client.txt` in the game's config folder remembers that; delete it to see the checklist again). Afterwards
the **Overview** opens first: four traffic lights (supply chains, treasury and taxes, citizen happiness, trade and
prosperity) and up to five suggested next actions, each one clickable. The **Guide** tab (last tab, or the **?** button
of any tab) explains every tab, the economy rules with a worked example, happiness and tax, the market, a glossary and the
controls. The numbers in the Guide are the server defaults; the server owner can change them in the mod config.

## Tabs

- **Overview** - the colony name, citizens and day, four traffic-light cards that each jump to the tab with the details,
  and "What to do next": up to five prioritised actions made from the real state of the colony (open errors, tax above the
  comfortable rate, debt, citizen limit, buildings without a worker, nothing to sell yet, ...). A click jumps to the right
  tab or shows the building in its chain on the Links tab.
- **Links** - the supply links, one chain at a time so nothing gets tangled. A strip of chips on top has one chip per
  chain (metal and ingots, tools and weapons, wood and bows, stone and glass, crops and bread, meat and fish, dyes and
  potions, the basic buildings, other buildings) with a traffic-light dot and "working/total"; the journal opens the first
  chain that has a problem. Below is the diagram of that chain only, drawn left to right: producers on the left,
  consumers on the right. Each building is a card with a framed icon, its name next to the icon, the number of buildings
  (x3) and the workers (1/2); the frame colour shows the status (green working, yellow partly staffed, orange no worker,
  red not built, grey absent). Buildings you do not have are shown as dashed grey cards so you see what is missing.
  Lines are orthogonal arrows from the producer to the consumer, each in its own lane and with a short label of what is
  delivered when there is room: green works, red broken (the producer of a building you own is missing or idle), amber
  dashed optional, grey not needed yet. Click a building (or hover it): its arrows light up, the others fade, and the
  panel at the bottom says in plain words what to do ("Smeltery is missing. Waiting for it: Blacksmith (ingots). Build a
  Smeltery."). Click empty space to clear. A chain that is bigger than the window shrinks its spacing first, then can be
  dragged or scrolled. The **Legend** button explains frames, numbers and arrows.
- **Problems** - a sorted list: missing basics (town hall, warehouse, courier, residence, builder, cook, hospital,
  guards), broken supply chains, buildings without workers, and the open requests of your buildings with the answer
  to "who could supply this" (for example "Guard Tower requests Iron Sword - Blacksmith: not built"). Click a line to
  jump to the building in its chain on the Links tab; hover it for how serious it is.
- **Buildings** - every building with level, workers, open requests and position, under a header whose columns have
  tooltips.
- **Economy** - the colony treasury (red when negative), projected income, trade income of the last day, upkeep and
  net per day, the tax slider with a marker at the rate citizens tolerate for the current colony size, the citizen
  mood caused by the tax, the colony prosperity (average building level, happiness and size; it raises the prices of
  your goods and your output), a bar chart of the net coins of the last days and a table of what each building type
  earns or costs. Colony owners and officers set the tax with the slider and the Confirm button.
- **Market** - two sub-tabs. *Colony goods*: colonies sell what their working producer buildings make, by themselves.
  Better colonies (higher building levels, happier, bigger) have higher prices and more output. Buy x1, x8 or x64 with
  the coins of the journal's colony; sort by item, colony, prosperity or price and filter by name. Surplus above the
  stock cap is sold automatically into the treasury. *Player offers*: buy offers of other players, withdraw your own,
  or sell the item in your main hand for a price of 1 to 1000000 coins (open the journal with the J key for that).
- **Statistics** - a table of every colony on the server: owner, citizens, happiness, treasury, income and upkeep,
  tax, buildings, average level and day. Click a header to sort, scroll with the wheel (Shift for sideways), hover a
  row for all numbers. Your own colonies are highlighted.
- **Guide** - the tutorial: a list of topics on the left (previous/next buttons in a narrow window) and the text on the
  right with headings, bullets and small icons.

The Economy, Market and Statistics tabs need the mod on the server too; they refresh while they are open.

## How it decides

The chains are a static table (`graph/BuildingGraph`). Open requests are classified by request type and item
(`analysis/NeedClassifier`): tools and armor go to the blacksmith, bows to the fletcher, food to cook/baker/farmer,
fuel to lumberjack/sawmill, and so on by item name. Unknown items are reported as "supply by hand".

## Build

From `colonyjournal/`: `./gradlew build` (runs the unit tests too, among them a check that the code and both lang files agree on every translation key). Needs the MineColonies, Structurize, BlockUI, Domum Ornamentum, Framework and
Curios jars in `../libs`.
