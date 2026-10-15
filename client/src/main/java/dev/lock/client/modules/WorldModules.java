package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.core.GameInfo;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.TextHud;
import dev.lock.client.render.Fmt;
import dev.lock.core.config.Waypoint;
import net.minecraft.client.player.LocalPlayer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class WorldModules {
    private WorldModules() {}

    /** Keybinds for the waypoint manager; the list itself is edited in the GUI or launcher. */
    public static final class Waypoints extends ClientModule {
        public Waypoints(LockClient l) { super(l, "waypoints"); }

        @Override public void onBind(String id, boolean pressed) {
            if (!pressed || mc.player == null) return;
            if (id.equals("addKey")) lock.waypoints.select(lock.waypoints.addHere(null));
            else if (id.equals("cycleKey")) lock.waypoints.cycle();
        }
    }

    public static final class DeathWaypoint extends ClientModule {
        private boolean wasDead;

        public DeathWaypoint(LockClient l) { super(l, "death_waypoint"); }

        @Override public void tick() {
            LocalPlayer p = mc.player;
            if (p == null) { wasDead = false; return; }
            boolean dead = p.isDeadOrDying();
            if (dead && !wasDead) {
                List<Waypoint> deaths = new ArrayList<>();
                for (Waypoint w : lock.waypoints.inScope()) if (w.name.startsWith("Death #")) deaths.add(w);
                int keep = i("keep");
                while (deaths.size() >= keep && !deaths.isEmpty()) lock.waypoints.delete(deaths.remove(0));
                int n = 1;
                for (Waypoint w : deaths) {
                    try { n = Math.max(n, Integer.parseInt(w.name.substring(7).trim()) + 1); } catch (NumberFormatException ignored) { }
                }
                Waypoint w = new Waypoint("Death #" + n, p.getBlockX(), p.getBlockY(), p.getBlockZ(), GameInfo.dimension(), lock.waypoints.scope(), 0xFFF2575F);
                lock.config.config().waypoints.add(w);
                lock.waypoints.select(w);
                lock.notifications.system("Death waypoint", p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ());
            }
            wasDead = dead;
        }
    }

    public static final class WaypointDistance extends TextHud {
        public WaypointDistance(LockClient l) { super(l, "waypoint_distance"); refreshMs = 100; }

        @Override protected void lines(List<Line> out, boolean compact) {
            Waypoint w = lock.waypoints.selected();
            LocalPlayer p = mc.player;
            if (w == null || p == null) return;
            double d = lock.waypoints.distance(w);
            String dist = d < 0 ? "other dimension" : Fmt.distance(d) + " m";
            String arrow = "";
            if (b("arrow") && d >= 0) {
                double dx = w.x + 0.5 - p.getX(), dz = w.z + 0.5 - p.getZ();
                double target = Math.toDegrees(Math.atan2(-dx, dz)); // Minecraft yaw of the direction to the waypoint
                double rel = ((target - p.getYRot()) % 360 + 540) % 360 - 180;
                String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
                arrow = arrows[(int) Math.floorMod(Math.round(rel / 45.0), 8)] + " ";
            }
            out.add(line("", arrow + w.name + "  " + dist, w.color | 0xFF000000));
            if (b("coords") && lock.visibility.visible("sensitive")) out.add(line("", w.x + ", " + w.y + ", " + w.z));
        }
    }

    public static final class Dimension extends TextHud {
        public Dimension(LockClient l) { super(l, "dimension"); refreshMs = 1000; }

        @Override protected void lines(List<Line> out, boolean compact) { out.add(line(compact ? "" : "Dimension", GameInfo.dimensionName())); }
    }

    public static final class Biome extends TextHud {
        public Biome(LockClient l) { super(l, "biome"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) { out.add(line(compact ? "" : "Biome", GameInfo.biome())); }
    }

    public static final class TimeInfo extends TextHud {
        public TimeInfo(LockClient l) { super(l, "time_info"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) {
            String t = GameInfo.clock(b("h24"));
            if (b("day")) t += compact ? " d" + GameInfo.day() : "  Day " + GameInfo.day();
            out.add(line(compact ? "" : "Time", t));
            if (b("real")) out.add(line(compact ? "" : "Real", new SimpleDateFormat(b("h24") ? "HH:mm" : "h:mm a").format(new Date())));
        }
    }

    public static final class Environment extends TextHud {
        public Environment(LockClient l) { super(l, "environment"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) {
            if (b("weather")) out.add(line("Weather", GameInfo.weather()));
            if (b("light")) out.add(line("Light", GameInfo.blockLight() + " (sky " + GameInfo.skyLight() + ")"));
            if (b("moon")) out.add(line("Moon", GameInfo.moonPhase()));
            if (b("difficulty")) out.add(line("Difficulty", GameInfo.difficulty()));
        }
    }

    public static final class WorldInfo extends TextHud {
        public WorldInfo(LockClient l) { super(l, "world_info"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (b("position") && p != null) out.add(line("Pos", p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ()));
            if (b("biome")) out.add(line("Biome", GameInfo.biome()));
            if (b("dimension")) out.add(line("Dim", GameInfo.dimensionName()));
            if (b("weather")) out.add(line("Weather", GameInfo.weather()));
        }
    }
}
