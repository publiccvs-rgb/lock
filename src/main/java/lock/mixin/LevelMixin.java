package lock.mixin;

import lock.Core;
import lock.module.Ambience;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ambience: time of day and weather overrides, client level only (nothing is sent to the server). */
@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void lock$time(CallbackInfoReturnable<Long> cir) {
        if (Core.modules == null || !((Object) this instanceof ClientLevel)) return;
        Ambience a = Core.modules.ambience;
        if (!a.enabled() || a.time.is("Auto")) return;
        long target = switch (a.time.get()) {
            case "Day" -> 6000L;
            case "Sunset" -> 12800L;
            case "Night" -> 16000L;
            default -> 18000L;
        };
        cir.setReturnValue(cir.getReturnValueJ() / 24000L * 24000L + target);
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void lock$rain(float partial, CallbackInfoReturnable<Float> cir) {
        if (lock$clear()) cir.setReturnValue(0f);
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void lock$thunder(float partial, CallbackInfoReturnable<Float> cir) {
        if (lock$clear()) cir.setReturnValue(0f);
    }

    private boolean lock$clear() {
        if (Core.modules == null || !((Object) this instanceof ClientLevel)) return false;
        Ambience a = Core.modules.ambience;
        return a.enabled() && a.weather.is("Clear");
    }
}
