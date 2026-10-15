package lock.module;

import lock.setting.*;

public class ItemEsp extends Module {
    public final BoolSetting valuableOnly = add(new BoolSetting("Valuables Only", true));
    public final NumSetting range = add(new NumSetting("Range", 48, 8, 128, 8));

    public ItemEsp() { super("Items", "Marks dropped items", false); }
}
