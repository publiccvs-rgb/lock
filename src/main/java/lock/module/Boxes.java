package lock.module;

import lock.setting.*;

public class Boxes extends Module {
    public final ModeSetting style = add(new ModeSetting("Style", "Corners", "Corners", "Outline")).d("Corner brackets or a full outline around each entity.");
    public final ModeSetting color = add(Esp.colorMode()).d("What the outline is tinted by.");
    public final BoolSetting fill = add(new BoolSetting("Fill", true)).d("Adds a faint translucent fill inside the box.");
    public final NumSetting fillAlpha = add(new NumSetting("Fill Alpha", 0.08, 0.02, 0.4, 0.02)).d("Opacity of the fill.");
    public final NumSetting thickness = add(new NumSetting("Thickness", 1.0, 0.5, 3.0, 0.25)).d("Line width in screen pixels.");

    public Boxes() { super("Boxes", "Thin corner boxes around entities", true); }
}
