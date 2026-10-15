package lock.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Client-side night vision; never sent to the server. */
public class Fullbright extends Module {
    public Fullbright() { super("Fullbright", "See in the dark", Category.RENDER, false); }

    @Override public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (enabled()) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
        }
    }

    @Override public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
    }
}
