package lock.module;

import lock.setting.*;

/** Edge-of-screen pointers toward players that are off screen. */
public class Arrows extends Module {
    public final ModeSetting color = add(Esp.colorMode()).d("Tint of the pointers.");
    public final NumSetting radius = add(new NumSetting("Radius", 0.8, 0.4, 0.95, 0.05)).d("How far from the center the pointers sit.");
    public final NumSetting size = add(new NumSetting("Size", 1.0, 0.6, 1.6, 0.1)).d("Pointer size.");
    public final BoolSetting distance = add(new BoolSetting("Distance", true)).d("Shows blocks away under each pointer.");

    public Arrows() { super("Arrows", "Pointers to players off screen", false); }
}
