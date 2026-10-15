package dev.lock.core.config;

import dev.lock.core.json.JsonUtil;
import dev.lock.core.module.Catalog;
import dev.lock.core.module.ModuleDef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A named set of module states, settings, keybinds, HUD layout and visibility policy. */
public final class Profile {
    public static final String DEFAULT = "Default", MINIMAL = "Minimal HUD", RECORDING = "Recording", STREAMER = "Streamer";
    public static final List<String> BUILT_IN = List.of(DEFAULT, MINIMAL, RECORDING, STREAMER);

    private String name;
    private final Map<String, ModuleState> modules = new LinkedHashMap<>();

    public Profile(String name) {
        this.name = name;
        for (ModuleDef d : Catalog.all().values()) modules.put(d.id, new ModuleState(d));
    }

    public String name() { return name; }

    void rename(String n) { name = n; }

    public boolean builtIn() { return BUILT_IN.contains(name); }

    public ModuleState module(String id) {
        ModuleState s = modules.get(id);
        if (s == null) throw new IllegalArgumentException("unknown module " + id);
        return s;
    }

    public Map<String, ModuleState> modules() { return modules; }

    public void resetAll() {
        for (ModuleState s : modules.values()) s.reset();
        applyPreset(name);
    }

    public Profile copy(String newName) {
        Profile p = new Profile(newName);
        for (Map.Entry<String, ModuleState> e : modules.entrySet()) p.modules.get(e.getKey()).copyFrom(e.getValue());
        return p;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        Map<String, Object> mods = new LinkedHashMap<>();
        for (ModuleState s : modules.values()) mods.put(s.def.id, s.toJson());
        m.put("modules", mods);
        return m;
    }

    public static Profile fromJson(String name, Map<String, Object> m, List<String> problems) {
        Profile p = new Profile(name);
        Map<String, Object> mods = JsonUtil.obj(m == null ? null : m.get("modules"));
        if (mods == null) {
            problems.add("profile '" + name + "': no modules section, using defaults");
            p.applyPreset(name);
            return p;
        }
        for (ModuleState s : p.modules.values()) {
            Object mj = mods.get(s.def.id);
            if (mj == null) continue; // module added after this profile was saved: keep defaults
            s.fromJson(JsonUtil.obj(mj), problems);
        }
        return p;
    }

    /** Default contents of the built-in profiles. */
    public static Profile create(String name) {
        Profile p = new Profile(name);
        p.applyPreset(name);
        return p;
    }

    private void applyPreset(String preset) {
        switch (preset) {
            case MINIMAL -> {
                for (ModuleState s : modules.values()) if (s.def.hud && s.def.toggleable()) s.setEnabled(false);
                module("fps").setEnabled(true);
                module("visibility").set("mode", "Minimal");
            }
            case RECORDING -> module("visibility").set("mode", "Recording");
            case STREAMER -> module("visibility").set("mode", "Streamer");
            default -> { }
        }
    }
}
