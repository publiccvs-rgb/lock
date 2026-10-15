package lock.module;

import lock.basefinder.*;
import lock.render.Colors;
import lock.render.Draw;
import lock.render.Projection;
import lock.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Finds chunks that show signs of player activity. Scanning is incremental: the game thread copies a chunk into a
 * snapshot (a couple of milliseconds, under a per-tick budget) and worker threads score it. Scores are posteriors
 * from {@link Engine}; neighbouring chunks reinforce each other through {@link Sites}.
 */
public class BaseFinder extends Module {
    public final ModeSetting signals = add(new ModeSetting("Signals", "All", "All", "Structures", "Dug Space", "Contradiction", "Entities"))
        .d("Which signal families count toward a chunk's score. Contradiction compares blocks against the server's light and heightmap.");
    public final ColorSetting livePlayer = add(new ColorSetting("Live Player Color", 0x4F8CFF)).d("Color for a chunk with a live player in it.");
    public final NumSetting minScore = add(new NumSetting("Min Score", 0, 0, 100, 1)).d("Chunks at or above this chance are flagged. 0 picks a floor automatically.");
    public final ModeSetting dug = add(new ModeSetting("Dug Space", "All", "All", "Tunnels", "Shafts", "Stairs", "Off"))
        .d("Detects player-dug tunnels, shafts and staircases by their straight rectangular cross-section.");
    public final ModeSetting alerts = add(new ModeSetting("Alerts", "Toast", "Off", "Toast", "Toast + Chat"))
        .d("Off silences everything; Toast shows a notification and plays a chime; Toast + Chat also prints the coordinates.");
    public final NumSetting cooldown = add(new NumSetting("Alert Cooldown", 5, 1, 120, 1)).d("Seconds before the same site may alert again.");
    public final BoolSetting export = add(new BoolSetting("Export Snapshot", false)).d("Writes a JSON dump of every detection and the baseline to ~/.lock, then turns back off.");
    public final BoolSetting render = add(new BoolSetting("Render", true)).d("Draws a flat tile per flagged chunk at the plane level below. Off leaves the dots and the target list.");
    public final BoolSetting dots = add(new BoolSetting("Dots", true)).d("Draws a dot on each flagged chunk. Hover one to see its full score breakdown.");
    public final BoolSetting targets = add(new BoolSetting("Targets", true)).d("A ranked list of the best chunks to check, best first.");
    public final NumSetting targetCount = add(new NumSetting("Target Count", 5, 1, 12, 1)).d("Rows in the target list.");
    public final NumSetting panelX = add(new NumSetting("Panel X", -1, -1, 400, 1)).d("Left edge of the target list in HUD units. -1 uses its default position.");
    public final NumSetting panelY = add(new NumSetting("Panel Y", -1, -1, 300, 1)).d("Top edge of the target list in HUD units. -1 uses its default position.");
    public final NumSetting renderDistance = add(new NumSetting("Render Distance", 256, 48, 768, 16)).d("Hides findings further away than this (blocks).");
    public final ColorSetting high = add(new ColorSetting("High Chance Color", 0x4DFF6B)).d("Color at 100% chance. Scores in between blend toward the low end.");
    public final ColorSetting low = add(new ColorSetting("Low Chance Color", 0xFF3B30)).d("Color at 0% chance. Below the floor there is no marker at all.");
    public final ModeSetting amethyst = add(new ModeSetting("Amethyst ESP", "Off", "Off", "Grown", "All")).d("Grown shows only full clusters and large buds; All shows every amethyst block.");
    public final ColorSetting amethystColor = add(new ColorSetting("Amethyst Color", 0xFF6BD6)).d("Color of the amethyst ESP markers.");
    public final NumSetting threads = add(new NumSetting("Scan Threads", 2, 1, 8, 1)).d("Background scan thread count. More clears a backlog faster but uses more CPU.");
    public final ModeSetting plane = add(new ModeSetting("Plane Level", "Band Middle", "Band Top", "Band Middle", "Fixed Y", "Bedrock"))
        .d("Band Top draws at the top of the evidence, Band Middle in the middle, Fixed Y at the Plane Y below, Bedrock one block up from the world floor.");
    public final NumSetting planeY = add(new NumSetting("Plane Y", -59, -64, 320, 1)).d("Fixed height every tile is drawn at.");
    public final BoolSetting shield = add(new BoolSetting("Integrity Shield", true)).d("Cross-checks blocks against light and heightmaps so server-side block rewriting cannot hide a chunk.");
    public final BoolSetting adaptive = add(new BoolSetting("Adaptive Baseline", true)).d("Learns what is normal on this server and fades signals that keep showing up in untouched terrain.");

