# Zero-K / Spring smoke component port

Imported 2026-10-09. These are actual game resources, not generated lookalikes.

## Sources

- Game: https://github.com/ZeroK-RTS/Zero-K/tree/ee8950e2c9af13dfe5273df1348a4be0d2bd0b90
- Texture mapping: `gamedata/resources.lua` maps `smoketrail` to `PD/SmokeTrail.tga`, `smoke`/`smokesmall` to `PD/SmokeSmall.tga`, and `smoke1` to `PD/smoke1.tga` (Spring resolves resource names without case sensitivity).
- `effects/missiletrail.lua` uses the `smoke` texture in rocket effects. The original texture files are kept here. Their upstream directory designates them **PD (public domain)**. No named original texture author is asserted.
- Engine: https://github.com/beyond-all-reason/RecoilEngine/blob/bb3ff796755a74add1c624153c808edffe173347/rts/Rendering/Env/Particles/Classes/SmokeTrailProjectile.cpp
- Spring/Recoil contributors, **GPL-2.0-or-later**; see `COPYING-GPL-2.0.txt` and `ENGINE-LICENSE.txt`. Original C++ retained; translated implementation: `src/main/java/dev/sbw/deepstrike/client/SpringSmokeTrail.java`.

`manifest.json` records the original asset hashes. `tools/import_zerok_smoke.py` converts the retained TGAs into PNGs without changing RGBA pixel values, resolution or colour space. The four-cell auxiliary atlas repeats the original 64×64 sprite without resampling. Individual textures remain available for Minecraft's particle atlas.

## What was actually ported

- Source smoke texture pixels and masks.
- `CSmokeTrailProjectile::Draw` billboard frame: normalized `(position-camera) × direction` at each endpoint.
- View-dependent opacity factor `0.7 + abs(dot(view,direction))`.
- `UpdateEndPos` curve threshold `dot(dir1,dir2) < 0.98`, control handles at `distance * 0.33`, and cubic Bezier midpoint at `t=0.5`.
- Source one-quad / two-quad subdivision and vertex order, translated to Forge's vertex consumer.

## Deliberate host adaptations

- Minecraft owns the missile trajectory and continuous emission history; no guest game runs.
- Width remains calibrated to the Minecraft missiles, rather than using Spring's world-unit sizes.
- Existing 35-second ordinary / 45-second Peacekeeper duration and delayed fade remain, as requested. Original `1-t` lifetime fade is replaced by this envelope.
- Longitudinal UV is continuous in world distance; mirrored texture addressing avoids restarting texture fragments at each sample.
- Host uses straight-alpha blending. Fading reduces alpha, rather than multiplying RGB toward black as in the source's premultiplied colour path.
- Shader explicitly changes albedo to white-grey and increases texture optical thickness (ribbon 5.5 layers; secondary smoke 2.6 layers). Source RGBA files themselves are unchanged.
- Lightmap luminance modulates diffuse brightness within 0.76–1.0; this prevents unloaded/dim terrain lighting from producing black exhaust.
- Degenerate view directions get a safe perpendicular fallback. Host wind/advection, secondary wisps, render sorting and node limits remain.

This is a port of identified components, **not** an unchanged complete Zero-K effect, a copied War Thunder effect, or a measured 1:1 visual match. Native source sprites are 16×64, 32×32 and 64×64; their detail is not that of a modern volumetric simulation. No upscaling or fabricated detail is represented as original game content.

## Evidence

Only source inspection, asset conversion and Gradle compilation/packaging are performed. The user prohibited tests and game clients. GPU shader loading, appearance and side-by-side fidelity have not been checked in game.
