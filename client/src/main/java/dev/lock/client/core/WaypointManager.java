package dev.lock.client.core;

import dev.lock.client.LockClient;
import dev.lock.core.config.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/** Waypoint operations on the persisted list, scoped to the current world/server and dimension. */
public final class WaypointManager {
    private final LockClient lock;
    private final Minecraft mc = Minecraft.getInstance();

    public WaypointManager(LockClient lock) { this.lock = lock; }

    public String scope() {
        return lock.config.state("navigation").bool("perServer") ? GameInfo.scope() : "*";
    }

    /** Waypoints for this world/server in the current dimension. */
    public List<Waypoint> here() { return lock.config.config().waypointsIn(scope(), GameInfo.dimension()); }

    /** Waypoints for this world/server in all dimensions. */
    public List<Waypoint> inScope() { return lock.config.config().waypointsIn(scope(), null); }

    public Waypoint selected() {
        Waypoint w = lock.config.config().waypoint(lock.config.config().selectedWaypoint);
        if (w == null) return null;
        String sc = scope();
        return (w.scope.equals(sc) || w.scope.equals("*") || sc.equals("*")) ? w : null;
    }

    public void select(Waypoint w) {
        lock.config.config().selectedWaypoint = w == null ? "" : w.id;
        lock.config.changed();
    }

    public Waypoint addHere(String name) {
        LocalPlayer p = mc.player;
        if (p == null) return null;
        if (name == null || name.isBlank()) name = "Waypoint " + (inScope().size() + 1);
        Waypoint w = new Waypoint(name, p.getBlockX(), p.getBlockY(), p.getBlockZ(), GameInfo.dimension(), scope(),
                lock.config.state("waypoints").color("defaultColor"));
        lock.config.config().waypoints.add(w);
        lock.config.changed();
        lock.notifications.system("Waypoint", "Added " + w.name);
        return w;
    }

    public void delete(Waypoint w) {
        lock.config.config().waypoints.remove(w);
        if (w.id.equals(lock.config.config().selectedWaypoint)) lock.config.config().selectedWaypoint = "";
        lock.config.changed();
    }

    public void cycle() {
        List<Waypoint> list = here();
        if (list.isEmpty()) { lock.notifications.info("Waypoints", "No waypoints in this dimension"); return; }
        Waypoint cur = selected();
        int i = cur == null ? -1 : list.indexOf(cur);
        Waypoint next = list.get((i + 1) % list.size());
        select(next);
        lock.notifications.system("Waypoint", "Selected " + next.name);
    }

    /** Distance from the player, or -1 if not in the same dimension. */
    public double distance(Waypoint w) {
        LocalPlayer p = mc.player;
        if (p == null || !w.dimension.equals(GameInfo.dimension())) return -1;
        return w.distanceTo(p.getX(), p.getY(), p.getZ());
    }
}
