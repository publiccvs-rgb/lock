package dev.lock.client.core;

import dev.lock.core.config.ModuleState;
import dev.lock.core.module.Catalog;

/**
 * Single source of truth for whether a class of Lock element is drawn. Every renderer asks this
 * manager instead of checking modes itself. The Click GUI and HUD editor are never hidden, so a
 * clean screen can always be undone.
 */
public final class VisibilityManager {
    private final ConfigManager config;
    private final NotificationManager notifications;
    private String beforeClean = "Normal";

    public VisibilityManager(ConfigManager config, NotificationManager notifications) {
        this.config = config;
        this.notifications = notifications;
    }

    private ModuleState s() { return config.state("visibility"); }

    public String mode() { return s().str("mode"); }

    public boolean clean() { return "Clean".equals(mode()); }

    public boolean visible(String group) {
        String mode = mode();
        if ("Clean".equals(mode)) return false;
        return s().bool(Catalog.modeKey(mode, group));
    }

    public void setMode(String mode) {
        if (!Catalog.MODES.contains(mode) || mode.equals(mode())) return;
        if (mode.equals("Clean")) beforeClean = mode();
        s().set("mode", mode);
        config.changed();
        if (!mode.equals("Clean")) notifications.system("Visibility", mode + " mode");
    }

    /** Clean screen on/off. Restores the mode that was active before. */
    public void toggleClean() {
        if (clean()) setMode(Catalog.MODES.contains(beforeClean) && !beforeClean.equals("Clean") ? beforeClean : "Normal");
        else setMode("Clean");
    }

    public void cycle() {
        int i = Catalog.MODES.indexOf(mode());
        String next = Catalog.MODES.get((i + 1) % (Catalog.MODES.size() - 1)); // Clean is only reachable via its own key
        setMode(next);
    }

    public boolean hideVanilla() { return clean() && s().bool("cleanVanilla"); }
}
