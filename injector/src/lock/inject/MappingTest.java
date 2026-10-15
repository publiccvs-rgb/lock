package lock.inject;

import java.io.FileReader;
import java.io.IOException;

/** Self-check against a real downloaded Mojang client mapping file. Args: path to client.txt */
public final class MappingTest {
    static int fails;
    static void check(boolean ok, String msg) { if (!ok) fails++; System.out.println((ok ? "  ok   " : "  FAIL ") + msg); }

    public static void main(String[] args) throws IOException {
        long t0 = System.nanoTime();
        Mapping m = Mapping.parse(new FileReader(args[0]));
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("parsed %d classes, %d members in %.0fms%n", m.classCount(), m.memberCount(), ms);

        var mc = m.clazz("net.minecraft.client.Minecraft");
        check(mc != null, "Minecraft class resolves");
        System.out.println("  Minecraft -> " + (mc != null ? mc.obfuscated() : "?"));

        var tick = m.method("net.minecraft.client.Minecraft", "tick");
        check(tick != null, "Minecraft.tick() resolves");
        if (tick != null) {
            System.out.println("  tick() -> " + tick.obfuscated() + "  descriptor " + m.obfMethodDescriptor(tick));
            check(m.obfMethodDescriptor(tick).equals("()V"), "tick() descriptor is ()V");
        }

        var glow = m.method("net.minecraft.client.Minecraft", "shouldEntityAppearGlowing", "net.minecraft.world.entity.Entity");
        check(glow != null, "Minecraft.shouldEntityAppearGlowing(Entity) resolves");
        if (glow != null) {
            String d = m.obfMethodDescriptor(glow);
            System.out.println("  shouldEntityAppearGlowing -> " + glow.obfuscated() + "  descriptor " + d);
            check(d.startsWith("(L") && d.endsWith(";)Z"), "descriptor shape is (L...;)Z");
            var entityClazz = m.clazz("net.minecraft.world.entity.Entity");
            check(entityClazz != null && d.contains(entityClazz.obfuscated().replace('.', '/')), "descriptor embeds Entity's real obfuscated name");
        }

        var teamColor = m.method("net.minecraft.world.entity.Entity", "getTeamColor");
        check(teamColor != null, "Entity.getTeamColor() resolves");
        if (teamColor != null) System.out.println("  Entity.getTeamColor() -> " + teamColor.obfuscated() + "  " + m.obfMethodDescriptor(teamColor));

        var levelRenderer = m.clazz("net.minecraft.client.renderer.LevelRenderer");
        check(levelRenderer != null, "LevelRenderer class resolves");
        var renderLevel = levelRenderer == null ? null : m.method("net.minecraft.client.renderer.LevelRenderer", "renderLevel",
            "com.mojang.blaze3d.resource.GraphicsResourceAllocator", "net.minecraft.client.DeltaTracker", "boolean",
            "net.minecraft.client.Camera", "org.joml.Matrix4f", "org.joml.Matrix4f", "org.joml.Matrix4f",
            "com.mojang.blaze3d.buffers.GpuBufferSlice", "org.joml.Vector4f", "boolean");
        check(renderLevel != null, "LevelRenderer.renderLevel(...) resolves with the full real signature");
        if (renderLevel != null) System.out.println("  renderLevel(...) -> " + renderLevel.obfuscated() + "  " + m.obfMethodDescriptor(renderLevel));

        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
