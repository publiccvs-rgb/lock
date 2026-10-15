package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.core.GameInfo;
import dev.lock.client.core.NotificationManager;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.module.TextHud;
import dev.lock.client.render.Fmt;
import dev.lock.client.render.Gfx;
import dev.lock.client.render.LockFont;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The HUD category: independent, individually configurable elements. */
public final class HudModules {
    private HudModules() {}

    static int pingColor(int ms) { return ms < 0 ? 0xFFAAAAAA : ms < 80 ? 0xFF6EE7A0 : ms < 160 ? 0xFFF5D76E : 0xFFF2575F; }

    static int fpsColor(int fps) { return fps >= 60 ? 0xFF6EE7A0 : fps >= 30 ? 0xFFF5D76E : 0xFFF2575F; }

    public static final class Fps extends TextHud {
        public Fps(LockClient l) { super(l, "fps"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            int fps = mc.getFps();
            out.add(line(b("label") ? (compact ? "" : "FPS") : "", compact && b("label") ? fps + " fps" : Integer.toString(fps), b("colorize") ? fpsColor(fps) : 0));
        }
    }

    public static final class Cps extends TextHud {
        public Cps(LockClient l) { super(l, "cps"); refreshMs = 50; }

        @Override protected void lines(List<Line> out, boolean compact) {
            String v = b("right") ? lock.stats.cps(0) + " | " + lock.stats.cps(1) : Integer.toString(lock.stats.cps(0));
            out.add(line(b("label") ? "CPS" : "", v));
        }
    }

    public static final class Keystrokes extends HudModule {
        public Keystrokes(LockClient l) { super(l, "keystrokes"); }

        @Override public int[] size(Gfx gfx) {
            int k = i("keySize"), gap = 2;
            int w = k * 3 + gap * 2;
            int h = k * 2 + gap;
            if (b("mouse")) h += gap + k;
            if (b("space")) h += gap + k / 2;
            return new int[]{w, h};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            int k = i("keySize"), gap = 2;
            var o = mc.options;
            key(gfx, o.keyUp, k + gap, 0, k, k, null);
            key(gfx, o.keyLeft, 0, k + gap, k, k, null);
            key(gfx, o.keyDown, k + gap, k + gap, k, k, null);
            key(gfx, o.keyRight, (k + gap) * 2, k + gap, k, k, null);
            int y = (k + gap) * 2;
            if (b("mouse")) {
                int mw = (w - gap) / 2;
                key(gfx, o.keyAttack, 0, y, mw, k, b("cps") ? lock.stats.cps(0) + " CPS" : null);
                key(gfx, o.keyUse, mw + gap, y, w - mw - gap, k, b("cps") ? lock.stats.cps(1) + " CPS" : null);
                y += k + gap;
            }
            if (b("space")) {
                float t = ease("jump", o.keyJump.isDown());
                gfx.rrect(0, y, w, k / 2, i(dev.lock.core.module.ModuleDef.H_RADIUS), ThemeManager.blend(color("released"), color("pressed"), t));
                int c = ThemeManager.blend(textColor(), color("pressedText"), t);
                gfx.rrect(w / 2 - 9, y + k / 4 - 1, 18, 2, 1, c);
            }
        }

