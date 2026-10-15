package lock.mixin;

import lock.Core;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void lock$teamColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Core.teamColor(cir.getReturnValueI(), (Entity) (Object) this));
    }
}
