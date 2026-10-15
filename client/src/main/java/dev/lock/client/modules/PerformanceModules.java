package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.module.TextHud;
import dev.lock.client.render.Gfx;
import dev.lock.core.util.Log;
import dev.lock.core.util.LockPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public final class PerformanceModules {
    private PerformanceModules() {}

    public static final class Performance extends TextHud {
        public Performance(LockClient l) { super(l, "performance"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            out.add(line("Avg FPS", String.format(Locale.ROOT, "%.0f", lock.stats.avgFps())));
            if (b("low")) out.add(line("1% low", String.format(Locale.ROOT, "%.0f", lock.stats.lowFps())));
            if (b("frametime")) out.add(line("Frame", String.format(Locale.ROOT, "%.2f ms", lock.stats.frameMs())));
        }
    }

    public static final class Memory extends TextHud {
        public Memory(LockClient l) { super(l, "memory"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) {
            Runtime r = Runtime.getRuntime();
            long used = (r.totalMemory() - r.freeMemory()) / 1048576, max = r.maxMemory() / 1048576;
            out.add(line("Mem", used + "/" + max + " MB" + (b("percent") ? " (" + used * 100 / Math.max(1, max) + "%)" : "")));
        }
    }

    public static final class FrameGraph extends HudModule {
        public FrameGraph(LockClient l) { super(l, "frame_graph"); }

        @Override public int[] size(Gfx gfx) { return new int[]{i("width"), i("height") + 10}; }

        @Override public void draw(Gfx gfx, int w, int h) {
            int gh = i("height");
            float target = (float) n("targetMs");
            float[] ms = lock.stats.recentFrameMs(w);
            gfx.fill(0, gh - 1, w, gh, 0x40FFFFFF);
            int y60 = gh - Math.round(Math.min(1f, 16.67f / target) * gh);
            gfx.fill(0, y60, w, y60 + 1, 0x306EE7A0);
            int x0 = w - ms.length;
            for (int i = 0; i < ms.length; i++) {
                int bh = Math.max(1, Math.min(gh, Math.round(ms[i] / target * gh)));
                int c = ms[i] <= 16.7f ? 0xC06EE7A0 : ms[i] <= 33.4f ? 0xC0F5D76E : 0xC0F2575F;
                gfx.fill(x0 + i, gh - bh, x0 + i + 1, gh, c);
            }
            gfx.textScaled(String.format(Locale.ROOT, "%.1f ms", lock.stats.frameMs()), 0, gh + 2, 0.75f, textColor(), shadow());
        }
    }

    public static final class DiagOverlay extends TextHud {
        public DiagOverlay(LockClient l) { super(l, "diag_overlay"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) {
            out.add(line("Lock", lock.agent.lockVersion() + " · MC " + lock.agent.minecraftVersion(), lock.theme.accent()));
            out.add(line("Hooks", lock.diagnostics.hooksApplied() + "/" + lock.agent.hooks().size()));
            out.add(line("HUD cost", String.format(Locale.ROOT, "%.0f µs/frame", lock.hud.averageCostMicros())));
            out.add(line("Errors", Integer.toString(Log.errorCount()), Log.errorCount() > 0 ? 0xFFF2575F : 0));
            out.add(line("Faulted", lock.modules.faultedIds().isEmpty() ? "none" : String.join(", ", lock.modules.faultedIds())));
            out.add(line("Profile", lock.config.profile().name() + " · " + lock.visibility.mode()));
        }
    }

    /** Appends FPS, frame time and memory to logs/perf-DATE.csv at a fixed interval. */
    public static final class PerfLog extends ClientModule {
        private BufferedWriter out;
        private long last;
        private Path file;

        public PerfLog(LockClient l) { super(l, "perf_log"); }

        @Override protected void onEnable() {
            try {
                file = LockPaths.logs().resolve("perf-" + LocalDate.now() + ".csv");
                Files.createDirectories(file.getParent());
                boolean fresh = !Files.exists(file);
                out = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (fresh) out.write("time,fps,avg_fps,low_fps,frame_ms,mem_mb,lock_hud_us\n");
                lock.notifications.info("Performance log", file.getFileName().toString());
            } catch (IOException e) {
                Log.error("cannot open performance log", e);
                out = null;
            }
        }

        @Override public void tick() {
            if (out == null) return;
            long now = System.currentTimeMillis();
            if (now - last < n("interval") * 1000) return;
            last = now;
            Runtime r = Runtime.getRuntime();
            try {
                out.write(String.format(Locale.ROOT, "%s,%d,%.1f,%.1f,%.2f,%d,%.0f%n", LocalDateTime.now(), mc.getFps(), lock.stats.avgFps(),
                        lock.stats.lowFps(), lock.stats.frameMs(), (r.totalMemory() - r.freeMemory()) / 1048576, lock.hud.averageCostMicros()));
                out.flush();
            } catch (IOException e) {
                Log.error("performance log write failed", e);
            }
        }

        @Override protected void onDisable() {
            try { if (out != null) out.close(); } catch (IOException ignored) { }
            out = null;
        }
    }
}
