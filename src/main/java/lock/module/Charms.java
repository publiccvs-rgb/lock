package lock.module;

import lock.setting.*;

public class Charms extends Module {
    public final ModeSetting color = add(Esp.colorMode()).d("What the model tint follows.");
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true)).d("Only tint player models.");
    public final NumSetting opacity = add(new NumSetting("Opacity", 0.45, 0.1, 0.9, 0.05)).d("How strongly the tint covers the model.");
    public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", true)).d("Draws the tinted model even when blocks are in the way.");

    public Charms() { super("Chams", "Tinted see-through player models", Category.PLAYER, true); }
}
