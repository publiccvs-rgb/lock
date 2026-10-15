package dev.lock.core.config;

import dev.lock.core.json.JsonUtil;
import dev.lock.core.module.Setting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A saved location. {@link #scope} identifies the world or server it belongs to. */
public final class Waypoint {
    public String id;
    public String name;
    public int x, y, z;
    public String dimension;
    public String scope;
    public int color;
    public boolean visible = true;
    public long created;

    public Waypoint(String name, int x, int y, int z, String dimension, String scope, int color) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.scope = scope;
        this.color = color;
        this.created = System.currentTimeMillis();
    }

    public double distanceTo(double px, double py, double pz) {
        double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("dimension", dimension);
        m.put("scope", scope);
        m.put("color", Setting.Color.format(color));
        m.put("visible", visible);
        m.put("created", created);
        return m;
    }

    public static Waypoint fromJson(Map<String, Object> m) {
        if (m == null) return null;
        String name = JsonUtil.str(m, "name", null);
        if (name == null || !(m.get("x") instanceof Number)) return null;
        Integer color = m.get("color") instanceof String cs ? Setting.Color.parse(cs) : null;
        Waypoint w = new Waypoint(name.length() > 48 ? name.substring(0, 48) : name,
                clampCoord(JsonUtil.num(m, "x", 0)), (int) Math.max(-2048, Math.min(4096, JsonUtil.num(m, "y", 64))),
                clampCoord(JsonUtil.num(m, "z", 0)), JsonUtil.str(m, "dimension", "minecraft:overworld"),
                JsonUtil.str(m, "scope", "*"), color == null ? 0xFF6EA8FE : color);
        w.id = JsonUtil.str(m, "id", w.id);
        w.visible = JsonUtil.bool(m, "visible", true);
        w.created = (long) JsonUtil.num(m, "created", w.created);
        return w;
    }

    private static int clampCoord(double v) { return (int) Math.max(-30_000_000, Math.min(30_000_000, v)); }
}
