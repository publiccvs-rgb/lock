package lock.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Render types for the Charms module; extends RenderStateShard only to reach its protected constants. */
public final class CharmLayers extends RenderStateShard {
    private static final Map<ResourceLocation, RenderType> THROUGH = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> NORMAL = new HashMap<>();

    private CharmLayers() { super("lock_charm", () -> {}, () -> {}); }

    public static RenderType get(ResourceLocation tex, boolean throughWalls) {
        return (throughWalls ? THROUGH : NORMAL).computeIfAbsent(tex, t -> build(t, throughWalls));
    }

    private static RenderType build(ResourceLocation tex, boolean throughWalls) {
        RenderType.CompositeState.CompositeStateBuilder b = RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(tex, net.minecraft.util.TriState.FALSE, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .setWriteMaskState(COLOR_WRITE);
        if (throughWalls) b.setDepthTestState(NO_DEPTH_TEST);
        return RenderType.create(throughWalls ? "lock_charm_xray" : "lock_charm", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, true, true, b.createCompositeState(true));
    }
}
