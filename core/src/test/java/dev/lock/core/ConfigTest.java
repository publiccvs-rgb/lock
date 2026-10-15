package dev.lock.core;

import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.config.Profile;
import dev.lock.core.config.Waypoint;
import dev.lock.core.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path dir;

    @Test
    void builtInProfilesExistWithPresets() {
        LockConfig c = LockConfig.defaults();
        for (String n : Profile.BUILT_IN) assertTrue(c.profiles().containsKey(n));
        assertEquals("Streamer", c.profiles().get(Profile.STREAMER).module("visibility").str("mode"));
        assertEquals("Minimal", c.profiles().get(Profile.MINIMAL).module("visibility").str("mode"));
    }

    @Test
    void profileLifecycle() {
        LockConfig c = LockConfig.defaults();
        assertNull(c.create("PvE"));
        assertNotNull(c.create("PvE"), "duplicate names are rejected");
        assertNotNull(c.create("bad/name"));
        assertNull(c.duplicate("PvE", "PvE 2"));
        assertNull(c.rename("PvE 2", "Build"));
        assertTrue(c.switchTo("Build"));
        assertNotNull(c.delete(Profile.DEFAULT), "built-ins cannot be deleted");
        assertNull(c.delete("Build"));
        assertEquals(Profile.DEFAULT, c.activeName(), "deleting the active profile falls back to Default");
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        ConfigStore store = new ConfigStore(dir.resolve("config.json"), dir.resolve("backups"));
        LockConfig c = LockConfig.defaults();
        c.active().module("zoom").set("level", 6.5);
        c.active().module("fps").setEnabled(false);
        c.waypoints.add(new Waypoint("Home", 10, 64, -20, "minecraft:overworld", "sp:World", 0xFF00FF00));
        c.favorites.add("zoom");
        c.create("Custom");
        c.switchTo("Custom");
        store.save(c, 3);
        LockConfig l = store.load();
        assertTrue(store.problems().isEmpty(), store.problems().toString());
        assertEquals("Custom", l.activeName());
        assertEquals(6.5, l.profiles().get(Profile.DEFAULT).module("zoom").num("level"));
        assertFalse(l.profiles().get(Profile.DEFAULT).module("fps").enabled());
        assertEquals("Home", l.waypoints.get(0).name);
        assertTrue(l.favorites.contains("zoom"));
    }

    @Test
    void invalidValuesAreRepairedNotFatal() throws Exception {
        Path f = dir.resolve("config.json");
        Files.writeString(f, "{\"activeProfile\":\"Gone\",\"profiles\":{\"Default\":{\"modules\":{\"zoom\":{\"enabled\":\"yes\",\"settings\":{\"level\":999,\"mode\":\"Sideways\",\"unknown\":1}}}}},\"waypoints\":[{\"name\":\"x\"},{\"name\":\"ok\",\"x\":1,\"y\":2,\"z\":3}],\"futureField\":{\"keep\":true}}");
        ConfigStore store = new ConfigStore(f, dir.resolve("backups"));
        LockConfig c = store.load();
        assertEquals(Profile.DEFAULT, c.activeName());
        assertEquals(15.0, c.active().module("zoom").num("level"));
        assertEquals("Hold", c.active().module("zoom").str("mode"));
        assertEquals(1, c.waypoints.size());
        assertFalse(store.problems().isEmpty());
        store.save(c, 0);
        assertTrue(Files.readString(f).contains("futureField"), "unknown top-level data is preserved");
    }

    @Test
    void corruptFileIsKeptAndDefaultsLoad() throws Exception {
        Path f = dir.resolve("config.json");
        Files.writeString(f, "{ this is not json");
        ConfigStore store = new ConfigStore(f, dir.resolve("backups"));
        LockConfig c = store.load();
        assertNotNull(c.active());
        assertNotNull(store.recoveredFrom());
        assertTrue(Files.exists(Path.of(store.recoveredFrom())));
    }

    @Test
    void recoversFromNewestBackup() throws Exception {
        Path f = dir.resolve("config.json");
        ConfigStore store = new ConfigStore(f, dir.resolve("backups"));
        LockConfig c = LockConfig.defaults();
        c.create("Saved");
        store.save(c, 3);
        store.save(c, 3); // second save creates a backup of the first
        Files.writeString(f, "garbage");
        assertTrue(store.load().profiles().containsKey("Saved"));
    }

    @Test
    void exportImportProfile() throws Exception {
        LockConfig a = LockConfig.defaults();
        a.create("Shared");
        a.profiles().get("Shared").module("crosshair").set("shape", "Dot");
        Path out = dir.resolve("shared.json");
        ConfigStore.exportProfile(a.profiles().get("Shared"), out);
        LockConfig b = LockConfig.defaults();
        b.create("Shared");
        String msg = ConfigStore.importFile(b, out);
        assertTrue(msg.contains("Shared (2)"), msg);
        assertEquals("Dot", b.profiles().get("Shared (2)").module("crosshair").str("shape"));
    }

    @Test
    void jsonOfDefaultsValidatesCleanly() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> json = Json.parseObject(Json.write(LockConfig.defaults().toJson()));
        LockConfig.fromJson(json, problems);
        assertTrue(problems.isEmpty(), problems.toString());
    }
}