    // ---- state ----
    private final Model model = new Model();
    private final Map<Long, Verdict> results = new ConcurrentHashMap<>();
    private final Map<Long, Integer> firstSeen = new HashMap<>(), lastScan = new HashMap<>(), scanCount = new HashMap<>();
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private ExecutorService pool;
    private int poolSize;
    private ClientLevel lastLevel;
    private int tick;
    private volatile boolean dirty;
    private final Map<Long, Integer> alertedAt = new HashMap<>();

    /** Linked, ranked results for the UI (rebuilt on the game thread). */
    public volatile List<Verdict> shown = List.of();
    public volatile List<Sites.Site> sites = List.of();
    public volatile Verdict hover;
    public volatile float[] hoverScreen;
    public volatile int scannedChunks, queued;

    public BaseFinder() { super("BaseFinder", "Marks chunks with signs of player activity", Category.WORLD, false); }

    @Override public void onDisable() { results.clear(); shown = List.of(); hover = null; stopPool(); }

    private void stopPool() { if (pool != null) { pool.shutdownNow(); pool = null; } }

    private Engine.Options options() {
        Engine.Options o = new Engine.Options();
        String sg = signals.get();
        o.built = sg.equals("All") || sg.equals("Structures");
        o.excavation = (sg.equals("All") || sg.equals("Dug Space")) && !dug.is("Off");
        o.traces = (sg.equals("All") || sg.equals("Contradiction")) && shield.get();
        o.life = sg.equals("All") || sg.equals("Entities");
        o.horizontalTunnels = dug.is("All") || dug.is("Tunnels");
        o.shafts = dug.is("All") || dug.is("Shafts");
        o.stairs = dug.is("All") || dug.is("Stairs");
        return o;
    }

    public double minShown() { return minScore.i() == 0 ? 0.25 : minScore.i() / 100.0; }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        tick++;
        if (level != lastLevel) {
            lastLevel = level;
            results.clear(); firstSeen.clear(); lastScan.clear(); scanCount.clear(); pending.clear(); alertedAt.clear();
        }
        if (pool == null || poolSize != threads.i()) {
            stopPool();
            poolSize = threads.i();
            pool = Executors.newFixedThreadPool(poolSize, r -> { Thread t = new Thread(r, "lock-finder"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t; });
        }

        scan(mc, level);

        if (dirty && tick % 8 == 0) {
            dirty = false;
            relink(mc);
        }
        if (export.get()) { export.set(false); export(); }
        queued = pending.size();
    }

