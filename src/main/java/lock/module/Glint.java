package lock.module;

import lock.setting.*;

/** Recolors the enchantment glint on items and armor, with speed and strength controls. */
public class Glint extends Module {
    public final ModeSetting color = add(new ModeSetting("Color", "Accent", "Accent", "Rainbow", "Aurora", "Custom")).d("Tint of the enchantment shimmer.");
    public final ColorSetting custom = add(new ColorSetting("Custom Color", 0x22D3EE)).d("Used when Color is set to Custom.");
    public final NumSetting intensity = add(new NumSetting("Intensity", 1.0, 0.2, 2.5, 0.1)).d("Brightness of the glint.");
    public final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.1, 4.0, 0.1)).d("How quickly the shimmer sweeps across.");

    public Glint() { super("EnchantShader", "Recolor the enchantment glint", false); }

    public int rgb() {
        long t = System.currentTimeMillis();
        return switch (color.get()) {
            case "Rainbow" -> java.awt.Color.HSBtoRGB((float) ((t * 0.00006 * speed.get()) % 1.0), 0.65f, 1f) & 0xFFFFFF;
            case "Aurora" -> lock.render.Colors.lerp(0x22D3EE, 0x9B7BFF, (float) (0.5 + 0.5 * Math.sin(t * 0.0012 * speed.get())));
            case "Custom" -> custom.get();
            default -> lock.Core.modules.general.accent.get();
        };
    }
}