        private void key(Gfx gfx, KeyMapping km, int x, int y, int w, int h, String sub) {
            float t = ease(km.getName(), km.isDown());
            int r = i(dev.lock.core.module.ModuleDef.H_RADIUS);
            gfx.rrect(x, y, w, h, r, ThemeManager.blend(color("released"), color("pressed"), t));
            String label = km == mc.options.keyAttack ? "LMB" : km == mc.options.keyUse ? "RMB" : km.getTranslatedKeyMessage().getString();
            if (label.length() > 4) label = label.substring(0, 4);
            int c = ThemeManager.blend(textColor(), color("pressedText"), t);
            float size = 8f;
            if (sub == null) {
                float lw = gfx.width(label, size, LockFont.Weight.MEDIUM);
                gfx.textMid(label, x + (w - lw) / 2f, y + h / 2f, size, c, LockFont.Weight.MEDIUM);
            } else {
                float lw = gfx.width(label, size, LockFont.Weight.MEDIUM);
                gfx.textMid(label, x + (w - lw) / 2f, y + h / 2f - 3, size, c, LockFont.Weight.MEDIUM);
                float sw = gfx.width(sub, 5.5f, LockFont.Weight.REGULAR);
                gfx.textMid(sub, x + (w - sw) / 2f, y + h / 2f + 5, 5.5f, ThemeManager.withAlpha(c, 0xB0), LockFont.Weight.REGULAR);
            }
        }

        private final java.util.Map<String, Float> anim = new java.util.HashMap<>();
        private long last = System.nanoTime();

