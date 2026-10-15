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
        if (Core.modules == null || !Core.modules.zoom.enabled()) return;
        cir.setReturnValue((float) (cir.getReturnValueF() / Core.modules.zoom.current));
    }
}
