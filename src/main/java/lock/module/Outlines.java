package lock.module;

import lock.setting.*;

public class Outlines extends Module {
    public final ModeSetting color = add(Esp.colorMode());
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true));

    public Outlines() { super("Outlines", "Soft glow outline around entities", false); }
}