        private float ease(String k, boolean down) {
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - last) / 1e9f);
            last = now;
            float v = anim.getOrDefault(k, 0f);
            v += ((down ? 1f : 0f) - v) * Math.min(1f, dt * 30f);
            anim.put(k, v);
            return v;
        }
    }

    public static final class MouseButtons extends HudModule {
        public MouseButtons(LockClient l) { super(l, "mouse_buttons"); }

        @Override public int[] size(Gfx gfx) { return new int[]{b("middle") ? 62 : 41, 16}; }

        @Override public void draw(Gfx gfx, int w, int h) {
            boolean mid = b("middle");
            btn(gfx, 0, 20, "LMB", mc.mouseHandler.isLeftPressed());
            if (mid) btn(gfx, 21, 20, "MMB", mc.mouseHandler.isMiddlePressed());
            btn(gfx, mid ? 42 : 21, 20, "RMB", mc.mouseHandler.isRightPressed());
        }

        private void btn(Gfx gfx, int x, int w, String l, boolean down) {
            gfx.rrect(x, 0, w, 16, i(dev.lock.core.module.ModuleDef.H_RADIUS), down ? color("pressed") : 0xB0141517);
            float lw = gfx.width(l, 6f, LockFont.Weight.MEDIUM);
            gfx.textMid(l, x + (w - lw) / 2f, 8, 6f, down ? 0xFF111214 : textColor(), LockFont.Weight.MEDIUM);
        }
    }

    public static final class Coordinates extends TextHud {
        public Coordinates(LockClient l) { super(l, "coordinates"); }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            int dec = b("block") ? 0 : lock.config.state("navigation").integer("decimals");
            String x = Fmt.coord(p.getX(), dec), y = Fmt.coord(p.getY(), dec), z = Fmt.coord(p.getZ(), dec);
            if (compact) out.add(line("XYZ", x + ", " + y + ", " + z));
            else {
                out.add(line("X", x));
                out.add(line("Y", y));
                out.add(line("Z", z));
            }
            if (b("facing")) out.add(line(compact ? "" : "Facing", Fmt.direction(p.getYRot(), longDirections()) + " (" + Fmt.axis(p.getYRot()) + ")"));
            if (b("nether")) {
                String dim = GameInfo.dimension();
                if (dim.equals("minecraft:overworld")) out.add(line("Nether", Fmt.coord(p.getX() / 8, 0) + ", " + Fmt.coord(p.getZ() / 8, 0)));
                else if (dim.equals("minecraft:the_nether")) out.add(line("Overworld", Fmt.coord(p.getX() * 8, 0) + ", " + Fmt.coord(p.getZ() * 8, 0)));
            }
            if (b("chunk")) out.add(line("Chunk", (p.getBlockX() >> 4) + ", " + (p.getBlockZ() >> 4) + " [" + (p.getBlockX() & 15) + ", " + (p.getBlockZ() & 15) + "]"));
        }

        private boolean longDirections() { return "Long".equals(lock.config.state("navigation").str("directions")); }
    }

    public static final class Compass extends HudModule {
        public Compass(LockClient l) { super(l, "compass"); }

        private boolean strip() { return "Strip".equals(s("style")); }

        @Override public int[] size(Gfx gfx) {
            if (!strip()) return new int[]{Math.max(40, gfx.width(text())), 9};
            return new int[]{i("width"), 20};
        }

        private float bearing() {
            float yaw = mc.player == null ? 0 : mc.player.getYRot();
            return ((yaw + 180) % 360 + 360) % 360;
        }

        private String text() {
            float yaw = mc.player == null ? 0 : mc.player.getYRot();
            boolean lng = "Long".equals(lock.config.state("navigation").str("directions"));
            return Fmt.direction(yaw, lng) + (b("degrees") ? " " + Math.round(bearing()) + "°" : "");
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            if (!strip()) {
                gfx.text(text(), 0, 0, textColor(), shadow());
                return;
            }
            float bearing = bearing();
            float ppd = w / 180f; // show +-90 degrees
            int cx = w / 2;
            gfx.scissor(0, 0, w, h);
            for (int deg = 0; deg < 360; deg += 15) {
                float off = ((deg - bearing + 540) % 360) - 180;
                int x = Math.round(cx + off * ppd);
                if (x < 0 || x > w) continue;
                boolean major = deg % 45 == 0;
                gfx.fill(x, 0, x + 1, major ? 4 : 2, ThemeManager.withAlpha(textColor(), major ? 0xC0 : 0x60));
                if (major) {
                    String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
                    String n = names[deg / 45];
                    boolean cardinal = deg % 90 == 0;
                    gfx.centered(n, x + 1, 6, cardinal ? textColor() : ThemeManager.withAlpha(textColor(), 0x90), shadow());
                }
            }
            gfx.unscissor();
            gfx.fill(cx, 0, cx + 1, 5, lock.theme.accent());
            if (b("degrees")) {
                String d = Math.round(bearing) + "°";
                float sc = 0.7f;
                gfx.textScaled(d, cx - gfx.width(d) * sc / 2f, 15, sc, lock.theme.accent(), false);
            }
        }
    }

    public static final class Ping extends TextHud {
        public Ping(LockClient l) { super(l, "ping"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) {
            int p = GameInfo.ping();
            out.add(line(b("label") ? "Ping" : "", p < 0 ? "-- ms" : p + " ms", b("colorize") ? pingColor(p) : 0));
        }
    }

    public static final class ServerStatus extends TextHud {
        public ServerStatus(LockClient l) { super(l, "server_status"); refreshMs = 1000; }

        @Override protected void lines(List<Line> out, boolean compact) {
            out.add(line(compact ? "" : "Server", GameInfo.server(b("address"))));
            if (b("players") && !mc.isLocalServer()) out.add(line("Players", Integer.toString(GameInfo.onlinePlayers())));
        }
    }

    /** Shared durability text. */
    static String durability(ItemStack s, String mode) {
        if (!s.isDamageableItem()) return "";
        int left = s.getMaxDamage() - s.getDamageValue();
        return switch (mode) {
            case "Value" -> Integer.toString(left);
            case "Percent" -> Math.round(left * 100f / s.getMaxDamage()) + "%";
            default -> "";
        };
    }

    static int durabilityColor(ItemStack s) {
        if (!s.isDamageableItem()) return 0xFFFFFFFF;
        float f = (s.getMaxDamage() - s.getDamageValue()) / (float) s.getMaxDamage();
        return f > 0.5f ? 0xFF6EE7A0 : f > 0.2f ? 0xFFF5D76E : 0xFFF2575F;
    }

    public static final class ArmorHud extends HudModule {
        public ArmorHud(LockClient l) { super(l, "armor_hud"); }

        private List<ItemStack> items() {
            List<ItemStack> out = new ArrayList<>();
            LocalPlayer p = mc.player;
            if (p == null) return out;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = p.getItemBySlot(slot);
                if (!s.isEmpty()) out.add(s);
            }
            if (b("hands")) {
                if (!p.getMainHandItem().isEmpty()) out.add(p.getMainHandItem());
                if (!p.getOffhandItem().isEmpty()) out.add(p.getOffhandItem());
            }
            return out;
        }

        @Override public boolean empty() { return items().isEmpty(); }

        @Override public int[] size(Gfx gfx) {
            List<ItemStack> items = items();
            int n = Math.max(1, items.size());
            boolean vertical = "Vertical".equals(s("orientation"));
            int tw = 0;
            for (ItemStack s : items) tw = Math.max(tw, gfx.width(durability(s, s("durability"))));
            return vertical ? new int[]{16 + (tw > 0 ? tw + 3 : 0), n * 17 - 1} : new int[]{n * 20 - 4, 16 + (tw > 0 ? 9 : 0)};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            List<ItemStack> items = items();
            if (items.isEmpty()) { gfx.text(def.name, 0, 4, textColor(), shadow()); return; }
            boolean vertical = "Vertical".equals(s("orientation"));
            int i = 0;
            for (ItemStack s : items) {
                String d = durability(s, s("durability"));
                if (vertical) {
                    gfx.item(s, 0, i * 17, true);
                    gfx.text(d, 19, i * 17 + 4, durabilityColor(s), shadow());
                } else {
                    gfx.item(s, i * 20, 0, true);
                    if (!d.isEmpty()) gfx.textScaled(d, i * 20 + 8 - gfx.width(d) * 0.75f / 2, 18, 0.75f, durabilityColor(s), shadow());
                }
                i++;
            }
        }
    }

    public static final class Durability extends TextHud {
        private final Set<String> warned = new HashSet<>();

        public Durability(LockClient l) { super(l, "durability"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            double warn = n("warn");
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = p.getItemBySlot(slot);
                if (s.isEmpty() || !s.isDamageableItem()) continue;
                if (b("onlyDamaged") && s.getDamageValue() == 0) continue;
                int left = s.getMaxDamage() - s.getDamageValue();
                double pct = left * 100.0 / s.getMaxDamage();
                String key = slot.name() + ":" + s.getItem();
                boolean low = pct < warn;
                if (low && b("notify") && warned.add(key)) lock.notifications.warn("Low durability", s.getHoverName().getString() + " " + Math.round(pct) + "%");
                if (!low) warned.remove(key);
                String name = compact ? "" : s.getHoverName().getString();
                out.add(line(name, left + "/" + s.getMaxDamage(), low ? 0xFFF2575F : 0));
            }
        }
    }

    public static final class PotionEffects extends TextHud {
        public PotionEffects(LockClient l) { super(l, "potion_effects"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            List<MobEffectInstance> effects = new ArrayList<>(p.getActiveEffects());
            if (!b("ambient")) effects.removeIf(MobEffectInstance::isAmbient);
            if ("Name".equals(s("sort"))) effects.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));
            else effects.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
            boolean blinkOff = b("blink") && (System.currentTimeMillis() / 400) % 2 == 0;
            for (MobEffectInstance e : effects) {
                String name = e.getEffect().value().getDisplayName().getString();
                if (e.getAmplifier() > 0) name += " " + Fmt.roman(e.getAmplifier() + 1);
                boolean expiring = !e.isInfiniteDuration() && e.getDuration() < 200;
                String time = e.isInfiniteDuration() ? "∞" : Fmt.ticks(e.getDuration());
                int color = e.getEffect().value().getColor() | 0xFF000000;
                out.add(line(name, expiring && blinkOff ? "" : time, color));
            }
        }
    }

    public static final class Status extends TextHud {
        public Status(LockClient l) { super(l, "status"); refreshMs = 100; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            if (b("air") && p.getAirSupply() < p.getMaxAirSupply()) out.add(line("Air", Math.max(0, p.getAirSupply() / 20) + "s", 0xFF8EC9FF));
            if (b("fire") && p.isOnFire()) out.add(line("", "Burning", 0xFFF59A48));
            if (b("freeze") && p.getTicksFrozen() > 0) out.add(line("Freezing", Math.round(p.getPercentFrozen() * 100) + "%", 0xFFBFE6FF));
            if (b("motion")) {
                if (p.getAbilities().flying) out.add(line("", "Flying"));
                else if (p.isFallFlying()) out.add(line("", "Gliding"));
                else if (p.isSwimming()) out.add(line("", "Swimming"));
                if (p.isSleeping()) out.add(line("", "Sleeping"));
            }
        }
    }

    public static final class Timers extends TextHud {
        private long swStart, swAccum;
        private boolean swRunning;
        private long cdEnd;
        private boolean cdRunning;

        public Timers(LockClient l) { super(l, "timers"); refreshMs = 100; }

        @Override public void onBind(String id, boolean pressed) {
            if (!pressed) return;
            long now = System.currentTimeMillis();
            switch (id) {
                case "swKey" -> {
                    if (swRunning) { swAccum += now - swStart; swRunning = false; }
                    else { swStart = now; swRunning = true; }
                }
                case "swReset" -> { swAccum = 0; swStart = now; }
                case "cdKey" -> {
                    if (cdRunning) cdRunning = false;
                    else { cdEnd = now + (long) (n("countdown") * 60_000); cdRunning = true; }
                }
                default -> { }
            }
            invalidate();
        }

        @Override public void tick() {
            if (cdRunning && System.currentTimeMillis() >= cdEnd) {
                cdRunning = false;
                if (b("cdNotify")) lock.notifications.post(NotificationManager.Kind.INFO, "Timer", "Countdown finished");
            }
        }

        @Override protected void lines(List<Line> out, boolean compact) {
            long now = System.currentTimeMillis();
            long sw = swAccum + (swRunning ? now - swStart : 0);
            out.add(line(compact ? "SW" : "Stopwatch", Fmt.duration(sw), swRunning ? 0 : 0xFFAAAAAA));
            long cd = cdRunning ? cdEnd - now : (long) (n("countdown") * 60_000);
            out.add(line(compact ? "CD" : "Countdown", Fmt.duration(cd + 999), cdRunning ? 0 : 0xFFAAAAAA));
        }
    }

    public static final class ModuleList extends HudModule {
        public ModuleList(LockClient l) { super(l, "module_list"); }

        private List<String> entries(Gfx gfx) {
            List<ClientModule> mods = new ArrayList<>();
            for (ClientModule m : lock.modules.all()) {
                if (!m.def.toggleable() || !m.active()) continue;
                if (b("hideHud") && m.def.hud) continue;
                mods.add(m);
            }
            List<String> out = new ArrayList<>();
            for (ClientModule m : mods) {
                String st = m.status();
                out.add(st == null ? m.def.name : m.def.name + " §7" + st);
            }
            if ("Alphabetical".equals(s("sort"))) out.sort(String::compareToIgnoreCase);
            else out.sort(Comparator.comparingInt((String x) -> gfx.width(x)).reversed());
            return out;
        }

        @Override public boolean empty() { return false; }

        @Override public int[] size(Gfx gfx) {
            List<String> e = entries(gfx);
            int w = 40;
            for (String s : e) w = Math.max(w, gfx.width(s) + 6);
            return new int[]{w + 4, Math.max(1, e.size()) * 13 - 1};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            List<String> e = entries(gfx);
            if (e.isEmpty()) { if (editing) gfx.textMid(def.name, 0, 6, 8f, textColor(), LockFont.Weight.MEDIUM); return; }
            boolean right = anchor().hx == 2 || "Right".equals(align());
            int y = 0;
            for (String s : e) {
                int tw = gfx.width(s);
                int bw = tw + 10;
                int bx = right ? w - bw : 0;
                gfx.rrect(bx, y, bw, 12, 3, 0xA0141517);
                if (b("bars")) gfx.rrect(right ? w - 2 : 0, y + 2, 2, 8, 1, lock.theme.accent());
                gfx.textMid(s, bx + 5, y + 6, 8f, textColor(), LockFont.Weight.MEDIUM);
                y += 13;
            }
        }
    }

    public static final class ModuleIndicators extends HudModule {
        public ModuleIndicators(LockClient l) { super(l, "module_indicators"); }

        private List<String> chips() {
            List<String> out = new ArrayList<>();
            for (String id : new String[]{"toggle_sprint", "toggle_sneak", "zoom", "smooth_camera", "sensitivity_profiles"}) {
                ClientModule m = lock.modules.get(id);
                if (m != null && m.active() && m.status() != null) out.add(m.def.name.replace("Toggle ", "") + ": " + m.status());
            }
            if (b("profile")) out.add("Profile: " + lock.config.profile().name());
            if (b("mode") && !"Normal".equals(lock.visibility.mode())) out.add(lock.visibility.mode());
            return out;
        }

        @Override public boolean empty() { return chips().isEmpty(); }

        @Override public int[] size(Gfx gfx) {
            List<String> c = chips();
            if (c.isEmpty()) return new int[]{gfx.width(def.name), 12};
            int w = 0;
            for (String s : c) w += gfx.width(s) + 16;
            return new int[]{w - 4, 12};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            List<String> c = chips();
            if (c.isEmpty()) { gfx.textMid(def.name, 0, 6, 8f, textColor(), LockFont.Weight.MEDIUM); return; }
            int x = 0;
            for (String s : c) {
                int tw = gfx.width(s);
                gfx.rrect(x, 0, tw + 12, 12, 6, 0xB0141517);
                gfx.circle(x + 6, 6, 2, lock.theme.accent());
                gfx.textMid(s, x + 10, 6, 7f, textColor(), LockFont.Weight.MEDIUM);
                x += tw + 16;
            }
        }
    }

    /** Expands {placeholders} used by Custom Text. */
    static String expand(LockClient lock, String text) {
        if (text.indexOf('{') < 0) return text;
        var mc = lock.mc;
        LocalPlayer p = mc.player;
        String out = text;
        out = out.replace("{fps}", Integer.toString(mc.getFps()));
        int ping = GameInfo.ping();
        out = out.replace("{ping}", ping < 0 ? "--" : Integer.toString(ping));
        if (p != null) {
            out = out.replace("{x}", Integer.toString(p.getBlockX())).replace("{y}", Integer.toString(p.getBlockY())).replace("{z}", Integer.toString(p.getBlockZ()));
        }
        if (out.contains("{biome}")) out = out.replace("{biome}", GameInfo.biome());
        out = out.replace("{dimension}", GameInfo.dimensionName()).replace("{time}", GameInfo.clock(true)).replace("{day}", Long.toString(GameInfo.day()));
        out = out.replace("{server}", GameInfo.server(false)).replace("{profile}", lock.config.profile().name()).replace("{mode}", lock.visibility.mode());
        out = out.replace("{clock}", new SimpleDateFormat("HH:mm").format(new Date()));
        return out;
    }

    public static final class CustomText extends TextHud {
        public CustomText(LockClient l) { super(l, "custom_text"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            String a = s("text"), b = s("text2");
            if (!a.isEmpty()) out.add(line("", expand(lock, a)));
            if (!b.isEmpty()) out.add(line("", expand(lock, b)));
        }
    }

    public static final class InfoPanel extends TextHud {
        public InfoPanel(LockClient l) { super(l, "info_panel"); refreshMs = 200; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (!s("title").isEmpty()) out.add(line("", s("title"), lock.theme.accent()));
            if (b("fps")) out.add(line("FPS", Integer.toString(mc.getFps())));
            if (b("ping")) { int pg = GameInfo.ping(); out.add(line("Ping", pg < 0 ? "--" : pg + " ms")); }
            if (b("coords") && p != null && lock.visibility.visible("sensitive")) out.add(line("XYZ", p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ()));
            if (b("direction") && p != null) out.add(line("Facing", Fmt.direction(p.getYRot(), false)));
            if (b("biome")) out.add(line("Biome", GameInfo.biome()));
            if (b("dimension")) out.add(line("Dim", GameInfo.dimensionName()));
            if (b("time")) out.add(line("Time", GameInfo.clock(true)));
            if (b("speed")) out.add(line("Speed", String.format("%.2f b/s", lock.stats.speed)));
            if (b("memory")) {
                Runtime r = Runtime.getRuntime();
                out.add(line("Mem", (r.totalMemory() - r.freeMemory()) / 1048576 + " MB"));
            }
        }
    }

    public static final class HeldItem extends HudModule {
        public HeldItem(LockClient l) { super(l, "held_item"); }

        private ItemStack stack() { return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem(); }

        private String info() {
            ItemStack s = stack();
            if (s.isEmpty()) return "";
            StringBuilder sb = new StringBuilder(s.getHoverName().getString());
            if (b("total")) {
                int total = 0;
                var inv = mc.player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack o = inv.getItem(i);
                    if (ItemStack.isSameItemSameComponents(o, s)) total += o.getCount();
                }
                if (total > 1) sb.append("  x").append(total);
            }
            if (b("durability") && s.isDamageableItem()) sb.append("  ").append(s.getMaxDamage() - s.getDamageValue()).append('/').append(s.getMaxDamage());
            return sb.toString();
        }

        @Override public boolean empty() { return stack().isEmpty(); }

        @Override public int[] size(Gfx gfx) {
            String t = stack().isEmpty() ? def.name : info();
            return new int[]{gfx.width(t) + (b("icon") ? 20 : 0), 16};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            ItemStack s = stack();
            int x = 0;
            if (b("icon") && !s.isEmpty()) { gfx.item(s, 0, 0, false); x = 20; }
            gfx.text(s.isEmpty() ? def.name : info(), x, 4, textColor(), shadow());
        }
    }

    public static final class InventoryHud extends HudModule {
        public InventoryHud(LockClient l) { super(l, "inventory_hud"); }

        @Override public boolean empty() {
            if (mc.player == null) return true;
            if (!b("emptyHide")) return false;
            for (int i = 9; i < 36; i++) if (!mc.player.getInventory().getItem(i).isEmpty()) return false;
            return true;
        }

        @Override public int[] size(Gfx gfx) { return new int[]{9 * 18 - 2, 3 * 18 - 2}; }

        @Override public void draw(Gfx gfx, int w, int h) {
            if (mc.player == null) return;
            for (int i = 0; i < 27; i++) {
                int x = (i % 9) * 18, y = (i / 9) * 18;
                gfx.fill(x, y, x + 16, y + 16, 0x30FFFFFF);
                gfx.item(mc.player.getInventory().getItem(9 + i), x, y, true);
            }
        }
    }

    public static final class NotificationFeed extends TextHud {
        public NotificationFeed(LockClient l) { super(l, "notification_feed"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            List<NotificationManager.Note> feed = lock.notifications.feed();
            int n = i("lines");
            SimpleDateFormat f = new SimpleDateFormat("HH:mm");
            for (int i = Math.max(0, feed.size() - n); i < feed.size(); i++) {
                NotificationManager.Note note = feed.get(i);
                String label = b("timestamps") ? f.format(new Date(note.time())) : "";
                out.add(line(label, note.title() + ": " + note.message()));
            }
        }
    }

}
