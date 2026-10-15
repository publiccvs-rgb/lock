package lock.render;

import lock.Core;
import lock.module.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Everything that lives in world space for the entity/block ESP modules. */
public final class WorldEsp {
    private WorldEsp() {}

    /** Called once per frame; owns the shared Draw batch for all world-space modules. */
    public static void render(Camera camera, float pt, Matrix4f view, Matrix4f proj) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ModuleManager m = Core.modules;
        Vec3 cam = camera.position();
        Projection.capture(proj, view, cam);

        boolean any = m.boxes.enabled() || m.tracers.enabled() || m.storage.enabled() || m.items.enabled() || m.baseFinder.enabled();
        if (!any) return;

        Draw.begin(view);
        try {
            Vec3 eye = Projection.look().scale(0.6);

            for (Entity e : mc.level.entitiesForRendering()) {
                if (m.items.enabled() && e instanceof ItemEntity ie) { item(m.items, ie, cam, pt); continue; }
                if (!Esp.targets(e)) continue;

                double x = Mth.lerp(pt, e.xOld, e.getX()) - e.getX();
                double y = Mth.lerp(pt, e.yOld, e.getY()) - e.getY();
                double z = Mth.lerp(pt, e.zOld, e.getZ()) - e.getZ();
                AABB bb = e.getBoundingBox().move(x - cam.x, y - cam.y, z - cam.z).inflate(0.03);
                float dist = mc.player.distanceTo(e);
                float range = (float) (double) m.general.range.get();
                float fade = Mth.clamp((range - dist) / (range * 0.25f), 0.15f, 1f);

                if (m.boxes.enabled()) {
                    Boxes b = m.boxes;
                    int c = Esp.color(e, b.color);
                    float px = b.thickness.f();
                    Draw.box(bb, Colors.argb(c, 0.10f * fade), px * 3.2f, b.style.is("Corners"));
                    Draw.box(bb, Colors.argb(c, 0.85f * fade), px, b.style.is("Corners"));
                    if (b.fill.get()) Draw.fill(bb, Colors.argb(c, b.fillAlpha.f() * fade));
                }
                if (m.tracers.enabled() && (!m.tracers.playersOnly.get() || Esp.isPlayer(e))) {
                    Tracers t = m.tracers;
                    int c = Esp.color(e, t.color);
                    Draw.line(eye, bb.getCenter(), Colors.argb(c, 0f), Colors.argb(c, t.opacity.f() * fade), t.thickness.f());
                }
            }
            if (m.storage.enabled()) storage(m.storage, mc, cam);
            if (m.baseFinder.enabled()) m.baseFinder.renderWorld(cam, pt);
        } finally {
            Draw.end();
        }
    }

    private static void storage(StorageEsp s, Minecraft mc, Vec3 cam) {
        double r2 = s.range.get() * s.range.get();
        int cr = (int) Math.ceil(s.range.get() / 16.0);
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    int rgb = s.colorFor(be);
                    if (rgb < 0) continue;
                    BlockPos p = be.getBlockPos();
                    double d2 = mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d2 > r2) continue;
                    float fade = (float) Mth.clamp(1.0 - d2 / r2, 0.25, 1.0);
                    AABB bb = new AABB(p).inflate(-0.04).move(-cam.x, -cam.y, -cam.z);
                    Draw.box(bb, Colors.argb(rgb, 0.09f * fade), 2.4f, true);
                    Draw.box(bb, Colors.argb(rgb, 0.8f * fade), 0.9f, true);
                    if (s.fill.get()) Draw.fill(bb, Colors.argb(rgb, 0.07f * fade));
                }
            }
        }
    }

    private static void item(ItemEsp s, ItemEntity ie, Vec3 cam, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.distanceTo(ie) > s.range.get()) return;
        ItemStack st = ie.getItem();
        int rgb = value(st);
        if (s.valuableOnly.get() && rgb == 0xCFCFCF) return;
        double x = Mth.lerp(pt, ie.xOld, ie.getX()), y = Mth.lerp(pt, ie.yOld, ie.getY()), z = Mth.lerp(pt, ie.zOld, ie.getZ());
        AABB bb = new AABB(x - 0.17, y, z - 0.17, x + 0.17, y + 0.34, z + 0.17).move(-cam.x, -cam.y, -cam.z);
        Draw.box(bb, Colors.argb(rgb, 0.1f), 2.2f, true);
        Draw.box(bb, Colors.argb(rgb, 0.85f), 0.8f, true);
    }

    /** Rough tiering by item id so the highlight colour means something at a glance. */
    private static int value(ItemStack st) {
        String id = st.getItem().builtInRegistryHolder().key().identifier().getPath();
        if (id.contains("netherite") || id.contains("elytra") || id.contains("totem") || id.contains("enchanted_golden_apple")) return 0xFF7AD9;
        if (id.contains("diamond") || id.contains("emerald") || id.contains("beacon")) return 0x7FE6FF;
        if (id.contains("gold") || id.contains("golden_apple") || id.contains("ender_pearl")) return 0xFFD479;
        if (id.contains("iron")) return 0xE8E8E8;
        return 0xCFCFCF;
    }
}
