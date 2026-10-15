package lock.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Batched immediate-mode helper. All coordinates are camera-relative.
 * Lines are built as camera-facing quads whose width tracks distance, so thickness stays
 * constant on screen (and works on macOS, where GL line widths above 1 are unsupported).
 */
public final class Draw {
    private static BufferBuilder buf;
    private static Matrix4f pose;

    private Draw() {}

    public static void begin(Matrix4f poseMatrix) {
        pose = poseMatrix;
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
    }

    public static void end() {
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        buf = null;
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void v(double x, double y, double z, int argb) {
        buf.addVertex(pose, (float) x, (float) y, (float) z).setColor(argb);
    }

    /** Screen-constant-width line with a colour at each end (gradient). px = thickness in pixels. */
    public static void line(Vec3 a, Vec3 b, int ca, int cb, float px) {
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid);
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize();
        // ~0.0011 world units per pixel per metre of distance at the default 70 fov
        double wa = px * 0.0011 * Math.max(a.length(), 0.5) * 0.5;
        double wb = px * 0.0011 * Math.max(b.length(), 0.5) * 0.5;
        v(a.x - side.x * wa, a.y - side.y * wa, a.z - side.z * wa, ca);
        v(a.x + side.x * wa, a.y + side.y * wa, a.z + side.z * wa, ca);
        v(b.x + side.x * wb, b.y + side.y * wb, b.z + side.z * wb, cb);
        v(b.x - side.x * wb, b.y - side.y * wb, b.z - side.z * wb, cb);
    }

    public static void line(Vec3 a, Vec3 b, int c, float px) { line(a, b, c, c, px); }

    private static Vec3[] corners(AABB bb) {
        return new Vec3[] {
            new Vec3(bb.minX, bb.minY, bb.minZ), new Vec3(bb.maxX, bb.minY, bb.minZ),
            new Vec3(bb.maxX, bb.minY, bb.maxZ), new Vec3(bb.minX, bb.minY, bb.maxZ),
            new Vec3(bb.minX, bb.maxY, bb.minZ), new Vec3(bb.maxX, bb.maxY, bb.minZ),
            new Vec3(bb.maxX, bb.maxY, bb.maxZ), new Vec3(bb.minX, bb.maxY, bb.maxZ),
        };
    }

    private static final int[][] EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    /** Wireframe; cornersOnly draws just the first/last fraction of each edge. */
    public static void box(AABB bb, int argb, float px, boolean cornersOnly) {
        Vec3[] c = corners(bb);
        for (int[] e : EDGES) {
            Vec3 a = c[e[0]], b = c[e[1]];
            if (!cornersOnly) { line(a, b, argb, px); continue; }
            Vec3 d = b.subtract(a).scale(0.28);
            line(a, a.add(d), argb, px);
            line(b, b.subtract(d), argb, px);
        }
    }

    /** Translucent faces, slightly brighter toward the bottom for a grounded look. */
    public static void fill(AABB bb, int argb) {
        Vec3[] c = corners(bb);
        int top = Colors.withAlpha(argb, 0.55f);
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        for (int[] f : faces) {
            for (int i : f) v(c[i].x, c[i].y, c[i].z, i < 4 ? argb : top);
        }
    }
}
