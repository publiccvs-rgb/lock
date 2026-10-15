package lock.mixin;

import lock.LockClient;
import lock.module.Esp;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void lock$outline(Entity e, CallbackInfoReturnable<Boolean> cir) {
        var o = LockClient.modules.outlines;
        if (!o.enabled() || !Esp.targets(e)) return;
        if (o.playersOnly.get() && !(e instanceof Player)) return;
        cir.setReturnValue(true);
    }
}
