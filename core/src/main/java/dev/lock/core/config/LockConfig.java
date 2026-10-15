package dev.lock.core.config;

import dev.lock.core.json.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Root of Lock's persisted configuration. */
public final class LockConfig {
    public static final int SCHEMA = 1;

    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private String active = Profile.DEFAULT;
    public final List<Waypoint> waypoints = new ArrayList<>();
    public String selectedWaypoint = "";
    public final Set<String> favorites = new LinkedHashSet<>();
    /** Saved HUD layouts: layout name -> module id -> {anchor, x, y, scale}. */
    public final Map<String, Map<String, Object>> layouts = new LinkedHashMap<>();
    /** In-game GUI state (window positions, collapsed panels), opaque to the launcher. */
    public final Map<String, Object> gui = new LinkedHashMap<>();
    /** Launcher preferences (Minecraft directory, ...), opaque to the runtime. */
    public final Map<String, Object> launcher = new LinkedHashMap<>();
    /** Anything from a newer schema that we do not understand, preserved on save. */
    private final Map<String, Object> unknown = new LinkedHashMap<>();

    public static LockConfig defaults() {
        LockConfig c = new LockConfig();
        for (String n : Profile.BUILT_IN) c.profiles.put(n, Profile.create(n));
        return c;
    }

    public Profile active() {
        Profile p = profiles.get(active);
        if (p == null) {
            active = profiles.containsKey(Profile.DEFAULT) ? Profile.DEFAULT : profiles.keySet().iterator().next();
            p = profiles.get(active);
        }
        return p;
    }

    public String activeName() { return active().name(); }

    public Map<String, Profile> profiles() { return profiles; }

    public boolean switchTo(String name) {
        if (!profiles.containsKey(name)) return false;
        active = name;
        return true;
    }

    public static String validateName(String name) {
        if (name == null) return "Name is empty.";
        String n = name.trim();
        if (n.isEmpty()) return "Name is empty.";
        if (n.length() > 32) return "Name is longer than 32 characters.";
        for (char c : n.toCharArray()) if (c < 0x20 || "\\/:*?\"<>|".indexOf(c) >= 0) return "Name contains '" + c + "'.";
        return null;
    }

    /** Creates a new profile from defaults. Returns an error message or null. */
    public String create(String name) {
        String err = checkNew(name);
        if (err != null) return err;
        profiles.put(name.trim(), new Profile(name.trim()));
        return null;
    }

    public String duplicate(String source, String name) {
        Profile src = profiles.get(source);
        if (src == null) return "No profile named '" + source + "'.";
        String err = checkNew(name);
        if (err != null) return err;
        profiles.put(name.trim(), src.copy(name.trim()));
        return null;
    }

    public String rename(String from, String to) {
        Profile p = profiles.get(from);
        if (p == null) return "No profile named '" + from + "'.";
        if (p.builtIn()) return "Built-in profiles cannot be renamed; duplicate it instead.";
        String err = checkNew(to);
        if (err != null) return err;
        Map<String, Profile> copy = new LinkedHashMap<>(profiles);
        profiles.clear();
        for (Map.Entry<String, Profile> e : copy.entrySet()) {
            if (e.getKey().equals(from)) {
                p.rename(to.trim());
                profiles.put(to.trim(), p);
            } else profiles.put(e.getKey(), e.getValue());
        }
        if (active.equals(from)) active = to.trim();
        return null;
    }

    public String delete(String name) {
        Profile p = profiles.get(name);
        if (p == null) return "No profile named '" + name + "'.";
        if (p.builtIn()) return "Built-in profiles cannot be deleted; reset it instead.";
        profiles.remove(name);
        if (active.equals(name)) active = Profile.DEFAULT;
        return null;
    }

    /** Adds an imported profile, renaming it if the name is taken. Returns the name used. */
    public String addImported(Profile p) {
        String base = p.name(), n = base;
        int i = 2;
        while (profiles.containsKey(n)) n = base + " (" + i++ + ")";
        Profile copy = p.copy(n);
        profiles.put(n, copy);
        return n;
    }

