package lock.module;

import lock.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public class Freecam extends Module {
    public final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.2, 5.0, 0.1)).d("Camera flight speed.");
    public final BoolSetting body = add(new BoolSetting("Show Body", true)).d("Keeps rendering your own model where you stand.");
    public final BoolSetting stopOnHit = add(new BoolSetting("Exit On Damage", true)).d("Returns the camera when you take damage.");

    public double x, y, z, px, py, pz;
    public float yaw, pitch;
    public boolean active;
    private double vx, vy, vz;

    public Freecam() { super("Freecam", "Detach the camera from your body", Category.RENDER, false); }

    @Override public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { active = false; return; }
        x = px = mc.player.getX(); y = py = mc.player.getEyeY(); z = pz = mc.player.getZ();
        yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
        vx = vy = vz = 0;
        active = true;
    }

    @Override public void onDisable() { active = false; }

    /** Mouse look, redirected here while active. */
    public void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
    }

    public double lx(float t) { return px + (x - px) * t; }
    public double ly(float t) { return py + (y - py) * t; }
    public double lz(float t) { return pz + (z - pz) * t; }

    @Override public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { setEnabled(false); return; }
        if (stopOnHit.get() && mc.player.hurtTime > 0) { setEnabled(false); return; }
        if (!active) return;
        px = x; py = y; pz = z;

        Options o = mc.options;
        boolean typing = mc.screen != null;
        double f = typing ? 0 : (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = typing ? 0 : (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double u = typing ? 0 : (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);         // to the right of the view direction
        double tx = fx * f - rx * s, ty = fy * f + u, tz = fz * f - rz * s;
        double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double k = speed.get() * 0.55;
        if (len > 1e-6) { tx = tx / len * k; ty = ty / len * k; tz = tz / len * k; } else { tx = ty = tz = 0; }
        vx += (tx - vx) * 0.38; vy += (ty - vy) * 0.38; vz += (tz - vz) * 0.38;
        x += vx; y += vy; z += vz;
    }
}
