package lock.mixin;

import lock.Core;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lock$fov(Camera camera, float partial, boolean useConfigured, CallbackInfoReturnable<Float> cir) {
        if (Core.modules == null) return;
        double z = Core.modules.zoom.factor();
        if (z > 1.001) cir.setReturnValue((float) (cir.getReturnValueF() / z));
    }
}
