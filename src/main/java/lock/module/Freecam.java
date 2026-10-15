package lock.module;

import lock.setting.*;

public class Freecam extends Module {
    public final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.2, 5.0, 0.1)).d("Camera flight speed in blocks per tick.");
    public final BoolSetting body = add(new BoolSetting("Show Body", true)).d("Keeps rendering your own model where you stand.");
    public final BoolSetting stopOnHit = add(new BoolSetting("Exit On Damage", true)).d("Returns the camera when you take damage.");

    public double x, y, z;
    public float yaw, pitch;
    public boolean active;

    public Freecam() { super("Freecam", "Detach the camera from your body", false); }
}
