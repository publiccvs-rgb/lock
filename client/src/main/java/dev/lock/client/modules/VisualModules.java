package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.core.NotificationManager;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Fmt;
import dev.lock.client.render.Gfx;
import dev.lock.client.render.LockFont;
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

        private float gapAnim = -1, targetAnim;
        private long last = System.nanoTime(), hitAt;

        /** Called when the player lands an attack on an entity: brief hit-marker flash. */
        public void hit() { hitAt = System.currentTimeMillis(); }

        public void render(Gfx gfx) {
            if (mc.player == null || mc.options.hideGui) return;
            if (b("hideThirdPerson") && !mc.options.getCameraType().isFirstPerson()) return;
            if (mc.screen != null && !(mc.screen instanceof dev.lock.client.gui.LockScreen)) return;
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - last) / 1e9f);
            last = now;
            int cx = gfx.g.guiWidth() / 2, cy = gfx.g.guiHeight() / 2;
            int size = i("size"), t = i("thickness");
            float wantGap = i("gap");
            if (b("dynamicGap") && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.003) wantGap += 2.5f;
            if (gapAnim < 0) gapAnim = wantGap;
            gapAnim += (wantGap - gapAnim) * Math.min(1f, dt * 16f);
            boolean target = b("targetColor") && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
            targetAnim += ((target ? 1f : 0f) - targetAnim) * Math.min(1f, dt * 18f);
            int color = ThemeManager.blend(color("color"), color("targetColorValue"), targetAnim);
            gfx.alpha = (float) n("opacity");
            int gap = Math.round(gapAnim);
            String shape = s("shape");
            if (b("outline")) drawShape(gfx, shape, cx, cy, size, t, gap, color("outlineColor"), 1);
            drawShape(gfx, shape, cx, cy, size, t, gap, color, 0);
            float h = (System.currentTimeMillis() - hitAt) / 260f;
            if (h < 1f) {
                // hit marker: four short diagonal strokes that push outward and fade
                gfx.alpha = (float) n("opacity") * (1f - h);
                gfx.push();
                gfx.translate(cx + 0.5f, cy + 0.5f);
                gfx.rotate((float) (Math.PI / 4));
                int r0 = gap + 3 + Math.round(h * 3), len = 4;
                int hc = ThemeManager.blend(0xFFFFFFFF, color("targetColorValue"), 0.35f);
                gfx.fill(-1, -r0 - len, 1, -r0, hc);
                gfx.fill(-1, r0, 1, r0 + len, hc);
                gfx.fill(-r0 - len, -1, -r0, 1, hc);
                gfx.fill(r0, -1, r0 + len, 1, hc);
                gfx.pop();
            }
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

    /**
     * Projects your saved waypoints onto the screen: a coloured core with a pulsing ring, a gentle bob,
     * a label pill with distance, distance and crosshair fades, and animated edge arrows for
     * waypoints that are off-screen or behind you.
     */
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
            double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // up = right x forward
            int w = gfx.g.guiWidth(), h = gfx.g.guiHeight();
            double tanHalf = Math.tan(Math.toRadians(lock.lastFov) / 2), aspect = (double) w / h;
            double maxDist = n("maxDistance");
            float scale = (float) n("scale");
            long now = System.currentTimeMillis();
            Waypoint selected = lock.waypoints.selected();
            for (Waypoint wp : list) {
                if (!wp.visible) continue;
                double dx = wp.x + 0.5 - c.x, dy = wp.y + 0.5 - c.y, dz = wp.z + 0.5 - c.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > maxDist) continue;
                double z = dx * fx + dy * fy + dz * fz;
                double x = dx * rx + dz * rz, y = dx * ux + dy * uy + dz * uz;
                double sx = 0, sy = 0;
                boolean onScreen = false;
                if (z > 0.05) {
                    sx = w / 2.0 + (x / (z * tanHalf * aspect)) * w / 2.0;
                    sy = h / 2.0 - (y / (z * tanHalf)) * h / 2.0;
                    onScreen = sx >= 6 && sx <= w - 6 && sy >= 6 && sy <= h - 6;
                }
                int col = ThemeManager.withAlpha(wp.color, 0xFF);
                float fadeFar = (float) Math.min(1, Math.max(0.35, 1.0 - (dist / maxDist - 0.7) / 0.3));
                float phase = (now + (wp.id.hashCode() & 0x3FF)) / 1000f;
                if (onScreen) {
                    float alpha = fadeFar;
                    if (b("fadeCenter")) {
                        double d = Math.hypot(sx - w / 2.0, sy - h / 2.0);
                        if (d < 40) alpha *= (float) Math.max(0.3, d / 40);
                    }
                    float bob = (float) Math.sin(phase * 2.2) * 1.2f;
                    gfx.push();
                    gfx.translate((float) sx, (float) sy + bob);
                    gfx.scale(scale);
                    gfx.alpha = alpha;
                    float pulse = (phase * 0.7f) % 1f;
                    gfx.circle(0, 0, 4 + pulse * 7, ThemeManager.withAlpha(col, (int) (0x90 * (1 - pulse))));
                    if (wp == selected) gfx.circle(0, 0, 6.5f, ThemeManager.withAlpha(col, 0x60));
                    gfx.circle(0, 0, 4f, 0xC0000000);
                    gfx.circle(0, 0, 3f, col);
                    gfx.circle(0, 0, 1.2f, 0xFFFFFFFF);
                    label(gfx, wp, dist, col);
                    gfx.alpha = 1f;
                    gfx.pop();
                } else if (b("edgeArrows")) {
                    // direction on screen of the waypoint (camera-space right/up), pinned to an inset ellipse
                    double ang = Math.atan2(-y, x);
                    float ex = (float) (w / 2.0 + Math.cos(ang) * (w / 2.0 - 22)), ey = (float) (h / 2.0 + Math.sin(ang) * (h / 2.0 - 22));
                    float push = (float) Math.sin(phase * 4) * 1.5f;
                    gfx.push();
                    gfx.translate(ex, ey);
                    gfx.scale(scale);
                    gfx.alpha = fadeFar;
                    gfx.circle(0, 0, 8, 0xB0101114);
                    gfx.push();
                    gfx.rotate((float) ang);
                    gfx.translate(push, 0);
                    gfx.icon("nav", -5, -5, 10, col);
                    gfx.pop();
                    String d = Fmt.distance(dist) + "m";
                    float tw = gfx.width(d, 6f, dev.lock.client.render.LockFont.Weight.MEDIUM);
                    gfx.textMid(d, -tw / 2f, 14, 6f, 0xE0FFFFFF, dev.lock.client.render.LockFont.Weight.MEDIUM);
                    gfx.alpha = 1f;
                    gfx.pop();
                }
            }
        }

        private void label(Gfx gfx, Waypoint wp, double dist, int col) {
            boolean name = b("showName"), distance = b("showDistance");
            if (!name && !distance) return;
            var R = dev.lock.client.render.LockFont.Weight.MEDIUM;
            String n = name ? wp.name : "", d = distance ? Fmt.distance(dist) + "m" : "";
            float nw = gfx.width(n, 7f, dev.lock.client.render.LockFont.Weight.SEMIBOLD), dw = gfx.width(d, 6.5f, R);
            float gap = name && distance ? 5 : 0, tw = nw + gap + dw + 12;
            int x0 = Math.round(-tw / 2), y0 = -22;
            gfx.rrect(x0, y0, Math.round(tw), 13, 6, 0xC8101114);
            gfx.fill(x0 + 6, y0 + 12, x0 + Math.round(tw) - 6, y0 + 13, ThemeManager.withAlpha(col, 0xC0));
            float tx = x0 + 6;
            if (name) tx = gfx.textMid(n, tx, y0 + 6.5f, 7f, 0xFFFFFFFF, dev.lock.client.render.LockFont.Weight.SEMIBOLD) + gap;
            if (distance) gfx.textMid(d, tx, y0 + 6.5f, 6.5f, 0xB0FFFFFF, R);
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
            if (notes.isEmpty()) return new int[]{120, 26};
            int w = 110;
            for (NotificationManager.Note n : notes)
                w = Math.max(w, (int) Math.max(gfx.width(n.title(), 7.5f, LockFont.Weight.SEMIBOLD), gfx.width(n.message(), 7f, LockFont.Weight.REGULAR)) + 30);
            return new int[]{w, notes.size() * 30 - 4};
        }

        @Override public boolean ownBackground() { return true; }

        @Override public void draw(Gfx gfx, int w, int h) {
            List<NotificationManager.Note> notes = notes();
            if (notes.isEmpty()) {
                card(gfx, 0, w, "info", lock.theme.accent(), "Notifications", "Preview", 1f, 0);
                return;
            }
            long dur = (long) (n("duration") * 1000), now = System.currentTimeMillis();
            int y = 0;
            for (NotificationManager.Note n : notes) {
                long age = now - n.time(), left = dur - age;
                float in = Math.min(1f, age / 180f), out = left < 250 ? Math.max(0, left / 250f) : 1f;
                float ease = 1 - (1 - in) * (1 - in);
                int color = switch (n.kind()) {
                    case WARNING -> 0xFFE9A23B;
                    case ERROR -> 0xFFF2575F;
                    default -> lock.theme.accent();
                };
                String icon = switch (n.kind()) {
                    case WARNING, ERROR -> "info";
                    case MODULE -> "check";
                    case SYSTEM -> "bell";
                    default -> "info";
                };
                boolean right = anchor().hx == 2;
                card(gfx, y, w, icon, color, n.title(), n.message(), Math.min(ease, out), (int) ((1 - ease) * 24 * (right ? 1 : -1)));
                if (left < dur) gfx.rrect(4, y + 24, Math.max(0, Math.round((w - 8) * (left / (float) dur))), 1, 0, ThemeManager.withAlpha(color, 0x90));
                y += 30;
            }
        }

        private void card(Gfx gfx, int y, int w, String icon, int color, String title, String msg, float alpha, int dx) {
            float base = gfx.alpha;
            gfx.alpha = base * alpha;
            gfx.push();
            gfx.translate(dx, 0);
            int r = i(dev.lock.core.module.ModuleDef.H_RADIUS);
            gfx.rrect(0, y, w, 26, r, color(dev.lock.core.module.ModuleDef.H_BG_COLOR) | 0xE0000000);
            gfx.circle(13, y + 13, 7.5f, ThemeManager.withAlpha(color, 0x38));
            gfx.icon(icon, 9, y + 9, 8, color);
            gfx.textMid(title, 25, y + 9, 7.5f, textColor(), LockFont.Weight.SEMIBOLD);
            gfx.textMid(msg, 25, y + 18, 7f, ThemeManager.withAlpha(textColor(), 0xB0), LockFont.Weight.REGULAR);
            gfx.pop();
            gfx.alpha = base;
        }
    }
}
