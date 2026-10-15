package lock.render;

import lock.Core;
import lock.module.Esp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Chams: tinted, translucent entity models, optionally drawn through walls. */
public final class CharmSystem {
    private CharmSystem() {}

    private static final Map<Object, Integer> TINTS = new WeakHashMap<>();
    private static final Map<Identifier, RenderType> XRAY = new HashMap<>();
    private static RenderPipeline xrayPipeline;

    public static void extract(LivingEntity e, LivingEntityRenderState state) {
        var c = Core.modules.charms;
        if (c.enabled() && (!c.playersOnly.get() || e instanceof Player) && Esp.targets(e)) {
            TINTS.put(state, Colors.argb(Esp.color(e, c.color), c.opacity.f()));
        } else {
            TINTS.remove(state);
        }
    }

    public static int tint(int orig, LivingEntityRenderState state) {
        Integer t = TINTS.get(state);
        return t != null ? t : orig;
    }

    public static RenderType layer(RenderType orig, LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state) {
        if (orig == null || !TINTS.containsKey(state)) return orig;
        @SuppressWarnings({"rawtypes", "unchecked"})
        Identifier tex = ((LivingEntityRenderer) renderer).getTextureLocation(state);
        if (!Core.modules.charms.throughWalls.get()) return RenderTypes.entityTranslucent(tex);
        return XRAY.computeIfAbsent(tex, CharmSystem::build);
    }

    private static RenderType build(Identifier tex) {
        if (xrayPipeline == null) {
            // same shader and state as the vanilla translucent entity pipeline, minus the depth test
            xrayPipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation("pipeline/lock_chams")
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withSampler("Sampler1")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build();
        }
        RenderSetup setup = RenderSetup.builder(xrayPipeline).withTexture("Sampler0", tex).useLightmap().useOverlay().affectsCrumbling().sortOnUpload().createRenderSetup();
        return RenderType.create("lock_chams", setup);
    }
}
