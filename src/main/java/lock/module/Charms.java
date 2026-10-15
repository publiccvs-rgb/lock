package lock.module;

import lock.setting.*;

public class Charms extends Module {
    public final ModeSetting color = add(Esp.colorMode());
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true));
    public final NumSetting opacity = add(new NumSetting("Opacity", 0.45, 0.1, 0.9, 0.05));
    public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", true));

    public Charms() { super("Charms", "Tinted see-through player models", true); }
}
