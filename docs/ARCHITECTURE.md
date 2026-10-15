# Architecture

```
 Lock Launcher (desktop)                         Minecraft 1.21.11 JVM
 ───────────────────────                         ──────────────────────────────────────────────
 Installer ── writes "lock" profile ──▶ official launcher starts the game with
                                          -javaagent:.minecraft/lock/lock-agent.jar
                                                   │
                                          LockAgent.premain (before Minecraft's main)
                                            ├─ detect version from the client jar's version.json
                                            ├─ load/download Mojang mappings (SHA-1 verified)
                                            ├─ HookTransformer: 24 injection points
                                            ├─ ClientClassLoader: remaps lock-client/** onto obf names
                                            └─ IPC server (127.0.0.1, random port, token)
 LauncherState ◀────── JSON lines over loopback (status / getConfig / setConfig / …) ──────▶
                                          LockClient (created on the first client tick)
                                            ├─ ConfigManager (profiles, autosave, backups)
                                            ├─ ModuleManager (69 modules; lifecycle; fault isolation)
                                            ├─ HudManager / HudEditorScreen / LockScreen (Click GUI)
                                            ├─ Keybind / Visibility / Notification / Waypoint managers
                                            └─ Diagnostics (hooks, faults, compatibility, reports)
```

## Compiling against readable names, running against obfuscated ones

`:mapping:setupMinecraft` downloads the official client and its ProGuard mappings and remaps the client to
Mojang names (a local, never-distributed compile-only jar). The `client` module is ordinary Java written
against those names. Its classes are packed into the agent jar under `lock-client/` — a path the system
class loader never looks at — and `ClientClassLoader` remaps each class to the obfuscated names of the
running game with `HierarchyRemapper`, which resolves inherited members (e.g. `LocalPlayer.getX()` declared
on `Entity`, or an override of `Screen.render`) by walking the class hierarchy.

## Hooks

`HookSpecs` declares every injection point in Mojang names; `HookTransformer` resolves them and reports each
one individually (applied / pending until the class loads / failed), so a moved method degrades one feature
instead of the whole client. Head injections run where the verifier state equals the method's initial
frame. Code that must run after a method returns uses a **wrapper**: the original is renamed to
`lock$<name>` and a straight-line method with the original name calls it, so Lock never has to agree with
ProGuard's optimised stack-map frames (which drop dead locals before returns).

Every hook enters through `Hooks`, which guards the call; a hook that keeps throwing is switched off.
Modules are isolated the same way: 20 errors fault the module for the session, and Diagnostics shows it.

## Rendering kit

`Gfx` wraps `GuiGraphics`: opacity, anti-aliased rounded rectangles and circles (multi-resolution circle
textures, linear filtering), soft shadows (blurred nine-slice), `LockFont` (Poppins glyph atlases rasterised
per weight at the real pixel size, flat-array glyph lookup, symbol fallback) and `Icons` (shared vector
`IconSet`, rasterised per size). Rasterisation happens off-thread during warm-up; uploads are drained on the
render thread a few per frame.

## Configuration

`core` owns the schema: every module's settings are typed (`Bool`, `Num`, `Color`, `Choice`, `Text`, `Key`)
and validated on load. `LockConfig` holds profiles, waypoints, favourites, saved layouts, GUI window state
and launcher preferences, preserving unknown fields from newer versions. `ConfigStore` writes atomically,
rotates backups and recovers from corrupt files.

## IPC

The runtime writes `runtime/<pid>.json` (port + random token, owner-only permissions on POSIX) and serves
JSON lines on 127.0.0.1. Commands: `status`, `getConfig`, `setConfig`, `setEnabled`, `setSetting`,
`switchProfile`, `setMode`, `openGui`, `openHudEditor`, `closeGui`, `report`. Commands run on the game thread.
The announcement file is removed on exit; stale files are cleaned by the launcher.
