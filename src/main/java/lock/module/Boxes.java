package lock.module;

import lock.setting.*;

public class Boxes extends Module {
    public final ModeSetting style = add(new ModeSetting("Style", "Corners", "Corners", "Outline"));
    public final ModeSetting color = add(Esp.colorMode());
    public final BoolSetting fill = add(new BoolSetting("Fill", true));
    public final NumSetting fillAlpha = add(new NumSetting("Fill Alpha", 0.08, 0.02, 0.4, 0.02));
    public final NumSetting thickness = add(new NumSetting("Thickness", 1.0, 0.5, 3.0, 0.25));

    public Boxes() { super("Boxes", "Thin corner boxes around entities", true); }
}
