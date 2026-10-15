package lock.module;

import lock.Core;
import lock.render.Colors;
import lock.setting.ColorSetting;
import lock.setting.ModeSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

/** Helpers shared by the entity ESP modules. */
public final class Esp {
    private Esp() {}

    public static final String[] COLOR_MODES = {"Accent", "Team", "Health", "Fade"};

    public static ModeSetting colorMode() { return new ModeSetting("Color", "Accent", COLOR_MODES); }

    /** RGB (no alpha) for an entity under the module's chosen colour mode. */
    public static int color(Entity e, ModeSetting mode) {
        int accent = Core.modules.general.accent.get();
        switch (mode.get()) {
            case "Team" -> {
                if (e.getTeam() instanceof PlayerTeam t && t.getColor().getColor() != null) {
                    return t.getColor().getColor();
                }
                return accent;
            }
            case "Health" -> {
                if (e instanceof LivingEntity le) return Colors.health(le.getHealth() / le.getMaxHealth());
                return accent;
            }
            case "Fade" -> { return Colors.fade(accent); }
            default -> { return accent; }
        }
    }

    public static boolean targets(Entity e) {
        return Core.modules.general.valid(e);
    }

    public static boolean isPlayer(Entity e) { return e instanceof Player; }
}
