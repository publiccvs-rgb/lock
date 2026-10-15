package lock.mixin;

import lock.Core;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void lock$extract(T entity, S state, float pt, CallbackInfo ci) { Core.extract(entity, state); }

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void lock$layer(S state, boolean visible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        cir.setReturnValue(Core.layer(cir.getReturnValue(), (LivingEntityRenderer<?, ?, ?>) (Object) this, state));
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void lock$tint(S state, CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(Core.tint(cir.getReturnValueI(), state)); }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void lock$name(T entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Core.nameTag(cir.getReturnValueZ(), entity));
    }
}
