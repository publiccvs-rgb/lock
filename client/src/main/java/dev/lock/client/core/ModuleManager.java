package dev.lock.client.core;

import dev.lock.client.LockClient;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.module.ModuleAccess;
import dev.lock.core.module.Catalog;
import dev.lock.core.module.ModuleDef;
import dev.lock.core.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Registry of module implementations; keeps runtime state in step with configuration. */
public final class ModuleManager {
    private static final int MAX_ERRORS = 20;
    private final LockClient lock;
    private final Map<String, ClientModule> modules = new LinkedHashMap<>();
    private final List<HudModule> huds = new ArrayList<>();
    private final Set<String> faulted = new HashSet<>();

    public ModuleManager(LockClient lock) { this.lock = lock; }

    public void register(ClientModule m) {
        if (modules.put(m.def.id, m) != null) throw new IllegalStateException("duplicate module " + m.def.id);
    }

    /** Orders modules like the catalog and indexes HUD elements. Call after registration. */
    public void finish() {
        Map<String, ClientModule> ordered = new LinkedHashMap<>();
        for (String id : Catalog.all().keySet()) if (modules.containsKey(id)) ordered.put(id, modules.get(id));
        modules.clear();
        modules.putAll(ordered);
        for (ClientModule m : modules.values()) if (m instanceof HudModule h) huds.add(h);
    }

    /** Catalog entries without a bound implementation (reported by the compatibility checker). */
    public List<String> unbound() {
        List<String> out = new ArrayList<>();
        for (ModuleDef d : Catalog.all().values()) if (!modules.containsKey(d.id)) out.add(d.id);
        return out;
    }

    public ClientModule get(String id) { return modules.get(id); }

    @SuppressWarnings("unchecked")
    public <T extends ClientModule> T get(String id, Class<T> type) { return (T) modules.get(id); }

    public Collection<ClientModule> all() { return modules.values(); }

    public List<HudModule> huds() { return huds; }

    public boolean faulted(String id) { return faulted.contains(id); }

    public Set<String> faultedIds() { return faulted; }

    public void tick() {
        for (ClientModule m : modules.values()) {
            sync(m);
            if (m.active() && !faulted.contains(m.def.id)) guard(m, "tick", m::tick);
        }
    }

    /** Runs onEnable/onDisable if the configured state differs from the runtime state. */
    public void sync(ClientModule m) {
        boolean want = m.enabled() && !faulted.contains(m.def.id);
        if (want == m.active()) return;
        ModuleAccess.setActive(m, want);
        guard(m, want ? "enable" : "disable", want ? () -> ModuleAccess.enable(m) : () -> ModuleAccess.disable(m));
    }

    public void syncAll() { for (ClientModule m : modules.values()) sync(m); }

    public void toggle(String id) { setEnabled(id, !lock.config.state(id).enabled()); }

    public void setEnabled(String id, boolean on) {
        ClientModule m = modules.get(id);
        if (m == null || !m.def.toggleable()) return;
        if (m.enabled() == on) return;
        m.state().setEnabled(on);
        if (on) faulted.remove(id);
        lock.config.changed();
        sync(m);
        if (on && m instanceof HudModule h) lock.hud.requestPlacement(h);
        lock.notifications.module(m.def.name, on);
    }

    /** Guards a call into a module; repeated failures fault the module (runtime only, not saved). */
    public void guard(ClientModule m, String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            int n = ModuleAccess.error(m);
            if (n == 1) Log.error("module " + m.def.id + " failed in " + what, t);
            if (n >= MAX_ERRORS && faulted.add(m.def.id)) {
                Log.error("module " + m.def.id + " disabled after repeated errors: " + t);
                lock.notifications.error(m.def.name, "Stopped after errors (see Diagnostics)");
                ModuleAccess.setActive(m, false);
            }
        }
    }

    public void resetFault(String id) { faulted.remove(id); }
}
