package dev.lock.client.module;

import dev.lock.client.LockClient;
import dev.lock.core.config.ModuleState;
import dev.lock.core.module.ModuleDef;
import net.minecraft.client.Minecraft;

/**
 * Runtime behaviour bound to a {@link ModuleDef}. The module manager calls {@link #onEnable} and
 * {@link #onDisable} whenever the effective state changes, whatever caused it (GUI, keybind,
 * launcher, profile switch).
 */
public abstract class ClientModule {
    public final ModuleDef def;
    protected final LockClient lock;
    protected final Minecraft mc;
    boolean active;
    int errors;

    protected ClientModule(LockClient lock, String id) {
        this.lock = lock;
        this.mc = Minecraft.getInstance();
        this.def = dev.lock.core.module.Catalog.get(id);
        if (def == null) throw new IllegalArgumentException("no catalog entry for " + id);
    }

    public ModuleState state() { return lock.config.state(def.id); }

    public boolean enabled() { return state().enabled(); }

    public boolean active() { return active; }

    public int errorCount() { return errors; }

    protected boolean b(String k) { return state().bool(k); }
    protected double n(String k) { return state().num(k); }
    protected int i(String k) { return state().integer(k); }
    protected int color(String k) { return state().color(k); }
    protected String s(String k) { return state().str(k); }

    protected void onEnable() {}
    protected void onDisable() {}
    public void tick() {}

    /** A keybind declared by this module (other than the toggle key) changed state. */
    public void onBind(String settingId, boolean pressed) {}

    /** Called when a setting of this module was changed through any UI. */
    public void onSettingChanged(String key) {}

    /** Short status shown next to the module in lists ("4x", "Toggled"...); null for none. */
    public String status() { return null; }
}
