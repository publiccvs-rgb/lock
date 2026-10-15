package dev.lock.launcher;

import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.ipc.RuntimeClient;
import dev.lock.core.ipc.RuntimeInfo;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.ui.Palette;
import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's model. Holds the configuration and keeps it in sync with a running game: while a
 * Lock runtime is connected, edits are pushed to it over IPC (the game owns the file) and changes made
 * in game are pulled back; otherwise edits are saved to the config file directly.
 */
public final class LauncherState {
    private final ConfigStore store = new ConfigStore(LockPaths.config(), LockPaths.backups());
    private LockConfig config;
    private final List<Runnable> configListeners = new ArrayList<>();
    private final List<Runnable> statusListeners = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Lock launcher IO"); t.setDaemon(true); return t; });
    private volatile RuntimeClient client;
    private volatile Map<String, Object> status;
    private volatile List<ProcessScanner.Game> games = List.of();
    private long lastSeenVersion = -1;
    private final Timer saveTimer;

    public LauncherState() {
        Log.init(LockPaths.logs().resolve("launcher.log"), "launcher");
        config = store.load();
        applyTheme();
        saveTimer = new Timer(500, e -> saveNow());
        saveTimer.setRepeats(false);
        Thread poll = new Thread(this::pollLoop, "Lock launcher poll");
        poll.setDaemon(true);
        poll.start();
    }

    public LockConfig config() { return config; }

    public Map<String, Object> status() { return status; }

    public boolean connected() { return client != null && status != null && Boolean.TRUE.equals(status.get("clientReady")); }

    public List<ProcessScanner.Game> games() { return games; }

    public List<String> loadProblems() { return store.problems(); }

    public void onConfig(Runnable r) { configListeners.add(r); }

    public void onStatus(Runnable r) { statusListeners.add(r); }

    public Path minecraftDir() {
        String s = JsonUtil.str(config.launcher, "minecraftDir", null);
        return s == null || s.isBlank() ? LockPaths.defaultMinecraftDir() : Paths.get(s);
    }

    public String jvmArgs() { return JsonUtil.str(config.launcher, "jvmArgs", Installer.DEFAULT_JVM); }

    /** Applies an edit made in the launcher UI (EDT), then pushes or saves it. */
    public void edit(Runnable change) {
        change.run();
        applyTheme();
        if (connected()) {
            Map<String, Object> json = config.toJson();
            io.execute(() -> {
                try {
                    Map<String, Object> r = client.send("setConfig", Map.of("config", json));
                    if (r.get("configVersion") instanceof Number n) lastSeenVersion = n.longValue();
                } catch (Exception e) {
                    Log.warn("push to game failed, saving to file instead: " + e.getMessage());
                    SwingUtilities.invokeLater(saveTimer::restart);
                }
            });
        } else saveTimer.restart();
        fire(configListeners);
    }

    private void saveNow() {
        try {
            store.save(config, 5);
        } catch (Exception e) {
            Log.error("saving configuration failed", e);
        }
    }

    public void replaceConfig(LockConfig c) { edit(() -> config = c); }

    public void reloadFromDisk() {
        config = store.load();
        applyTheme();
        fire(configListeners);
    }

    private void applyTheme() {
        Theme.setAccent(Palette.accent(config.active().module("theme")));
    }

    /** Sends a command to the running game (off the EDT). */
    public void command(String cmd, Map<String, Object> args, java.util.function.Consumer<Map<String, Object>> done) {
        RuntimeClient c = client;
        if (c == null) { if (done != null) done.accept(Map.of("ok", false, "error", "no game with Lock is running")); return; }
        io.execute(() -> {
            Map<String, Object> r;
            try {
                r = c.send(cmd, args);
            } catch (Exception e) {
                r = Map.of("ok", false, "error", String.valueOf(e.getMessage()));
            }
            Map<String, Object> fr = r;
            if (done != null) SwingUtilities.invokeLater(() -> done.accept(fr));
        });
    }

    private void pollLoop() {
        while (true) {
            try {
                games = ProcessScanner.scan();
                RuntimeInfo target = null;
                for (ProcessScanner.Game g : games) if (g.lock()) { target = g.runtime(); break; }
                if (target == null) {
                    if (client != null) client.close();
                    client = null;
                    status = null;
                } else {
                    if (client == null || client.info().pid() != target.pid()) {
                        if (client != null) client.close();
                        client = new RuntimeClient(target);
                        lastSeenVersion = -1;
                    }
                    Map<String, Object> s = client.send("status", null);
                    status = s;
                    Object v = s.get("configVersion");
                    if (v instanceof Number n && n.longValue() != lastSeenVersion) {
                        Map<String, Object> r = client.send("getConfig", null);
                        Map<String, Object> cj = JsonUtil.obj(r.get("config"));
                        if (cj != null) {
                            LockConfig c = LockConfig.fromJson(cj, new ArrayList<>());
                            lastSeenVersion = n.longValue();
                            SwingUtilities.invokeLater(() -> {
                                config = c;
                                applyTheme();
                                fire(configListeners);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                status = null;
                if (client != null) client.close();
                client = null;
            }
            SwingUtilities.invokeLater(() -> fire(statusListeners));
            try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
        }
    }

    private static void fire(List<Runnable> l) { for (Runnable r : List.copyOf(l)) r.run(); }
}
