package lock.mixin;

import lock.LockClient;
import lock.module.Esp;
import lock.render.CharmLayers;
import lock.render.Colors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
    /** Tint of the entity currently being drawn; 0 when charms don't apply. Render is single threaded. */
    @org.spongepowered.asm.mixin.Unique private static int lock$tint;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void lock$extract(T entity, S state, float pt, CallbackInfo ci) {
        var c = LockClient.modules.charms;
        int tint = 0;
        if (c.enabled() && entity instanceof Player && Esp.targets(entity)) {
            tint = Colors.argb(Esp.color(entity, c.color), c.opacity.f());
        }
        ((CharmState) state).lock$setCharm(tint);
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void lock$begin(S state, com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buf, int light, CallbackInfo ci) {
        lock$tint = ((CharmState) state).lock$getCharm();
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void lock$end(S state, com.mojang.blaze3d.vertex.PoseStack ps, net.minecraft.client.renderer.MultiBufferSource buf, int light, CallbackInfo ci) {
        lock$tint = 0;
    }

    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void lock$layer(S state, boolean visible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (lock$tint == 0 || cir.getReturnValue() == null) return;
        var self = (LivingEntityRenderer<T, S, ?>) (Object) this;
        cir.setReturnValue(CharmLayers.get(self.getTextureLocation(state), LockClient.modules.charms.throughWalls.get()));
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
               index = 4)
    private int lock$color(int original) {
        return lock$tint != 0 ? lock$tint : original;
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void lock$hideTag(T entity, double distSq, CallbackInfoReturnable<Boolean> cir) {
        var h = LockClient.modules.health;
        if (h.enabled() && h.hideVanillaTag.get() && entity instanceof Player && Esp.targets(entity)) {
            cir.setReturnValue(false);
        }
    }
}
