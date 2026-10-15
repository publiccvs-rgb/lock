package lock.module;

import lock.setting.*;

public class Outlines extends Module {
    public final ModeSetting color = add(Esp.colorMode()).d("What the glow follows.");
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true)).d("Only outline players.");

    public Outlines() { super("Outlines", "Soft glow outline around entities", Category.PLAYER, false); }
}
