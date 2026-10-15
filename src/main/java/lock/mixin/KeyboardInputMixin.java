package lock.mixin;

import lock.Core;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    /** While Freecam is active the body stays put: movement keys steer the camera instead. */
    @Inject(method = "tick", at = @At("RETURN"))
    private void lock$freeze(CallbackInfo ci) {
        if (Core.modules == null || !Core.modules.freecam.enabled() || !Core.modules.freecam.active) return;
        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
    }
}
