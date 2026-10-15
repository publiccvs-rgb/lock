package lock.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import lock.Core;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    /** After the frame graph has run: the world is on the main target, matrices are still the world's. */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void lock$after(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                            Matrix4f view, Matrix4f proj, Matrix4f cullProj, GpuBufferSlice fog, Vector4f fogColor,
                            boolean flag, CallbackInfo ci) {
        Core.world(camera, delta, view, proj);
    }
}
