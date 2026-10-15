package lock.mixin;

import lock.Core;
import lock.module.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @org.spongepowered.asm.mixin.gen.Invoker("setPosition") abstract void lock$setPos(double x, double y, double z);
    @org.spongepowered.asm.mixin.gen.Invoker("setRotation") abstract void lock$setRot(float yaw, float pitch);

    /** Freecam: after vanilla places the camera on the player, move it to the free position. */
    @Inject(method = "setup", at = @At("RETURN"))
    private void lock$freecam(Level level, Entity entity, boolean thirdPerson, boolean mirrored, float partial, CallbackInfo ci) {
        if (Core.modules == null) return;
        Freecam f = Core.modules.freecam;
        if (!f.enabled() || !f.active) return;
        lock$setPos(f.x, f.y, f.z);
        lock$setRot(f.yaw, f.pitch);
    }
}
