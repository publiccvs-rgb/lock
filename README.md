# lock.

Minimal visual client for Fabric 1.21.4 (Java 21). Client-side only.

## Build
    ./gradlew build        # jar lands in build/libs/lock-0.1.0.jar
    ./gradlew runClient    # dev client

Drop the jar plus Fabric API into `mods/`.

## Menu
`Right Shift` opens the menu. Left click toggles a module, right click folds its settings open.
Config is saved to `config/lock.json`.

## Modules
| Module | What it does |
|---|---|
| General | Accent colour + who gets drawn (players, mobs, invisible, NPC filter, teammates, range) |
| Boxes | Thin corner/outline boxes, soft halo, optional faint fill, fade with distance |
| Tracers | Gradient lines that fade in from the crosshair |
| Charms | Tinted see-through player models |
| Outlines | Vanilla glow outline, coloured per mode |
| Storage | Chests, trapped, ender, barrels, shulkers (+furnaces/hoppers) through walls |
| Health | 30x4px pill above heads: damage trail, absorption, optional name/number, hides vanilla tag |
| Items | Tiered highlight on valuable drops |
| Fullbright | Client-side night vision |

Colour modes: Accent, Team, Health, Fade (slow pulse).
