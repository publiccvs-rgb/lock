package lock.module;

import lock.setting.*;

public class Health extends Module {
    public final BoolSetting number = add(new BoolSetting("Number", false));
    public final BoolSetting name = add(new BoolSetting("Name", false));
    public final BoolSetting trail = add(new BoolSetting("Damage Trail", true));
    public final BoolSetting hideVanillaTag = add(new BoolSetting("Hide Nametag", true));
    public final ModeSetting color = add(new ModeSetting("Color", "Health", "Health", "Accent", "Team"));
    public final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.6, 1.5, 0.1));

    public Health() { super("Health", "Slim health bar above heads", true); }
}
