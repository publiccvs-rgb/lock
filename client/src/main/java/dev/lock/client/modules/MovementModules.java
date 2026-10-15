package dev.lock.client.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lock.client.LockClient;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.OptionModule;
import dev.lock.client.module.TextHud;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Movement and camera utilities. All of them drive the game's own inputs and options. */
public final class MovementModules {
    private MovementModules() {}

    /** Whether the physical key bound to a mapping is held, independent of Lock's own setDown calls. */
    static boolean physicallyDown(KeyMapping km) {
        Minecraft mc = Minecraft.getInstance();
        if (km.isUnbound()) return false;
        InputConstants.Key key = InputConstants.getKey(km.saveString());
        long window = mc.getWindow().handle();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }

    /** Shared logic for toggle sprint/sneak: an edge on the physical key flips a latch that holds the key mapping down. */
    abstract static class Latch extends ClientModule {
        protected boolean latched, wasDown;

        Latch(LockClient l, String id) { super(l, id); }

        abstract KeyMapping mapping();

        boolean always() { return false; }

        @Override public void tick() {
            if (mc.player == null) return;
            boolean down = mc.screen == null && physicallyDown(mapping());
            if (always()) latched = true;
            else if (down && !wasDown) latched = !latched;
            wasDown = down;
            if (latched) mapping().setDown(true);
        }

        @Override protected void onDisable() {
            latched = false;
            mapping().setDown(physicallyDown(mapping()));
        }

        @Override public String status() { return latched ? "Toggled" : null; }

        public boolean latched() { return active() && latched; }
    }

    public static final class ToggleSprint extends Latch {
        public ToggleSprint(LockClient l) { super(l, "toggle_sprint"); }

        @Override KeyMapping mapping() { return mc.options.keySprint; }

        @Override boolean always() { return "Always".equals(s("mode")); }

        @Override public String status() { return latched ? ("Always".equals(s("mode")) ? "Always" : "Toggled") : null; }
    }

    public static final class ToggleSneak extends Latch {
        public ToggleSneak(LockClient l) { super(l, "toggle_sneak"); }

        @Override KeyMapping mapping() { return mc.options.keyShift; }
    }

    public static final class Zoom extends ClientModule {
        private boolean zooming;
        private double level = 4, progress;
        private long lastNanos;
        private Boolean prevSmooth;

        public Zoom(LockClient l) { super(l, "zoom"); }

        @Override public void onBind(String id, boolean pressed) {
            if (!id.equals("zoomKey")) return;
            if ("Toggle".equals(s("mode"))) { if (pressed) set(!zooming); }
            else set(pressed);
        }

        private void set(boolean z) {
            if (z == zooming) return;
            zooming = z;
            if (z) {
                level = n("level");
                if (b("cinematic")) { prevSmooth = mc.options.smoothCamera; mc.options.smoothCamera = true; }
            } else if (prevSmooth != null) {
                mc.options.smoothCamera = prevSmooth;
                prevSmooth = null;
            }
        }

        @Override protected void onDisable() { set(false); progress = 0; }

        public float apply(float fov) {
            if (!active()) { progress = 0; return fov; }
            long now = System.nanoTime();
            double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
            lastNanos = now;
            double target = zooming ? 1 : 0;
            if (b("smooth")) progress += (target - progress) * Math.min(1, dt * 14);
            else progress = target;
            if (Math.abs(progress - target) < 0.001) progress = target;
            double factor = 1 + (level - 1) * progress;
            return (float) (fov / factor);
        }

        public double sensitivityFactor() {
            if (!active() || progress <= 0) return 1;
            return 1 - (1 - n("sensitivity")) * progress;
        }

        public boolean scroll(double amount) {
            if (!active() || !zooming || !b("scroll") || amount == 0) return false;
            level = Math.max(1.5, Math.min(30, level * (amount > 0 ? 1.15 : 1 / 1.15)));
            return true;
        }

        public boolean zooming() { return active() && zooming; }

        @Override public String status() { return zooming ? String.format("%.1fx", level) : null; }
    }

    public static final class Perspective extends ClientModule {
        private CameraType before;

        public Perspective(LockClient l) { super(l, "perspective"); }

        @Override public void onBind(String id, boolean pressed) {
            if (!id.equals("perspectiveKey")) return;
            if ("Hold".equals(s("mode"))) {
                if (pressed) {
                    before = mc.options.getCameraType();
                    mc.options.setCameraType("Third person front".equals(s("holdView")) ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
                } else if (before != null) {
                    mc.options.setCameraType(before);
                    before = null;
                }
            } else if (pressed) {
                mc.options.setCameraType(mc.options.getCameraType().cycle());
            }
        }

        @Override protected void onDisable() {
            if (before != null) { mc.options.setCameraType(before); before = null; }
        }
    }

    public static final class Fov extends OptionModule {
        private Integer fov;
        private Double effects;

        public Fov(LockClient l) { super(l, "fov"); }

        @Override protected void remember() { fov = mc.options.fov().get(); effects = mc.options.fovEffectScale().get(); }

        @Override protected void apply() {
            int want = i("fov");
            if (mc.options.fov().get() != want) mc.options.fov().set(want);
            double e = n("effects");
            if (Math.abs(mc.options.fovEffectScale().get() - e) > 1e-6) mc.options.fovEffectScale().set(e);
        }

