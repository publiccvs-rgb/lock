# lock.

Minimal visual client for Minecraft 1.21.11.

- `src/main/java/lock/basefinder` - chunk finder engine (pure Java, no game classes except `McScan`).
  Bayesian scoring with natural-structure explanation, geometric tunnel detection, light/heightmap cross-checks
  against block-rewriting plugins, neighbour support and a self-calibrating baseline.
- `src/main/java/lock/module`, `render`, `gui`, `mixin` - modules, 1.21.11 renderer, menu, and the Fabric mixin layer.
- `tools/BaseFinderTest.java` - synthetic-world checks for the finder: `javac` the engine + test, run `BaseFinderTest`.
- `tools/GuiPreview.java`, `tools/FinderPreview.java` - headless renders of the menu and finder cards.
- `loader/` - standalone loading window (Swing).

Build: `./gradlew build` -> `build/libs/lock-0.2.0.jar` (Fabric Loader only; no Fabric API).
