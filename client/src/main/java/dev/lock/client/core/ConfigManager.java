package dev.lock.client.core;

import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.config.ModuleState;
import dev.lock.core.config.Profile;
import dev.lock.core.json.Json;
import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the live configuration. Every change goes through {@link #changed()}, which bumps a version
 * counter (used by the launcher to sync) and schedules a debounced save, so dragging a slider does
 * not hammer the disk.
 */
public final class ConfigManager {
    private final ConfigStore store = new ConfigStore(LockPaths.config(), LockPaths.backups());
    private LockConfig config;
    private volatile long version = 1;
    private boolean dirty;
    private long lastChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lock config IO");
        t.setDaemon(true);
        return t;
    });
    private final List<String> loadProblems = new ArrayList<>();

    public ConfigManager() {
        config = store.load();
        loadProblems.addAll(store.problems());
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveBlocking, "Lock config save"));
    }

    public LockConfig config() { return config; }

    public Profile profile() { return config.active(); }

    public ModuleState state(String id) { return config.active().module(id); }

    public long version() { return version; }

    public List<String> loadProblems() { return loadProblems; }

    public String recoveredFrom() { return store.recoveredFrom(); }

    public void changed() {
        version++;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    /** Replaces the whole configuration (launcher sync, import). */
    public void replace(LockConfig c) {
        config = c;
        changed();
    }

    public void tick() {
        if (!dirty) return;
        double delay = state("config").num("autosave");
        if (System.currentTimeMillis() - lastChange < delay * 1000) return;
        saveAsync();
    }

    public void saveAsync() {
        dirty = false;
        // Serialise on the game thread (the model is not thread-safe), write on the IO thread.
        LockConfig snapshot = copy();
        int backups = state("config").integer("backups");
        io.execute(() -> {
            try {
                store.save(snapshot, backups);
            } catch (IOException e) {
                Log.error("saving configuration failed", e);
            }
        });
    }

    private LockConfig copy() {
        return LockConfig.fromJson(Json.parseObject(Json.write(config.toJson())), new ArrayList<>());
    }

    synchronized void saveBlocking() {
        if (!dirty) return;
        try {
            store.save(config, state("config").integer("backups"));
            dirty = false;
        } catch (Exception e) {
            Log.error("saving configuration at shutdown failed", e);
        }
    }
}
