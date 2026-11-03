# War Mode (NeoForge 1.21.1)

Team war mode for DeepStrike and Superb Warfare: blue and red teams, a regenerating team bank, rewards for kills
and interceptions, and a supply shop.

Soft dependencies: Superb Warfare (vehicle kills) and DeepStrike (interception rewards). Without them the mod still
loads; missing shop items are skipped.

## Quick start (admin)

```
/war mode score            hq | score | timed | endless
/war team auto             split online players into teams
/war start
```

Players join with `/war join blue|red`. The shop opens by using a **Supply Terminal** block. In `hq` mode place both
headquarters first: `/war hq set blue` and `/war hq set red` (the block is placed at your position).

## Modes

| Mode | Ends when |
|---|---|
| hq | an HQ reaches 0 health (explosions near it damage it); on timeout the higher HQ health wins |
| score | a team reaches `score_limit`; on timeout (if `time_limit_min` > 0) the higher score wins |
| timed | `time_limit_min` runs out; the higher score wins |
| endless | never; only bank, rewards and shop |

## Settings (`/war set <key> <value>`, `/war settings`)

`start_balance`, `balance_cap`, `regen_base`, `regen_per_player` (per second), `time_limit_min`, `score_limit`,
`hq_health`, `friendly_fire` (0/1), and for every deed `reward_<kind>` (bank) and `points_<kind>` (score), with kinds
`kill_player`, `kill_vehicle`, `intercept_missile`, `intercept_aircraft`, `intercept_ordnance`.

## Rewards

- Killing an enemy player (also by projectiles and DeepStrike missiles via their launcher).
- Destroying an enemy Superb Warfare vehicle (the vehicle's team is the team of the last rider).
- Intercepting enemy missiles, aircraft or Project: Explosive ordnance. Needs DeepStrike's `InterceptionEvent`.
  Air-defense units have no owner field, so the team of the unit's rider, or of the nearest team player within
  96 blocks, gets the reward.

## Shop

`config/warmode/shop.json` is generated on first start (70 entries). Each entry: `id`, `item`, `count`, `price`,
`category`, `team` (`any`, `blue`, `red`). Edit and run `/war reload`.

## Other commands

`/war status`, `/war leave`, `/war team add|remove <players>`, `/war bank <team> add|set <n>`,
`/war reward <team> <kind>` (manual award), `/war hq remove <team>`.

## Build

From `warmode/`: `./gradlew build`. `./gradlew runServer` starts a test server with Superb Warfare and DeepStrike
(needs `source/build/libs/dsm-*.jar`).
