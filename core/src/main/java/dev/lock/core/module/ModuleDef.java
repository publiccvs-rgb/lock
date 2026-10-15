package dev.lock.core.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static description of a module: identity, settings schema and compatibility.
 * Shared by the launcher (to edit configuration offline) and the in-game runtime
 * (which binds behaviour to the module by {@link #id}).
 */
public final class ModuleDef {
    public enum Kind {
        /** Normal module with an on/off state. */
        TOGGLE,
        /** Always-on system component (Click GUI, Visibility Manager, ...): settings only. */
        SYSTEM
    }

    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final Kind kind;
    public final boolean defaultEnabled;
    /** True when the module changes game behaviour/options rather than only displaying information. */
    public final boolean changesBehavior;
    /** True when the module draws a positionable HUD element. */
    public final boolean hud;
    /** Visibility group used by the visibility manager. */
    public final String visibilityGroup;
    public final Support support;
    public final String supportNote;
    public final List<String> tags;
    private final Map<String, Setting<?>> settings;

    private ModuleDef(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.description = b.description;
        this.category = b.category;
        this.kind = b.kind;
        this.defaultEnabled = b.defaultEnabled;
        this.changesBehavior = b.changesBehavior;
        this.hud = b.hud;
        this.visibilityGroup = b.visibilityGroup;
        this.support = b.support;
        this.supportNote = b.supportNote;
        this.tags = List.copyOf(b.tags);
        Map<String, Setting<?>> m = new LinkedHashMap<>();
        for (Setting<?> s : b.settings) {
            if (m.put(s.id, s) != null) throw new IllegalStateException(id + ": duplicate setting " + s.id);
        }
        this.settings = Collections.unmodifiableMap(m);
    }

    public Map<String, Setting<?>> settings() { return settings; }

    public Setting<?> setting(String key) { return settings.get(key); }

    public boolean toggleable() { return kind == Kind.TOGGLE; }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase().trim();
        if (name.toLowerCase().contains(q) || id.contains(q) || description.toLowerCase().contains(q)
                || category.label.toLowerCase().contains(q)) return true;
        for (String t : tags) if (t.contains(q)) return true;
        return false;
    }

    public static Builder builder(String id, String name, Category category) { return new Builder(id, name, category); }

    public static final class Builder {
        private final String id, name;
        private final Category category;
        private String description = "";
        private Kind kind = Kind.TOGGLE;
        private boolean defaultEnabled;
        private boolean changesBehavior;
        private boolean hud;
        private String visibilityGroup = "hud";
        private Support support = Support.SUPPORTED;
        private String supportNote = "";
        private final List<String> tags = new ArrayList<>();
        private final List<Setting<?>> settings = new ArrayList<>();
        private String group = "";

        private Builder(String id, String name, Category category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }

        public Builder desc(String d) { description = d; return this; }
        public Builder system() { kind = Kind.SYSTEM; defaultEnabled = true; return this; }
        public Builder enabled() { defaultEnabled = true; return this; }
        public Builder behavior() { changesBehavior = true; return this; }
        public Builder group(String visibilityGroup) { this.visibilityGroup = visibilityGroup; return this; }
        public Builder tags(String... t) { tags.addAll(Arrays.asList(t)); return this; }
        public Builder support(Support s, String note) { support = s; supportNote = note; return this; }
        /** Starts a new settings section; subsequent settings are listed under it. */
        public Builder section(String g) { group = g; return this; }

        public Builder add(Setting<?> s) {
            s.group = group;
            settings.add(s);
            return this;
        }

        public Builder bool(String id, String name, boolean def, String desc) { return add(new Setting.Bool(id, name, desc, def)); }
        public Builder bool(String id, String name, boolean def) { return bool(id, name, def, ""); }
        public Builder num(String id, String name, double def, double min, double max, double step, String desc) { return add(new Setting.Num(id, name, desc, def, min, max, step)); }
        public Builder num(String id, String name, double def, double min, double max, double step) { return num(id, name, def, min, max, step, ""); }
        public Builder color(String id, String name, int def, String desc) { return add(new Setting.Color(id, name, desc, def)); }
        public Builder color(String id, String name, int def) { return color(id, name, def, ""); }
        public Builder choice(String id, String name, String def, String desc, String... options) { return add(new Setting.Choice(id, name, desc, def, List.of(options))); }
        public Builder text(String id, String name, String def, int max, String desc) { return add(new Setting.Text(id, name, desc, def, max)); }
        public Builder key(String id, String name, int def, String desc) { return add(new Setting.Key(id, name, desc, def)); }

        /** Toggle keybind shared by all toggleable modules. */
        public Builder toggleKey(int def) { return key(KEYBIND, "Toggle key", def, "Toggles this module in game."); }

        /**
         * Adds the standard, independently configurable HUD properties: position, scale, alignment,
         * colours, opacity, background, border, spacing, compact mode and local visibility.
         */
        public Builder hudLayout(Anchor anchor, int x, int y, boolean background) {
            hud = true;
            String prev = group;
            group = "Layout";
            choice(H_ANCHOR, "Anchor", anchor.name(), "Screen corner/edge the position is relative to.", names(Anchor.values()));
            num(H_X, "X offset", x, -4000, 4000, 1, "Horizontal offset from the anchor, in GUI pixels.");
            num(H_Y, "Y offset", y, -4000, 4000, 1, "Vertical offset from the anchor, in GUI pixels.");
            num(H_SCALE, "Scale", 1.0, 0.5, 3.0, 0.05, "Element scale (multiplied by the global HUD scale).");
            choice(H_ALIGN, "Alignment", "Left", "Text alignment inside the element.", "Left", "Center", "Right");
            group = "Appearance";
            num(H_TEXT_SCALE, "Text size", 1.0, 0.5, 2.0, 0.05, "Text size relative to the element scale.");
            color(H_COLOR, "Text colour", 0xFFFFFFFF, "Primary text colour.");
            bool(H_ACCENT, "Use theme accent", false, "Use the theme accent for labels.");
            num(H_OPACITY, "Opacity", 1.0, 0.1, 1.0, 0.05, "Opacity of the whole element.");
            bool(H_SHADOW, "Text shadow", true);
            bool(H_BG, "Background", background);
            color(H_BG_COLOR, "Background colour", 0x80101114);
            bool(H_BORDER, "Border", false);
            color(H_BORDER_COLOR, "Border colour", 0x60FFFFFF);
            num(H_PADDING, "Spacing", 3, 0, 12, 1, "Inner padding and line spacing.");
            bool(H_COMPACT, "Compact", false, "Shorter labels and tighter layout.");
            group = "Visibility";
            bool(H_LOCAL, "Show locally", true, "Draw this element on your screen.");
            group = prev;
            return this;
        }

        public ModuleDef build() {
            if (kind == Kind.TOGGLE && settings.stream().noneMatch(s -> s.id.equals(KEYBIND))) {
                String prev = group;
                group = "Controls";
                toggleKey(Keys.NONE);
                group = prev;
            }
            return new ModuleDef(this);
        }
    }

    private static String[] names(Enum<?>[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i].name();
        return out;
    }

    public static final String KEYBIND = "key";
    public static final String H_ANCHOR = "hud.anchor", H_X = "hud.x", H_Y = "hud.y", H_SCALE = "hud.scale",
            H_ALIGN = "hud.align", H_TEXT_SCALE = "hud.textScale", H_COLOR = "hud.color", H_ACCENT = "hud.accent",
            H_OPACITY = "hud.opacity", H_SHADOW = "hud.shadow", H_BG = "hud.bg", H_BG_COLOR = "hud.bgColor",
            H_BORDER = "hud.border", H_BORDER_COLOR = "hud.borderColor", H_PADDING = "hud.padding",
            H_COMPACT = "hud.compact", H_LOCAL = "hud.local";
}
