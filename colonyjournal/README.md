# Colony Journal (MineColonies addon, NeoForge 1.21.1)

A journal that shows how the buildings of a MineColonies colony depend on each other and what is missing to make
the colony self-sufficient. License: GPL-3.0-or-later (it builds on MineColonies, which is GPL-3.0).

Open it with the **Colony Journal** item (right click; recipe: book + compass) or the **J** key. It reads the colony
around the player, so it works on servers with the mod installed on the client only for the key, and on both sides
for the item.

## Tabs

- **Links** - a map of building types in four columns: gatherers, processors, crafters, consumers. Each box shows the
  type, how many exist and a status colour (green working, yellow partly staffed, orange no worker, red not built).
  Lines are supply links (miner > smeltery > blacksmith > guard tower ...); a red line means the producer of a
  building you own is missing or idle. Click a box to see what it needs and supplies. Drag to move the map.
- **Problems** - a sorted list: missing basics (town hall, warehouse, courier, residence, builder, cook, hospital,
  guards), broken supply chains, buildings without workers, and the open requests of your buildings with the answer
  to "who could supply this" (for example "Guard Tower requests Iron Sword - Blacksmith: not built"). Click a line to
  jump to the building on the map.
- **Buildings** - every building with level, workers, open requests and position.

## How it decides

The chains are a static table (`graph/BuildingGraph`). Open requests are classified by request type and item
(`analysis/NeedClassifier`): tools and armor go to the blacksmith, bows to the fletcher, food to cook/baker/farmer,
fuel to lumberjack/sawmill, and so on by item name. Unknown items are reported as "supply by hand".

## Build

From `colonyjournal/`: `./gradlew build`. Needs the MineColonies, Structurize, BlockUI, Domum Ornamentum, Framework and
Curios jars in `../libs`.