    private String checkNew(String name) {
        String err = validateName(name);
        if (err != null) return err;
        if (profiles.containsKey(name.trim())) return "A profile named '" + name.trim() + "' already exists.";
        return null;
    }

    public List<Waypoint> waypointsIn(String scope, String dimension) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : waypoints)
            if ((scope == null || w.scope.equals(scope) || w.scope.equals("*")) && (dimension == null || w.dimension.equals(dimension)))
                out.add(w);
        return out;
    }

    public Waypoint waypoint(String id) {
        for (Waypoint w : waypoints) if (w.id.equals(id)) return w;
        return null;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>(unknown);
        m.put("schema", SCHEMA);
        m.put("activeProfile", active);
        Map<String, Object> ps = new LinkedHashMap<>();
        for (Profile p : profiles.values()) ps.put(p.name(), p.toJson());
        m.put("profiles", ps);
        List<Object> ws = new ArrayList<>();
        for (Waypoint w : waypoints) ws.add(w.toJson());
        m.put("waypoints", ws);
        m.put("selectedWaypoint", selectedWaypoint);
        m.put("favorites", new ArrayList<>(favorites));
        m.put("layouts", layouts);
        m.put("launcher", launcher);
        m.put("gui", gui);
        return m;
    }

    public static LockConfig fromJson(Map<String, Object> m, List<String> problems) {
        LockConfig c = new LockConfig();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            switch (e.getKey()) {
                case "schema", "activeProfile", "profiles", "waypoints", "selectedWaypoint", "favorites", "layouts", "launcher", "gui" -> { }
                default -> c.unknown.put(e.getKey(), e.getValue());
            }
        }
        Map<String, Object> ps = JsonUtil.obj(m.get("profiles"));
        if (ps != null) {
            for (Map.Entry<String, Object> e : ps.entrySet()) {
                if (validateName(e.getKey()) != null) {
                    problems.add("skipped profile with invalid name '" + e.getKey() + "'");
                    continue;
                }
                c.profiles.put(e.getKey(), Profile.fromJson(e.getKey(), JsonUtil.obj(e.getValue()), problems));
            }
        } else problems.add("no profiles section; defaults created");
        for (String n : Profile.BUILT_IN) if (!c.profiles.containsKey(n)) c.profiles.put(n, Profile.create(n));
        c.active = JsonUtil.str(m, "activeProfile", Profile.DEFAULT);
        if (!c.profiles.containsKey(c.active)) {
            problems.add("active profile '" + c.active + "' missing; using Default");
            c.active = Profile.DEFAULT;
        }
        List<Object> ws = JsonUtil.arr(m.get("waypoints"));
        if (ws != null) for (Object o : ws) {
            Waypoint w = Waypoint.fromJson(JsonUtil.obj(o));
            if (w != null) c.waypoints.add(w);
            else problems.add("skipped invalid waypoint " + o);
        }
        c.selectedWaypoint = JsonUtil.str(m, "selectedWaypoint", "");
        List<Object> fav = JsonUtil.arr(m.get("favorites"));
        if (fav != null) for (Object o : fav) if (o instanceof String s) c.favorites.add(s);
        Map<String, Object> lay = JsonUtil.obj(m.get("layouts"));
        if (lay != null) for (Map.Entry<String, Object> e : lay.entrySet()) {
            Map<String, Object> l = JsonUtil.obj(e.getValue());
            if (l != null) c.layouts.put(e.getKey(), l);
        }
        Map<String, Object> gu = JsonUtil.obj(m.get("gui"));
        if (gu != null) c.gui.putAll(gu);
        Map<String, Object> la = JsonUtil.obj(m.get("launcher"));
        if (la != null) c.launcher.putAll(la);
        return c;
    }
}
