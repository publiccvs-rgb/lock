package dev.lock.client.module;

import dev.lock.client.LockClient;
import dev.lock.client.render.Gfx;
import dev.lock.core.module.Anchor;
import dev.lock.core.module.ModuleDef;

/**
 * A positionable HUD element. Subclasses report their unscaled size and draw at (0, 0); the HUD
 * manager applies anchor, offset, scale, opacity, background and border from the module's settings.
 */
public abstract class HudModule extends ClientModule {
    /** Last on-screen bounds (GUI pixels), used by the HUD editor for hit testing. */
    public int lastX, lastY, lastW, lastH;
    public long lastRenderNanos;
    /** True while the HUD editor draws this element; dynamic elements then show placeholder content. */
    public boolean editing;

    protected HudModule(LockClient lock, String id) { super(lock, id); }

    /** Unscaled content size {width, height}, excluding padding. */
    public abstract int[] size(Gfx gfx);

    /** Draws content at (0,0) in unscaled coordinates. {@code w,h} is the content box. */
    public abstract void draw(Gfx gfx, int w, int h);

    /** Elements that draw their own cards (e.g. toasts) skip the shared background. */
    public boolean ownBackground() { return false; }

    /** True when there is nothing worth drawing (the element is skipped entirely, except in the editor). */
    public boolean empty() { return false; }

    /** Content shown in the HUD editor when the live content is empty. */
    public void preview() {}

    private static final java.util.Map<String, String> ICONS = java.util.Map.ofEntries(
            java.util.Map.entry("fps", "gauge"), java.util.Map.entry("cps", "mouse"), java.util.Map.entry("coordinates", "pin"),
            java.util.Map.entry("ping", "signal"), java.util.Map.entry("server_status", "server"), java.util.Map.entry("durability", "hammer"),
            java.util.Map.entry("potion_effects", "flask"), java.util.Map.entry("status", "heart"), java.util.Map.entry("timers", "timer"),
            java.util.Map.entry("custom_text", "text"), java.util.Map.entry("info_panel", "info"), java.util.Map.entry("notification_feed", "bell"),
            java.util.Map.entry("movement_indicators", "move"), java.util.Map.entry("sprint_status", "zap"), java.util.Map.entry("health_info", "heart"),
            java.util.Map.entry("gameplay_stats", "chart"), java.util.Map.entry("session_stats", "clock"), java.util.Map.entry("waypoint_distance", "pin"),
            java.util.Map.entry("dimension", "layers"), java.util.Map.entry("biome", "mountain"), java.util.Map.entry("time_info", "clock"),
            java.util.Map.entry("environment", "sun"), java.util.Map.entry("world_info", "globe"), java.util.Map.entry("performance", "cpu"),
            java.util.Map.entry("memory", "cpu"), java.util.Map.entry("diag_overlay", "bug"));

    /** Icon shown by text elements (null for none or when disabled in settings). */
    public String icon() { return b(ModuleDef.H_ICON) ? ICONS.get(def.id) : null; }

    public Anchor anchor() {
        try { return Anchor.valueOf(s(ModuleDef.H_ANCHOR)); } catch (IllegalArgumentException e) { return Anchor.TOP_LEFT; }
    }

    public int padding() { return i(ModuleDef.H_PADDING); }
    public float textScale() { return (float) n(ModuleDef.H_TEXT_SCALE); }
    public int textColor() { return color(ModuleDef.H_COLOR); }
    public boolean shadow() { return b(ModuleDef.H_SHADOW); }
    public boolean compact() { return b(ModuleDef.H_COMPACT); }
    public String align() { return s(ModuleDef.H_ALIGN); }

    public int labelColor() { return b(ModuleDef.H_ACCENT) ? lock.theme.accent() : dev.lock.client.core.ThemeManager.withAlpha(textColor(), 0xA0); }
}
