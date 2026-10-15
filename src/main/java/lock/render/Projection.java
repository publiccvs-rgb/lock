package lock.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Captures the world matrices each frame so 2D overlays can anchor to world positions. */
public final class Projection {
    private static final Matrix4f viewProj = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static boolean ready;

    private Projection() {}

    public static void capture(Matrix4f proj, Matrix4f view, Vec3 camPos) {
        viewProj.set(proj).mul(view);
        cam = camPos;
        ready = true;
    }

    /** GUI-scaled screen position, or null if behind the camera. */
    public static float[] toScreen(Vec3 world) {
        if (!ready) return null;
        Vector4f p = new Vector4f((float) (world.x - cam.x), (float) (world.y - cam.y), (float) (world.z - cam.z), 1f);
        viewProj.transform(p);
        if (p.w <= 0.05f) return null;
        float nx = p.x / p.w, ny = p.y / p.w;
        if (nx < -1.3f || nx > 1.3f || ny < -1.3f || ny > 1.3f) return null;
        var win = Minecraft.getInstance().getWindow();
        return new float[] {(nx * 0.5f + 0.5f) * win.getGuiScaledWidth(), (1f - (ny * 0.5f + 0.5f)) * win.getGuiScaledHeight()};
    }
}
