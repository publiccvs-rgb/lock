package lock.mixin;

import lock.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void lock$teamColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Core.teamColor(cir.getReturnValueI(), (Entity) (Object) this));
    }

    /** Freecam: mouse movement steers the camera, not the body. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void lock$turn(double dx, double dy, CallbackInfo ci) {
        if (Core.modules == null) return;
        var f = Core.modules.freecam;
        if (f.enabled() && f.active && (Object) this == Minecraft.getInstance().player) {
            f.turn(dx, dy);
            ci.cancel();
        }
    }
}