    private void scan(Minecraft mc, ClientLevel level) {
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        int radius = Math.min(level.getChunkSource().getLoadedChunksCount() > 0 ? 40 : 0, (int) Math.ceil(renderDistance.get() / 16.0));
        long t0 = System.nanoTime();
        Map<Long, List<ChunkSnapshot.Ent>> ents = null;
        Engine.Options opt = options();
        boolean learn = adaptive.get();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long key = Sites.key(cx, cz);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) { firstSeen.remove(key); continue; }
                    Integer seen = firstSeen.putIfAbsent(key, tick);
                    int age = tick - (seen == null ? tick : seen);
                    if (age < 20 || pending.contains(key)) continue;
                    int n = scanCount.getOrDefault(key, 0);
                    int since = tick - lastScan.getOrDefault(key, -100000);
                    boolean due = n == 0 || (n == 1 && since > 160 && ring <= 12) || (n >= 2 && since > 1200 && ring <= 6);
                    if (!due) continue;
                    if (System.nanoTime() - t0 > 3_000_000L) return;      // per-tick budget
                    if (ents == null) ents = McScan.bucketEntities(level);
                    ChunkSnapshot snap = McScan.snapshot(level, chunk, ents.get(key));
                    lastScan.put(key, tick);
                    scanCount.put(key, n + 1);
                    pending.add(key);
                    final Model m = learn ? model : new Model();
                    pool.execute(() -> {
                        try {
                            Verdict v = Engine.analyze(snap, m, opt);
                            results.put(key, v);
                            dirty = true;
                        } catch (Throwable t) {
                            t.printStackTrace();
                        } finally {
                            pending.remove(key);
                        }
                    });
                }
            }
        }
    }

    private void relink(Minecraft mc) {
        Map<Long, Verdict> copy = new HashMap<>(results);
        List<Sites.Site> s = Sites.link(copy);
        double min = minShown();
        double maxDist = renderDistance.get();
        double px = mc.player.getX(), pz = mc.player.getZ();
        List<Verdict> list = new ArrayList<>();
        for (Verdict v : copy.values()) {
            double d = Math.hypot(v.cx * 16 + 8 - px, v.cz * 16 + 8 - pz);
            if (v.p >= min && d <= maxDist) list.add(v);
        }
        list.sort((a, b) -> Double.compare(b.p, a.p));
        shown = list;
        sites = s;
        scannedChunks = copy.size();
        alert(mc, list);
    }

    private void alert(Minecraft mc, List<Verdict> list) {
        if (alerts.is("Off")) return;
        double at = minScore.i() == 0 ? 0.6 : minShown();
        for (Verdict v : list) {
            if (v.p < at) break;
            long k = v.siteId >= 0 ? (1L << 40) | v.siteId : Sites.key(v.cx, v.cz);
            Integer last = alertedAt.get(k);
            if (last != null && tick - last < cooldown.i() * 20) continue;
            alertedAt.put(k, tick);
            String where = (v.cx * 16 + 8) + ", " + (v.hasEvidenceRange() ? (v.evidenceMinY + v.evidenceMaxY) / 2 : "?") + ", " + (v.cz * 16 + 8);
            String title = v.type.label + " " + v.pct() + "%";
            net.minecraft.client.gui.components.toasts.SystemToast.add(mc.getToastManager(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("BaseFinder - " + title), Component.literal(where));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3f));
            if (alerts.is("Toast + Chat") && mc.player != null) mc.player.displayClientMessage(Component.literal("[BaseFinder] " + title + " at " + where), false);
            return;   // one alert per pass
        }
    }

    private void export() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".lock");
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder("{\"chunks\":[");
            boolean first = true;
            for (Verdict v : results.values()) {
                if (v.p < 0.05) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"cx\":").append(v.cx).append(",\"cz\":").append(v.cz).append(",\"p\":").append(String.format(Locale.ROOT, "%.4f", v.p))
                    .append(",\"type\":\"").append(v.type.label).append("\",\"points\":").append(v.points).append(",\"reasons\":[");
                for (int i = 0; i < v.reasons.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(v.reasons.get(i).text().replace("\"", "'")).append('"');
                }
                sb.append("]}");
            }
            sb.append("],\"baseline\":{");
            boolean f2 = true;
            for (var e : model.dump().entrySet()) {
                if (!f2) sb.append(',');
                f2 = false;
                sb.append('"').append(e.getKey()).append("\":").append(String.format(Locale.ROOT, "%.5f", e.getValue()[0]));
            }
            sb.append("}}");
            try (Writer w = Files.newBufferedWriter(dir.resolve("basefinder-snapshot.json"))) { w.write(sb.toString()); }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ world rendering

    private int planeY(Verdict v) {
        return switch (plane.get()) {
            case "Band Top" -> v.hasEvidenceRange() ? v.evidenceMaxY + 1 : planeY.i();
            case "Band Middle" -> v.hasEvidenceRange() ? (v.evidenceMinY + v.evidenceMaxY) / 2 : planeY.i();
            case "Bedrock" -> v.minY + 1;
            default -> planeY.i();
        };
    }

    public int colorFor(Verdict v) {
        if (v.livePlayer) return livePlayer.get();
        return Colors.lerp(low.get(), high.get(), (float) Math.min(1.0, v.p));
    }

    public void renderWorld(Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        List<Verdict> list = shown;
        Vec3 look = Projection.look();
        Verdict best = null;
        float[] bestScreen = null;
        double bestScore = Double.MAX_VALUE;
        var win = mc.getWindow();
        float cxs = win.getGuiScaledWidth() / 2f, cys = win.getGuiScaledHeight() / 2f;

        for (Verdict v : list) {
            int rgb = colorFor(v);
            double y = planeY(v) + 0.02;
            double x0 = v.cx * 16 - cam.x, z0 = v.cz * 16 - cam.z, yy = y - cam.y;
            float fade = (float) Math.max(0.35, Math.min(1.0, 0.45 + v.p));
            if (render.get()) {
                Vec3 a = new Vec3(x0, yy, z0), b = new Vec3(x0 + 16, yy, z0), c = new Vec3(x0 + 16, yy, z0 + 16), d = new Vec3(x0, yy, z0 + 16);
                Draw.quad(a, b, c, d, Colors.argb(rgb, 0.05f + 0.08f * (float) v.p));
                int edge = Colors.argb(rgb, 0.55f * fade);
                Draw.line(a, b, edge, 0.9f); Draw.line(b, c, edge, 0.9f); Draw.line(c, d, edge, 0.9f); Draw.line(d, a, edge, 0.9f);
                // corner ticks, a touch brighter
                int tick = Colors.argb(rgb, 0.95f * fade);
                double t = 2.5;
                Vec3[][] corners = {{a, new Vec3(x0 + t, yy, z0), new Vec3(x0, yy, z0 + t)}, {b, new Vec3(x0 + 16 - t, yy, z0), new Vec3(x0 + 16, yy, z0 + t)},
                    {c, new Vec3(x0 + 16 - t, yy, z0 + 16), new Vec3(x0 + 16, yy, z0 + 16 - t)}, {d, new Vec3(x0 + t, yy, z0 + 16), new Vec3(x0, yy, z0 + 16 - t)}};
                for (Vec3[] k : corners) { Draw.line(k[0], k[1], tick, 1.6f); Draw.line(k[0], k[2], tick, 1.6f); }
            }
            if (dots.get() || render.get()) {
                double r = 0.45 + 0.9 * v.p;
                Vec3 cen = new Vec3(x0 + 8, yy, z0 + 8);
                int dc = Colors.argb(rgb, 0.95f);
                Draw.quad(cen.add(-r, 0, -r), cen.add(r, 0, -r), cen.add(r, 0, r), cen.add(-r, 0, r), dc);
                double ring = r * 2.1;
                int rc = Colors.argb(rgb, 0.35f);
                Draw.line(cen.add(-ring, 0, -ring), cen.add(ring, 0, -ring), rc, 0.8f);
                Draw.line(cen.add(ring, 0, -ring), cen.add(ring, 0, ring), rc, 0.8f);
                Draw.line(cen.add(ring, 0, ring), cen.add(-ring, 0, ring), rc, 0.8f);
                Draw.line(cen.add(-ring, 0, ring), cen.add(-ring, 0, -ring), rc, 0.8f);
                // hover candidate: closest to the crosshair on screen
                float[] sp = Projection.toScreen(new Vec3(v.cx * 16 + 8, y, v.cz * 16 + 8), 1.0f);
                if (sp != null) {
                    double dd = Math.hypot(sp[0] - cxs, sp[1] - cys);
                    if (dd < 28 && dd < bestScore) { bestScore = dd; best = v; bestScreen = sp; }
                }
            }
        }
        hover = best;
        hoverScreen = bestScreen;

        if (!amethyst.is("Off")) {
            boolean grownOnly = amethyst.is("Grown");
            int col = amethystColor.get();
            for (Verdict v : list.isEmpty() ? List.<Verdict>of() : list) {
                for (int[] m : v.amethyst) {
                    if (grownOnly && m[3] == 0) continue;
                    double ax = m[0] - cam.x, ay = m[1] - cam.y, az = m[2] - cam.z;
                    net.minecraft.world.phys.AABB bb = new net.minecraft.world.phys.AABB(ax + 0.2, ay, az + 0.2, ax + 0.8, ay + 0.9, az + 0.8);
                    Draw.box(bb, Colors.argb(col, 0.9f), 0.9f, true);
                }
            }
        }
    }
}
