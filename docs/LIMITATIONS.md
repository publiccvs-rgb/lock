# Compatibility and limitations

An honest list of what Lock does not do, or does only partially.

## By design

- **No ESP, tracers, chams, storage ESP, entity outlines/boxes through walls or entity off-screen
  arrows, and no combat modules.** These show other players, mobs and containers through walls, or
  automate combat. They give an advantage over other players on servers and are not part of Lock.
  (Waypoint markers and their edge arrows show only *your own saved locations*.)
- **No anti-cheat evasion, concealment or "undetectable" claims.** Lock is loaded as a declared Java agent
  that is visible in the process command line.
- **No attaching to an already running game.** Lock loads at game start through the Lock profile. The
  launcher detects running Minecraft processes and tells you when one is running without Lock.
- **No capture-only visibility.** Lock draws inside Minecraft's frame, so screenshots, OBS and other
  recordings show exactly what you see. Recording/Streamer/Clean modes change what is drawn for everyone.
  Lock does not use OS capture-exclusion APIs.
- **No inventory movement** (walking with a container open) — servers commonly treat that as illegitimate
  input.

## Partial support

| Feature | Limitation |
| --- | --- |
| Ping | Shows the latency the server publishes in the tab list; `--` in singleplayer or when hidden. |
| Hitbox Overlay | Toggles the vanilla F3+B renderer; appearance is not customisable. |
| Vanilla XP bar control | Its hooks apply when the game first loads the XP-bar renderer (not in creative); Diagnostics shows them as "pending" until then. |
| Option-based modules (FOV, view bobbing, auto-jump, camera comfort, menu blur, sensitivity profiles) | They keep vanilla options at your values while enabled and restore the previous values when disabled. If the game crashes while they are enabled, `options.txt` may keep Lock's values. |
| Process discovery | Games with Lock are always found (they announce themselves). Games without Lock are recognised by command line, which Windows usually does not expose to other processes. |
| Safe disconnect | The launcher can close its connection at any time; the in-game runtime cannot be unloaded from a running JVM (restart the game without the Lock profile). |
| Relative agent path | If the absolute path to `.minecraft` contains spaces, the profile uses `-javaagent:lock/lock-agent.jar`, which relies on the official launcher using the game directory as the working directory. |
| Minecraft versions | 1.21.11 only. Other versions are detected and Lock disables itself; the game runs normally. |
| Tooltip customisation | Adds lines to the vanilla tooltip only; it does not restyle tooltips. |

## Not verified here

- Windows and macOS were not tested in this environment (development and the end-to-end test ran on Linux
  with software OpenGL). Lock is pure Java and uses the same APIs on every OS, but launcher folder detection
  and the official launcher's profile handling should be checked on each OS.
- Real-GPU frame-rate impact was not measured (see TESTING.md).
