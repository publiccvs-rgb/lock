package lock.module;

import lock.setting.BindSetting;
import lock.setting.Setting;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final String desc;
    public final List<Setting<?>> settings = new ArrayList<>();
    public final BindSetting bind = new BindSetting("Bind");
    private boolean enabled;

    protected Module(String name, String desc, boolean enabledByDefault) {
        this.name = name;
        this.desc = desc;
        this.enabled = enabledByDefault;
        bind.desc = "Keybind for " + name;
        settings.add(bind);
    }

    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }

    public boolean enabled() { return enabled; }

    public void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        if (v) onEnable(); else onDisable();
    }

    public void toggle() { setEnabled(!enabled); }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
}
