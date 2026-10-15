package lock.module;

import lock.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/** Shared accent colour and target filter used by every entity ESP module. */
public class General extends Module {
    public final ColorSetting accent = add(new ColorSetting("Accent", 0x14F2BE));
    public final BoolSetting players = add(new BoolSetting("Players", true));
    public final BoolSetting hostiles = add(new BoolSetting("Hostiles", false));
    public final BoolSetting passives = add(new BoolSetting("Passives", false));
    public final BoolSetting invisible = add(new BoolSetting("Invisible", true));
    public final BoolSetting ignoreNpcs = add(new BoolSetting("Ignore NPCs", true));
    public final BoolSetting teammates = add(new BoolSetting("Teammates", true));
    public final NumSetting range = add(new NumSetting("Range", 96, 16, 256, 8));

    public General() { super("General", "Accent colour and who gets drawn", true); }

    public boolean valid(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) return false;
        if (mc.player.distanceTo(e) > range.get()) return false;
        if (!invisible.get() && e.isInvisible()) return false;
        if (e instanceof Player p) {
            if (!players.get()) return false;
            if (ignoreNpcs.get() && mc.getConnection() != null
                && mc.getConnection().getPlayerInfo(p.getUUID()) == null) return false;
            return teammates.get() || !mc.player.isAlliedTo(p);
        }
        if (e instanceof Mob m) {
            boolean hostile = m.getType().getCategory() == MobCategory.MONSTER;
            return hostile ? hostiles.get() : passives.get();
        }
        return false;
    }
}
