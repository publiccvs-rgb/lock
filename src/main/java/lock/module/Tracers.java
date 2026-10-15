package lock.module;

import lock.setting.*;

public class Tracers extends Module {
    public final ModeSetting color = add(Esp.colorMode()).d("What the line is tinted by.");
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true)).d("Only draw tracers toward players.");
    public final NumSetting thickness = add(new NumSetting("Thickness", 0.75, 0.25, 3.0, 0.25)).d("Line width in screen pixels.");
    public final NumSetting opacity = add(new NumSetting("Opacity", 0.6, 0.1, 1.0, 0.05)).d("Opacity at the target end; the line fades in from the crosshair.");

    public Tracers() { super("Tracers", "Fading lines toward targets", true); }
}
