# lock.

Minimal visual client for Minecraft 1.21.11. Work in progress.

- `loader/` - frameless loading window (Swing). Build: `javac -d out src/lock/loader/*.java && cp res/wordmark.png out/`, run: `java -cp out lock.loader.Loader`. Prebuilt: `dist/lock-loader.jar`.
- `src/main/java/lock/gui` - in-game menu (Mojang-mapped 1.21.11). `GuiView` holds layout/animation/input and draws through `Gfx`, so `tools/GuiPreview.java` can render it headless.
- `src/main/java/lock/module`, `setting` - modules and their settings.
- `src/main/java/lock/render` - ESP rendering; being ported to the 1.21.11 render pipeline (excluded from the build for now).

Menu text uses Arial, read from the system's fonts (not bundled); falls back to Minecraft's font.