        @Override protected void restore() {
            if (fov != null) mc.options.fov().set(fov);
            if (effects != null) mc.options.fovEffectScale().set(effects);
        }
    }

    public static final class ViewBobbing extends OptionModule {
        private Boolean prev;

        public ViewBobbing(LockClient l) { super(l, "view_bobbing"); }

        @Override protected void remember() { prev = mc.options.bobView().get(); }
        @Override protected void apply() { if (mc.options.bobView().get() != b("bobbing")) mc.options.bobView().set(b("bobbing")); }
        @Override protected void restore() { if (prev != null) mc.options.bobView().set(prev); }
    }

    public static final class AutoJump extends OptionModule {
        private Boolean prev;

        public AutoJump(LockClient l) { super(l, "auto_jump"); }

        @Override protected void remember() { prev = mc.options.autoJump().get(); }
        @Override protected void apply() { if (mc.options.autoJump().get() != b("autoJump")) mc.options.autoJump().set(b("autoJump")); }
        @Override protected void restore() { if (prev != null) mc.options.autoJump().set(prev); }
    }

    public static final class CameraControls extends OptionModule {
        private Double tilt, distortion, darkness;

        public CameraControls(LockClient l) { super(l, "camera_controls"); }

        @Override protected void remember() {
            tilt = mc.options.damageTiltStrength().get();
            distortion = mc.options.screenEffectScale().get();
            darkness = mc.options.darknessEffectScale().get();
        }

        @Override protected void apply() {
            setIf(mc.options.damageTiltStrength(), n("damageTilt"));
            setIf(mc.options.screenEffectScale(), n("distortion"));
            setIf(mc.options.darknessEffectScale(), n("darkness"));
        }

        static void setIf(net.minecraft.client.OptionInstance<Double> o, double v) { if (Math.abs(o.get() - v) > 1e-6) o.set(v); }

        @Override protected void restore() {
            if (tilt != null) mc.options.damageTiltStrength().set(tilt);
            if (distortion != null) mc.options.screenEffectScale().set(distortion);
            if (darkness != null) mc.options.darknessEffectScale().set(darkness);
        }
    }

    public static final class SmoothCamera extends ClientModule {
        private boolean setByUs;

        public SmoothCamera(LockClient l) { super(l, "smooth_camera"); }

        @Override public void onBind(String id, boolean pressed) {
            if (!pressed || !id.equals("smoothKey")) return;
            mc.options.smoothCamera = !mc.options.smoothCamera;
            setByUs = mc.options.smoothCamera;
            lock.notifications.module("Smooth Camera", mc.options.smoothCamera);
        }

        @Override protected void onDisable() { if (setByUs) mc.options.smoothCamera = false; setByUs = false; }

        @Override public String status() { return mc.options.smoothCamera ? "On" : null; }
    }

    public static final class SensitivityProfiles extends OptionModule {
        private Double prev;

        public SensitivityProfiles(LockClient l) { super(l, "sensitivity_profiles"); }

        private double target() { return n("s" + i("active")) / 200.0; }

        @Override protected void remember() { prev = mc.options.sensitivity().get(); }
        @Override protected void apply() { CameraControls.setIf(mc.options.sensitivity(), target()); }
        @Override protected void restore() { if (prev != null) mc.options.sensitivity().set(prev); }

        @Override public void onBind(String id, boolean pressed) {
            if (!pressed || !id.equals("cycleKey")) return;
            int next = i("active") % 3 + 1;
            state().set("active", next);
            lock.config.changed();
            apply();
            lock.notifications.system("Sensitivity", "Profile " + next + " (" + Math.round(n("s" + next)) + "%)");
        }

        @Override public String status() { return "P" + i("active"); }
    }

    public static final class MovementIndicators extends TextHud {
        public MovementIndicators(LockClient l) { super(l, "movement_indicators"); refreshMs = 100; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            StringBuilder st = new StringBuilder();
            if (p.isSprinting()) st.append("Sprinting ");
            if (p.isShiftKeyDown()) st.append("Sneaking ");
            if (p.getAbilities().flying) st.append("Flying ");
            else if (!p.onGround()) st.append("Airborne ");
            if (!st.isEmpty()) out.add(line("", st.toString().trim()));
            if (b("speed")) {
                double sp = lock.stats.speed;
                out.add(line("Speed", "km/h".equals(s("units")) ? String.format("%.1f km/h", sp * 3.6) : String.format("%.2f b/s", sp)));
            }
        }
    }

    public static final class SprintStatus extends TextHud {
        public SprintStatus(LockClient l) { super(l, "sprint_status"); refreshMs = 100; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            ToggleSprint ts = lock.modules.get("toggle_sprint", ToggleSprint.class);
            ToggleSneak tn = lock.modules.get("toggle_sneak", ToggleSneak.class);
            if (b("sneak") && p.isShiftKeyDown()) out.add(line("", tn.latched() ? "[Sneaking (Toggled)]" : "[Sneaking]"));
            else if (p.isSprinting()) out.add(line("", ts.latched() ? "[Sprinting (Toggled)]" : "[Sprinting]"));
            else if (ts.latched()) out.add(line("", "[Sprint Toggled]"));
        }
    }
}
