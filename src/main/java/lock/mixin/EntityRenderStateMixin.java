package lock.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements CharmState {
    @Unique private int lock$charm;
    @Override public int lock$getCharm() { return lock$charm; }
    @Override public void lock$setCharm(int argb) { lock$charm = argb; }
}
