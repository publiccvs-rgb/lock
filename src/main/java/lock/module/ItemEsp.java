package lock.module;

import lock.setting.*;

public class ItemEsp extends Module {
    public final BoolSetting valuableOnly = add(new BoolSetting("Valuables Only", true)).d("Hides common drops; keeps tools, gold, diamonds and rare items.");
    public final NumSetting range = add(new NumSetting("Range", 48, 8, 128, 8)).d("Items further than this are not drawn (blocks).");

    public ItemEsp() { super("ItemESP", "Marks dropped items", Category.WORLD, false); }
}
