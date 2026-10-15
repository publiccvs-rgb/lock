package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.core.NotificationManager;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Fmt;
import dev.lock.client.render.Gfx;
import dev.lock.core.config.Waypoint;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class VisualModules {
    private VisualModules() {}

    /** Custom crosshair drawn at the screen centre in GUI pixels. */
    public static final class Crosshair extends ClientModule {
        public Crosshair(LockClient l) { super(l, "crosshair"); }

        public boolean replacesVanilla() { return true; }

        public void render(Gfx gfx) {
            if (mc.player == null || mc.options.hideGui) return;
            if (b("hideThirdPerson") && !mc.options.getCameraType().isFirstPerson()) return;
            if (mc.screen != null && !(mc.screen instanceof dev.lock.client.gui.LockScreen)) return;
            int cx = gfx.g.guiWidth() / 2, cy = gfx.g.guiHeight() / 2;
            int size = i("size"), t = i("thickness"), gap = i("gap");
            if (b("dynamicGap") && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.003) gap += 2;
            int color = color("color");
            if (b("targetColor") && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) color = color("targetColorValue");
            gfx.alpha = (float) n("opacity");
            boolean outline = b("outline");
            int oc = color("outlineColor");
            String shape = s("shape");
            if (outline) drawShape(gfx, shape, cx, cy, size, t, gap, oc, 1);
            drawShape(gfx, shape, cx, cy, size, t, gap, color, 0);
            gfx.alpha = 1f;
        }

        private void drawShape(Gfx g, String shape, int cx, int cy, int size, int t, int gap, int c, int grow) {
            int h1 = t / 2, h2 = t - h1; // thickness split around the centre line
            switch (shape) {
                case "Dot" -> rect(g, cx - Math.max(1, h1), cy - Math.max(1, h1), cx + Math.max(1, h2), cy + Math.max(1, h2), c, grow);
                case "Circle" -> {
                    int r = gap + size / 2 + 1;
                    int steps = Math.max(16, r * 6);
                    for (int i = 0; i < steps; i++) {
                        double a = Math.PI * 2 * i / steps;
                        int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                        rect(g, px - h1, py - h1, px + h2, py + h2, c, grow);
                    }
                }
                case "Square" -> {
                    int r = gap + size / 2 + 1;
                    rect(g, cx - r, cy - r, cx + r, cy - r + t, c, grow);
                    rect(g, cx - r, cy + r - t, cx + r, cy + r, c, grow);
                    rect(g, cx - r, cy - r, cx - r + t, cy + r, c, grow);
                    rect(g, cx + r - t, cy - r, cx + r, cy + r, c, grow);
                }
                default -> {
                    boolean top = !shape.equals("T");
                    rect(g, cx - gap - size, cy - h1, cx - gap, cy + h2, c, grow);           // left
                    rect(g, cx + gap + (t % 2), cy - h1, cx + gap + size + (t % 2), cy + h2, c, grow); // right
                    if (top) rect(g, cx - h1, cy - gap - size, cx + h2, cy - gap, c, grow); // top
                    rect(g, cx - h1, cy + gap + (t % 2), cx + h2, cy + gap + size + (t % 2), c, grow); // bottom
                    if (shape.equals("Cross + Dot")) rect(g, cx - h1, cy - h1, cx + h2, cy + h2, c, grow);
                }
            }
        }

        private static void rect(Gfx g, int x1, int y1, int x2, int y2, int c, int grow) {
            g.fill(x1 - grow, y1 - grow, x2 + grow, y2 + grow, c);
        }
    }

    /** Toggles the vanilla entity hitbox debug renderer (same as F3+B). */
    public static final class Hitboxes extends ClientModule {
        private DebugScreenEntryStatus previous;

        public Hitboxes(LockClient l) { super(l, "hitboxes"); }

        @Override protected void onEnable() {
            previous = mc.debugEntries.getStatus(DebugScreenEntries.ENTITY_HITBOXES);
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
        }

        @Override protected void onDisable() {
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, previous == null || previous == DebugScreenEntryStatus.ALWAYS_ON ? DebugScreenEntryStatus.NEVER : previous);
        }
    }

    /** Projects saved waypoints onto the screen. */
    public static final class WaypointMarkers extends ClientModule {
        public WaypointMarkers(LockClient l) { super(l, "waypoint_markers"); }

        public void render(Gfx gfx) {
            if (mc.player == null || mc.options.hideGui) return;
            List<Waypoint> list = lock.waypoints.here();
            if (list.isEmpty()) return;
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 c = cam.position();
            double yaw = Math.toRadians(cam.yRot()), pitch = Math.toRadians(cam.xRot());
            double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            // up = right x forward
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            int w = gfx.g.guiWidth(), h = gfx.g.guiHeight();
            double tanHalf = Math.tan(Math.toRadians(lock.lastFov) / 2), aspect = (double) w / h;
            double maxDist = n("maxDistance");
            float scale = (float) n("scale");
            Waypoint selected = lock.waypoints.selected();
            for (Waypoint wp : list) {
                if (!wp.visible) continue;
                double dx = wp.x + 0.5 - c.x, dy = wp.y + 0.5 - c.y, dz = wp.z + 0.5 - c.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > maxDist) continue;
                double z = dx * fx + dy * fy + dz * fz;
                double x = dx * rx + dz * rz, y = dx * ux + dy * uy + dz * uz;
                boolean behind = z <= 0.05;
                if (behind && !b("edgeArrows")) continue;
                double sx, sy;
                if (!behind) {
                    sx = w / 2.0 + (x / (z * tanHalf * aspect)) * w / 2.0;
                    sy = h / 2.0 - (y / (z * tanHalf)) * h / 2.0;
                } else {
                    sx = x > 0 ? w - 8 : 8; // behind the camera: pin to the side it is on
                    sy = h / 2.0;
                }
                boolean offscreen = sx < 0 || sx > w || sy < 0 || sy > h;
                if (offscreen && !b("edgeArrows")) continue;
                sx = Math.max(8, Math.min(w - 8, sx));
                sy = Math.max(8, Math.min(h - 8, sy));
                float alpha = 1f;
                if (b("fadeCenter")) {
                    double d = Math.hypot(sx - w / 2.0, sy - h / 2.0);
                    if (d < 40) alpha = (float) Math.max(0.25, d / 40);
                }
                String label = (b("showName") ? wp.name : "") + (b("showDistance") ? (b("showName") ? "  " : "") + Fmt.distance(dist) + "m" : "");
                gfx.push();
                gfx.translate((float) sx, (float) sy);
                gfx.scale(scale);
                gfx.alpha = alpha;
                int col = ThemeManager.withAlpha(wp.color, 0xFF);
                gfx.fill(-3, -3, 3, 3, 0xC0000000);
                gfx.fill(-2, -2, 2, 2, col);
                if (wp == selected) gfx.outline(-5, -5, 10, 10, col);
                if (!label.isEmpty()) {
                    int tw = gfx.width(label);
                    gfx.round(-tw / 2 - 3, -17, tw / 2 + 3, -6, 0x90101114);
                    gfx.text(label, -tw / 2, -15, 0xFFFFFFFF, false);
                }
                gfx.alpha = 1f;
                gfx.pop();
            }
        }
    }

    /** Toasts. */
    public static final class Notifications extends HudModule {
        public Notifications(LockClient l) { super(l, "notifications"); }

        private List<NotificationManager.Note> notes() {
            List<NotificationManager.Note> all = lock.notifications.activeToasts((long) (n("duration") * 1000));
            int max = i("max");
            return all.size() > max ? all.subList(all.size() - max, all.size()) : all;
        }

        @Override public boolean empty() { return notes().isEmpty(); }

        @Override public int[] size(Gfx gfx) {
            List<NotificationManager.Note> notes = notes();
            if (notes.isEmpty()) return new int[]{110, 20};
            int w = 90;
            for (NotificationManager.Note n : notes) w = Math.max(w, Math.max(gfx.width(n.title()), gfx.width(n.message())) + 8);
            return new int[]{w, notes.size() * 24 - 4};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            List<NotificationManager.Note> notes = notes();
            if (notes.isEmpty()) {
                gfx.fill(0, 0, 2, 20, lock.theme.accent());
                gfx.text("Notifications", 6, 1, lock.theme.accent(), shadow());
                gfx.text("Preview", 6, 11, textColor(), shadow());
                return;
            }
            long dur = (long) (n("duration") * 1000), now = System.currentTimeMillis();
            int y = 0;
            float base = gfx.alpha;
            for (NotificationManager.Note n : notes) {
                long left = dur - (now - n.time());
                gfx.alpha = base * (left < 300 ? Math.max(0, left / 300f) : 1f);
                int bar = switch (n.kind()) {
                    case WARNING -> 0xFFF5B748;
                    case ERROR -> 0xFFF2575F;
                    default -> lock.theme.accent();
                };
                gfx.fill(0, y, 2, y + 20, bar);
                gfx.text(n.title(), 6, y + 1, bar, shadow());
                gfx.text(n.message(), 6, y + 11, textColor(), shadow());
                y += 24;
            }
            gfx.alpha = base;
        }
    }
}
