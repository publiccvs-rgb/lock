# lock.

A sleek, minimal companion client for **Minecraft Java Edition 1.21.11**. It is your own software, not a
Fabric/Forge/NeoForge mod:

- **Lock Launcher** — a desktop app (Swing + FlatLaf, Lock design) that installs a *Lock* profile into the
  official Minecraft Launcher, discovers running games, shows live runtime status and edits every module,
  profile, waypoint and keybind, synchronised with the running game.
- **Lock runtime** — a Java agent that the JVM loads when the game starts (`-javaagent`). It downloads
  Mojang's official mappings, remaps Lock's in-game client onto the obfuscated game and hooks 24 points
  (HUD pass, tick, input, FOV, tooltips, vanilla HUD elements).

Sign-in, downloads and launching stay with Mojang's launcher. Lock does not attach to an already running
game, does not evade or bypass anti-cheat, and makes no claim of being undetectable. Combat features and
see-through-walls visuals (ESP, tracers, chams, storage ESP) are not part of Lock — see
[docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## What's inside

| Area | Highlights |
| --- | --- |
| Click GUI (Right Shift) | Vape-style sidebar + draggable category columns, accent-filled rows, inline settings (pill toggles, round-knob sliders, segmented choices, HSV colour editor, key capture), search, favourites, profiles, waypoints, keybinds, diagnostics. Smooth TTF text, anti-aliased shapes, line icons, open/close/expand animations. Ctrl+scroll scales it live. |
| HUD (26 elements) | FPS, CPS, keystrokes, mouse buttons, coordinates, compass strip, ping, server, armor, durability, potions, player status, timers, module list, indicators, custom text, info panel, held item, inventory, notification feed, health, stats, session, waypoint distance, dimension, biome, time, environment, world info, performance, frame-time graph, memory, diagnostic overlay. Each has position, anchor, scale, alignment, text size, colours, opacity, background, corner radius, border, spacing, compact mode and visibility. |
| HUD editor | Drag & drop, alignment guides, grid snapping, scroll-to-scale (element or global), per-element visibility, preview, resets, saved layouts per profile, resolution-independent anchoring. |
| Movement & camera | Toggle sprint/sneak, zoom (hold/toggle, scroll, smooth, sensitivity), perspective (cycle/hold), FOV, view bobbing, smooth camera, sensitivity profiles, auto-jump, camera comfort, movement/sprint HUDs. |
| World | Waypoint manager (per world/server and dimension), animated markers with edge arrows, death waypoints, navigation preferences, biome/dimension/time/environment overlays. |
| Visibility | Normal, Minimal, Recording, Streamer and Clean modes with per-mode element groups; Clean screen key (F7) always restorable. |
| Profiles | Default, Minimal HUD, Recording, Streamer + custom; create/rename/duplicate/delete/reset/export/import; atomic saves with rotating backups and corrupt-file recovery. |

## Quick start (players)

1. Build or download `lock-launcher-<version>.jar` and run it (`java -jar lock-launcher-0.1.0.jar`, Java 21+).
2. **Home → Install Lock profile.** Lock copies its runtime into `.minecraft/lock/`, downloads Mojang's
   mappings (SHA-1 verified) and adds the "Lock 1.21.11" profile.
3. Open the official Minecraft Launcher and play **Lock 1.21.11**. Press **Right Shift** in game.

The full guide is in [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Building

Requirements: JDK 21, internet access on the first build (Gradle dependencies, and the Minecraft client
+ official mappings used only to compile against; nothing from Minecraft is packaged).

```sh
./gradlew :launcher:jar        # -> launcher/build/libs/lock-launcher-0.1.0.jar (contains the agent)
./gradlew :agent:jar           # -> agent/build/libs/lock-agent-0.1.0.jar
./gradlew test                 # unit tests (core, mapping, hook resolution against real mappings)
```

`:mapping:setupMinecraft` runs automatically before the client compiles: it downloads the 1.21.11 client,
libraries and mappings into `.mc-dev/` and produces a Mojang-named compile-only jar.
Development and testing details: [docs/TESTING.md](docs/TESTING.md).

## Project layout

```
core/      shared, Minecraft-free: module catalog & settings schema, config/profiles/waypoints, JSON, IPC, icons, palette
mapping/   ProGuard mappings parser, hierarchy-aware ASM remapper, dev setup
agent/     the -javaagent: version check, mappings, bytecode hooks, client class loader, IPC status
client/    the in-game client (compiled against Mojang names, remapped at runtime): modules, HUD, GUI, rendering kit
launcher/  the desktop app and installer (also a CLI: --cli check|install|uninstall|processes|status)
tools/     dev-run.sh (run the real game with Lock) and integration_test.py (end-to-end test)
```

Architecture notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
