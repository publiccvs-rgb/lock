# Lock user guide

## Installing

1. Run the Lock Launcher (`java -jar lock-launcher-0.1.0.jar`). On first start it finds `.minecraft`
   (change it under **Settings** if you use a custom folder).
2. **Home** lists every compatibility check: Minecraft folder, launcher profiles, Minecraft 1.21.11,
   Java runtime, official mappings, Lock runtime, Lock profile.
3. Click **Install Lock profile**. Your `launcher_profiles.json` is backed up first (to Lock's `backups`
   folder) and only the `lock` profile entry is added or updated.
4. Start the official Minecraft Launcher and play **Lock 1.21.11**. If 1.21.11 isn't downloaded yet, the
   Minecraft Launcher downloads it.

**Uninstall** removes the profile and the runtime jar but keeps your settings.

Lock loads only when the game starts. If Minecraft is already running without Lock, the launcher shows it
under *Running Minecraft* — quit and start the Lock profile.

## In game

| Key | Action |
| --- | --- |
| Right Shift | Click GUI (configurable under Interface → Click GUI) |
| F7 | Clean screen on/off (hides every Lock overlay; the GUI key still works) |
| C | Zoom (hold; scroll while zooming to change the level) |
| Ctrl + scroll, Ctrl +/-/0 | Resize the Click GUI |

Every module has an optional toggle key; module-specific keys (zoom, perspective, timers, waypoints,
sensitivity cycling, quick keys) are in each module's settings and in **Keybinds**. Lock binds never fire
while you type in chat or hold F3.

### Click GUI

- **Sidebar**: search (or just start typing), categories, Favorites, Profiles, Waypoints, Keybinds,
  Diagnostics, and quick actions (HUD editor, visibility mode, Lock folder).
- **Category columns**: click a module to enable/disable it (the row fills with the accent colour); the `⋮`
  button (or right-click) expands its settings; middle-click adds it to Favorites. Drag a column by its
  header; the chevron collapses it. Positions are remembered.
- Settings change immediately and are saved automatically (debounced, with backups).

### HUD editor

Open it from the sidebar, from a HUD module's **Position** button, or with a Quick Keys bind.
Drag elements; hold Shift to disable snapping; scroll over an element to scale it, over empty space to
scale the whole HUD. Arrow keys nudge (Shift = 10 px), R resets the selected element, Delete hides it,
G toggles the grid, P previews. The toolbar's list button shows every HUD element with a visibility toggle.

Positions are stored as an anchor (corner/edge/centre) plus an offset, so layouts survive window resizes,
fullscreen changes and GUI-scale changes. By default the HUD keeps the same physical size at every vanilla
GUI scale (Interface → Visual Scale → *Follow Minecraft GUI scale* to change that).

### Profiles and modes

Profiles store module states, settings, keybinds, HUD layout and visibility mode. Built-in: Default,
Minimal HUD, Recording, Streamer. Saved **HUD layouts** (Profiles window) let you snapshot and re-apply
element positions.

Visibility modes decide which groups of Lock elements are drawn: HUD elements, location & server info,
crosshair, in-world markers, branding, notifications, module list, custom text and diagnostics. Edit each
mode under Recording & Visibility → Visibility Manager. *Streamer* hides coordinates and the server
address by default (also inside Custom Text placeholders). *Clean* hides everything Lock draws and can
optionally hide the vanilla HUD too.

What you see is what recordings see: Lock draws into Minecraft's own frame, so there is no separate
"capture-only" visibility (see LIMITATIONS.md).

### Waypoints

Create waypoints at your position (Waypoints window, or the *Add waypoint* key), edit name, coordinates
and colour, hide them, or set one as the **target** (shown by Waypoint Distance and highlighted in the
world). Waypoints are separated per world/server and dimension (Navigation Preferences). Death Waypoint
drops one where you die. Off-screen waypoints get animated arrows at the screen edge.

## Launcher pages

- **Modules** — category chips, search, rows (click to select, click the right edge to toggle) and a
  full settings editor for the selected module.
- **Profiles**, **Waypoints**, **Keybinds** — the same data as in game.
- **Diagnostics** — live runtime state, every hook's status, module faults, compatibility report and the
  runtime log; *Write report in game* saves a JSON diagnostic report.
- **Settings** — Minecraft folder, JVM arguments for the profile, theme, export/import/reset.

While a game with Lock is running, launcher edits are pushed to the game immediately and changes made in
game appear in the launcher within ~1.5 s. Otherwise the launcher edits the configuration file.

## Files

| OS | Lock data folder |
| --- | --- |
| Windows | `%APPDATA%\Lock` |
| macOS | `~/Library/Application Support/Lock` |
| Linux | `~/.config/lock` (or `$XDG_CONFIG_HOME/lock`) |

Inside: `config.json`, `backups/`, `exports/`, `logs/` (`runtime.log`, `launcher.log`, performance CSVs,
diagnostic reports), `cache/mappings/`, `runtime/` (announcement files of running games). Override with
the `LOCK_HOME` environment variable.

If `config.json` cannot be read, Lock keeps a copy (`config.json.corrupt-<time>`), restores the newest
backup if there is one, and otherwise starts from defaults. Invalid individual values are corrected and
listed under Diagnostics.

## Performance

Atlases for fonts, icons and shapes are rasterised on a background thread at startup and uploaded a few
per frame, so opening the GUI does not stutter. HUD text content is rebuilt at a per-element rate
(e.g. 4 Hz for FPS) and sizes are cached. Diagnostics shows Lock's HUD cost per frame. If you want the
lowest possible CPU cost, set Interface → Visual Scale → *Text renderer* to **Fast** (Minecraft's
font). Lock makes no claim of improving FPS.
