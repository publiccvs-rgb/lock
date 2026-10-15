package lock.module;

import lock.setting.*;
import net.minecraft.world.level.block.entity.*;

public class StorageEsp extends Module {
    public final BoolSetting chests = add(new BoolSetting("Chests", true)).d("Marks chests.");
    public final BoolSetting trapped = add(new BoolSetting("Trapped", true)).d("Marks trapped chests.");
    public final BoolSetting ender = add(new BoolSetting("Ender", true)).d("Marks ender chests.");
    public final BoolSetting barrels = add(new BoolSetting("Barrels", true)).d("Marks barrels.");
    public final BoolSetting shulkers = add(new BoolSetting("Shulkers", true)).d("Marks shulker boxes.");
    public final BoolSetting misc = add(new BoolSetting("Furnaces/Hoppers", false)).d("Also marks furnaces, hoppers and dispensers.");
    public final BoolSetting fill = add(new BoolSetting("Fill", true)).d("Adds a faint translucent fill.");
    public final NumSetting range = add(new NumSetting("Range", 64, 16, 128, 8)).d("Containers further than this are not drawn (blocks).");

    public StorageEsp() { super("StorageESP", "Containers through walls", true); }

    /** RGB for a container the user wants drawn, or -1 to skip it. */
    public int colorFor(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return trapped.get() ? 0xFF8F8F : -1;
        if (be instanceof ChestBlockEntity) return chests.get() ? 0xFFD98A : -1;
        if (be instanceof EnderChestBlockEntity) return ender.get() ? 0xC7A1FF : -1;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? 0xD9B08A : -1;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? 0xFF9DE0 : -1;
        if (be instanceof AbstractFurnaceBlockEntity || be instanceof HopperBlockEntity
            || be instanceof DispenserBlockEntity) return misc.get() ? 0x9FB4C7 : -1;
        return -1;
    }
}
