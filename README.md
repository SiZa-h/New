# New Mod

A **Fabric** mod for **Minecraft 26.3** that adds the **Black Block**, the pillar weapon **Black Block Star**, the **Obsidian Cube**, and a set of shape-summoning abilities built around black block pillars — collisions, black holes, mark-based barrages and two sneak skills.

> 中文说明见 [`README.zh_cn.md`](README.zh_cn.md)

<p align="center">
  <img src="src/main/resources/assets/new-mod/icon.png" width="128" alt="New Mod icon">
  <br>
  <img src="docs/preview/model-preview.png" width="220" alt="Black Block Star model, isometric preview">
  <img src="docs/preview/orb-preview.png" width="160" alt="Star orb texture, magnified">
</p>

## Features

| Content | What it does |
| --- | --- |
| `new-mod:black_block` | Placeable black block (hardness 1.5, blast resistance 6.0, stone sounds). Always drops itself. |
| `new-mod:black_block_star` | Pillar weapon. **Left click** fires a short black block pillar, **right click** fires a long one; both share one generation path (same origin and direction) so they are naturally collinear. Touch damage 6, **bypasses armor**. |
| `new-mod:obsidian_cube` | The same pillar weapon, differing **only in looks and damage**: obsidian visuals and touch damage 4 that **respects armor**. |
| `new-mod:short_black_block_pillar` / `new-mod:obsidian_pillar` | Grow at 30 blocks/s, **always reaching 30 blocks**: when the front hits a block they **extend backwards**; if both ends are blocked, the softest block inside a 0.75 block sphere at either endpoint is destroyed (drops included, bedrock protected). Touching the pillar knocks creatures towards its front end; the shooter recovers with a dedicated recoil. |
| `new-mod:long_black_block_pillar` | **Right click** of the star: spawns 60 blocks long immediately, then fills with black blocks and behaviour from **tip to base** at 60 blocks/s (destroying as it fills, bedrock included), then dissipates the same way. Knocks creatures **towards the shooter**. |
| `new-mod:horizontal_axis_pillar` / `new-mod:vertical_axis_pillar` | 20 block clash pillars fired from the endpoints of the blank axes by **mouse gestures**. Two pillars of the same group facing each other **explode without creating a black hole** and mark nearby creatures **2 / 3**. |
| `new-mod:black_hole` | Created only by the **left click × right click** collision: holds everything the explosion caught at the collision point for **1 second** (velocity zeroed, position pinned each tick). |
| `new-mod:mark_lock` | The after-effect of **mark 1**: follows the marked creature for 5 seconds and **only clears its velocity** every tick. |
| `new-mod:pillar_barrage` | Triggered when a creature carries **all three marks** (1+2+3): follows it for **40 seconds**, firing a 60 block / 240 blocks-per-second piercing pillar from a random direction every **0.15 s** (damage halved), then a **1 second finale** — the target is pinned back at the **barrage trigger point**, 28 pillars hit it at once, and a power 6 explosion closes the show. |
| `new-mod:barrage_pillar` | The barrage's projectile: spawns 8 blocks away, aims through the target and keeps going. Destroys blocks along the path (bedrock included); its **damage and knockback are both halved**. |
| `new-mod:dash_pillar` | **Sneak + right click**: spawns 3 blocks behind the player, grows along the view direction and launches the player forward, clearing the blocks around them first. |
| **Infection** | **Sneak + left click**: instantly infects up to 64 blocks with a random walk starting from the targeted block (within a 3 block radius). **Releasing sneak** settles it: the blocks vanish and drop **their original loot plus one black block** each. |
| **Settings screen** | Press **K** to toggle bedrock breaking and whether explosions break blocks. |

Everything destructive ships **enabled** and is gated behind that settings screen: bedrock breaking is irreversible, and explosions and barrages reshape terrain heavily.

## Requirements

- Minecraft **26.3**
- Fabric Loader **0.19.5** or newer
- Fabric API **0.161.0+26.3** or newer
- Java **25**

## Building

```bash
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # launch a development client
./gradlew runDatagen     # regenerate data (headless)
```

## Project layout

```
src/main/java/com/sizah/
├── NewMod.java              mod entrypoint: registrations + per-tick sampling
├── block/                   the black block
├── item/                    weapons, weapon variants and per-ability cooldowns
├── entity/                  pillars, blank axes, black hole, mark lock, barrage
├── effect/                  marks, knockback-free explosions, push conflicts, view motion, infection
├── network/                 client → server payloads and the authoritative ability logic
├── config/                  server-side gameplay toggles
├── damage/                  custom damage types
└── client/                  renderers, mouse gestures, settings screen, client settings sync

tools/
├── gen-assets.js            draws every texture and the 3D item model (hand-written PNG encoder, no dependencies)
├── check-assets.js          validates the model, the PNGs, the item-atlas rule and the "pure white" palette
└── check-cjk.js             guards the "all Java comments are English" rule (localisation files are exempt)
```

## Assets

All textures, the item model and the mod icon are **generated by a script** — no image library and no third-party art:

```bash
node tools/gen-assets.js                    # regenerate textures, model and icon
node tools/gen-assets.js --preview <dir>    # also write magnified / isometric previews
node tools/check-assets.js                  # validate the generated assets
```

The how and why (hand-written PNG structure, voxel spheres, item-atlas rules, 26.3 model limitations) is documented in the generator's own comments. The images above are produced by `--preview` into `docs/preview/`.

## License

[MIT](LICENSE) © 2026 siza-h.

This mod is not affiliated with Mojang Studios or Microsoft. "Minecraft" is a trademark of Mojang Synergies AB; this project only links against the official game and Fabric APIs, and redistributes no Mojang assets.
