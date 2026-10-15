package lock.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Batched world-space drawing through its own render pipeline (position+colour quads, alpha blended, no depth
 * test so everything shows through walls). All coordinates are camera-relative. Lines are camera-facing quads whose
 * width follows distance, so their thickness stays constant on screen.
 */
public final class Draw {
    private static RenderType type, skyType;
    private static MultiBufferSource.BufferSource source;
    private static VertexConsumer vc;
    private static Matrix4f pose;

    private Draw() {}

    private static RenderType type() {
        if (type == null) {
            RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation("pipeline/lock_overlay")
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
            type = RenderType.create("lock_overlay", RenderSetup.builder(pipeline).bufferSize(1 << 17).createRenderSetup());
        }
        return type;
    }

    /** Depth-tested additive layer for the sky: hidden behind terrain and clouds, glows over the sky itself. */
    private static RenderType skyType() {
        if (skyType == null) {
            RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation("pipeline/lock_sky")
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withBlend(BlendFunction.ADDITIVE)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
            skyType = RenderType.create("lock_sky", RenderSetup.builder(pipeline).bufferSize(1 << 17).createRenderSetup());
        }
        return skyType;
    }

    public static void beginSky(Matrix4f view) {
        pose = view;
        source = Minecraft.getInstance().renderBuffers().bufferSource();
        vc = source.getBuffer(skyType());
    }

    public static void endSky() {
        if (source != null) source.endBatch(skyType());
        vc = null;
        source = null;
    }

    public static void begin(Matrix4f view) {
        pose = view;
        source = Minecraft.getInstance().renderBuffers().bufferSource();
        vc = source.getBuffer(type());
    }

    public static void end() {
        if (source != null) source.endBatch(type());
        vc = null;
        source = null;
    }

    public static void v(double x, double y, double z, int argb) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(argb);
    }

    public static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        v(a.x, a.y, a.z, argb); v(b.x, b.y, b.z, argb); v(c.x, c.y, c.z, argb); v(d.x, d.y, d.z, argb);
    }

    /** Quad with a colour per corner (a, b, c, d in order). */
    public static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        v(a.x, a.y, a.z, ca); v(b.x, b.y, b.z, cb); v(c.x, c.y, c.z, cc); v(d.x, d.y, d.z, cd);
    }

    /** Screen-constant-width line with a colour at each end. px = thickness in pixels. */
    public static void line(Vec3 a, Vec3 b, int ca, int cb, float px) {
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid);
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize();
        double wa = px * 0.00115 * Math.max(a.length(), 0.5) * 0.5;
        double wb = px * 0.00115 * Math.max(b.length(), 0.5) * 0.5;
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

    /** Wireframe; cornersOnly draws just the end fraction of each edge. */
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

    /** Translucent faces, brighter toward the top for a grounded look. */
    public static void fill(AABB bb, int argb) {
        Vec3[] c = corners(bb);
        int top = Colors.withAlpha(argb, 0.55f);
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        for (int[] f : faces) for (int i : f) v(c[i].x, c[i].y, c[i].z, i < 4 ? argb : top);
    }
}
