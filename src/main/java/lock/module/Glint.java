package lock.module;

import com.mojang.blaze3d.platform.NativeImage;
import lock.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Recolors the enchantment glint. The glint is a scrolling texture, so the shimmer is re-tinted by swapping in a
 * recolored copy of the vanilla textures; strength and speed use the game's own glint options.
 */
public class Glint extends Module {
    public final ModeSetting color = add(new ModeSetting("Color", "Accent", "Accent", "Rainbow", "Aurora", "Custom")).d("Tint of the enchantment shimmer.");
    public final ColorSetting custom = add(new ColorSetting("Custom Color", 0x22D3EE)).d("Used when Color is set to Custom.");
    public final NumSetting intensity = add(new NumSetting("Intensity", 1.0, 0.2, 2.5, 0.1)).d("Brightness of the glint.");
    public final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.1, 4.0, 0.1)).d("How quickly the shimmer sweeps across.");

    private static final Identifier[] TEX = {
        Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"),
        Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png"),
    };
    private final Map<Identifier, NativeImage> originals = new HashMap<>();
    private int lastRgb = -1, tickCount;
    private double lastStrength = -1;
    private double savedStrength = -1, savedSpeed = -1;

    public Glint() { super("EnchantShader", "Recolor the enchantment glint", Category.RENDER, false); }

    public int rgb() {
        long t = System.currentTimeMillis();
        return switch (color.get()) {
            case "Rainbow" -> java.awt.Color.HSBtoRGB((float) ((t * 0.00006 * speed.get()) % 1.0), 0.65f, 1f) & 0xFFFFFF;
            case "Aurora" -> lock.render.Colors.lerp(0x22D3EE, 0x9B7BFF, (float) (0.5 + 0.5 * Math.sin(t * 0.0012 * speed.get())));
            case "Custom" -> custom.get();
            default -> lock.Core.modules.general.accent.get();
        };
    }

    @Override public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        savedStrength = mc.options.glintStrength().get();
        savedSpeed = mc.options.glintSpeed().get();
        lastRgb = -1; lastStrength = -1;
    }

    @Override public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (savedStrength >= 0) mc.options.glintStrength().set(savedStrength);
        if (savedSpeed >= 0) mc.options.glintSpeed().set(savedSpeed);
        for (Identifier id : TEX) {
            NativeImage o = originals.get(id);
            if (o != null) mc.getTextureManager().register(id, new DynamicTexture(() -> "lock_glint_restore", copy(o, 0xFFFFFF, 1.0)));
        }
        lastRgb = -1;
    }

    @Override public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (++tickCount % 2 != 0) return;
        int rgb = rgb();
        double strength = Math.min(1.0, 0.75 * Math.min(intensity.get(), 1.33));
        mc.options.glintStrength().set(strength);
        mc.options.glintSpeed().set(Math.max(0, Math.min(1, speed.get() * 0.5)));
        double boost = Math.max(1.0, intensity.get() / 1.33);
        if (rgb == lastRgb && Math.abs(boost - lastStrength) < 1e-6) return;
        lastRgb = rgb; lastStrength = boost;
        for (Identifier id : TEX) {
            NativeImage o = originals.computeIfAbsent(id, this::load);
            if (o == null) continue;
            mc.getTextureManager().register(id, new DynamicTexture(() -> "lock_glint", copy(o, rgb, boost)));
        }
    }

    private NativeImage load(Identifier id) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id).open()) {
            return NativeImage.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static NativeImage copy(NativeImage src, int rgb, double boost) {
        NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), false);
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getPixel(x, y);
                int a = p >>> 24, pr = (p >> 16) & 255, pg = (p >> 8) & 255, pb = p & 255;
                int lum = (pr + pg + pb) / 3;
                int nr = Math.min(255, (int) (lum * r * boost)), ng = Math.min(255, (int) (lum * g * boost)), nb = Math.min(255, (int) (lum * b * boost));
                out.setPixel(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }
}
