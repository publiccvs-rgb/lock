# Testing

## Unit tests

```sh
./gradlew test
```

- `core`: JSON round-trips and error handling; every catalog module is well-formed (description, toggle
  key, full HUD property set, defaults that validate to themselves); visibility modes cover every group;
  anchors resolve/invert; setting validation (clamping, stepping, bad input → default, text sanitising,
  keys by name/code); profile lifecycle; save/load round trip; invalid values repaired and unknown fields
  preserved; corrupt-file recovery and restore from backup; profile export/import with renaming.
- `mapping`: ProGuard parsing in both directions; inherited member resolution; remapping real class bytes.
- `agent`: every hook resolves against the real 1.21.11 mappings (uses `.mc-dev`, skipped if absent).

## End-to-end test against the real game (Linux)

Needs Xvfb, Mesa (software OpenGL is enough) and a singleplayer world named `Test`:

```sh
./gradlew :agent:jar :mapping:setupMinecraftAssets
# create a world once, e.g. with the dedicated server (eula=true, level-name=Test), then copy it to
# .mc-dev/run/saves/Test, and in .mc-dev/run/options.txt set onboardAccessibility:false
python3 tools/integration_test.py --keep-screenshots /tmp/lock-shots
```

It launches the real client with the agent (offline dev account), waits for READY over IPC and checks:
version detection; every hook applied or legitimately pending; every catalog module bound; module toggles
and setting changes via IPC; no module faults while rendering the HUD, Click GUI and HUD editor; settings
persisted to disk; the runtime announcement removed on exit; and the safe-failure path (agent disabled →
the game keeps running without Lock). Last run: **21/21 passed**.

`tools/dev-run.sh` starts the game with the freshly built agent for manual testing.

## Manually verified in the real game (screenshots during development)

Click GUI (columns, inline settings, sliders, choices, toggles, colour swatches, window drag, tooltips,
Ctrl+scroll scaling, open/close/expand animations), HUD elements with auto-placement, HUD editor (toolbar,
element list, selection labels, guides), waypoint markers and edge arrows, notifications, launcher pages,
installer against a sample `launcher_profiles.json` (other profiles and settings preserved, backup written,
uninstall clean), and two-way launcher ⇄ game sync.

## Performance measurement

In this development container the world is rendered on the CPU (Mesa llvmpipe, 1–3 FPS), which inflates
wall-clock timings of everything on the render thread. A stack-sampling profile of the render thread showed
Lock code in about 1–2 % of samples. Real-GPU numbers have not been measured here; use the Diagnostic
Overlay / Diagnostics page (HUD µs per frame) and Performance Logging (CSV) on your own machine.
