package dev.lock.core.config;

import dev.lock.core.module.ModuleDef;
import dev.lock.core.module.Setting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-profile state of one module: enabled flag plus validated setting values. */
public final class ModuleState {
    public final ModuleDef def;
    private boolean enabled;
    private final Map<String, Object> values = new LinkedHashMap<>();

    public ModuleState(ModuleDef def) {
        this.def = def;
        reset();
    }

    public void reset() {
        enabled = def.defaultEnabled;
        values.clear();
        for (Setting<?> s : def.settings().values()) values.put(s.id, s.defaultValue);
    }

    public boolean enabled() { return enabled || !def.toggleable(); }

    public void setEnabled(boolean e) { if (def.toggleable()) enabled = e; }

    @SuppressWarnings("unchecked")
    public <T> T get(Setting<T> s) {
        Object v = values.get(s.id);
        return v == null ? s.defaultValue : (T) v;
    }

    public Object raw(String key) {
        Setting<?> s = def.setting(key);
        if (s == null) return null;
        Object v = values.get(key);
        return v == null ? s.defaultValue : v;
    }

    public boolean bool(String key) { return raw(key) instanceof Boolean b && b; }

    public double num(String key) { return raw(key) instanceof Number n ? n.doubleValue() : 0; }

    public int integer(String key) { return (int) Math.round(num(key)); }

    public int color(String key) { return raw(key) instanceof Integer i ? i : 0xFFFFFFFF; }

    public String str(String key) { return raw(key) instanceof String s ? s : ""; }

    public int key(String key) { return raw(key) instanceof Integer i ? i : -1; }

    /** Sets a value after validating it. Returns false when the key is unknown. */
    public boolean set(String key, Object raw) {
        Setting<?> s = def.setting(key);
        if (s == null) return false;
        values.put(key, s.validate(raw));
        return true;
    }

    public void copyFrom(ModuleState o) {
        enabled = o.enabled;
        values.clear();
        values.putAll(o.values);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (def.toggleable()) m.put("enabled", enabled);
        Map<String, Object> sv = new LinkedHashMap<>();
        for (Setting s : def.settings().values()) sv.put(s.id, s.toJson(get(s)));
        m.put("settings", sv);
        return m;
    }

    /** Loads from JSON, validating every value; unknown keys are ignored, missing keys keep defaults. */
    public void fromJson(Map<String, Object> m, java.util.List<String> problems) {
        reset();
        if (m == null) return;
        Object e = m.get("enabled");
        if (e instanceof Boolean b) enabled = b;
        else if (e != null) problems.add(def.id + ".enabled: not a boolean");
        Object s = m.get("settings");
        if (s instanceof Map<?, ?> sm) {
            for (Map.Entry<?, ?> en : sm.entrySet()) {
                String k = String.valueOf(en.getKey());
                Setting<?> setting = def.setting(k);
                if (setting == null) continue;
                Object validated = setting.validate(en.getValue());
                if (!same(en.getValue(), setting.toJsonUnchecked(validated))) {
                    problems.add(def.id + "." + k + ": value '" + en.getValue() + "' adjusted to '" + setting.toJsonUnchecked(validated) + "'");
                }
                values.put(k, validated);
            }
        }
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return Math.abs(x.doubleValue() - y.doubleValue()) < 1e-6;
        if (a instanceof String x && b instanceof String y) return x.equalsIgnoreCase(y);
        return java.util.Objects.equals(a, b);
    }
}
