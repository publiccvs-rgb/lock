package lock.module;

import lock.setting.*;

public class Zoom extends Module {
    public final NumSetting factor = add(new NumSetting("Factor", 3.0, 1.5, 10.0, 0.5)).d("How strongly the view is magnified.");
    public final BoolSetting scroll = add(new BoolSetting("Scroll To Adjust", true)).d("Mouse wheel changes the zoom while held.");
    public final BoolSetting smooth = add(new BoolSetting("Smooth", true)).d("Eases in and out instead of snapping.");

    public double current = 1.0;

    public Zoom() { super("Zoom", "Magnify the view while active", false); }
}
