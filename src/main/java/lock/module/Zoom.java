package lock.module;

import lock.setting.*;

public class Zoom extends Module {
    public final NumSetting factor = add(new NumSetting("Factor", 3.0, 1.5, 10.0, 0.5)).d("How strongly the view is magnified.");
    public final BoolSetting smooth = add(new BoolSetting("Smooth", true)).d("Eases in and out instead of snapping.");

    private double current = 1.0;
    private long last = System.nanoTime();

    public Zoom() { super("Zoom", "Magnify the view while active", false); }

    /** Per-frame smoothed zoom factor (1.0 = off). Called from the FOV hook, so it also eases back out. */
    public double factor() {
        long now = System.nanoTime();
        double dt = Math.min((now - last) / 1e9, 0.1);
        last = now;
        double target = enabled() ? factor.get() : 1.0;
        if (!smooth.get()) current = target;
        else current += (target - current) * (1 - Math.exp(-dt * 14));
        if (Math.abs(current - target) < 0.002) current = target;
        return current;
    }
}
