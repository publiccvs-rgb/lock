package dev.lock.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;

/** Uploads Java2D-rasterised images as linearly filtered GUI textures. */
public final class Tex {
    private Tex() {}

    /** A dynamic texture sampled with linear filtering so it scales smoothly. */
    static final class LinearTexture extends DynamicTexture {
        LinearTexture(String name, NativeImage img) {
            super(() -> name, img);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    /**
     * Registers {@code img} under lock:{name}. Colour channels are forced to white (only alpha is
     * kept) because everything Lock draws from these textures is tinted, and white RGB avoids dark
     * fringes when transparent edges are filtered.
     */
    /** Images rasterised off-thread, waiting to be uploaded on the render thread. */
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Schedules work that must run on the render thread (texture uploads). */
    public static void onRenderThread(Runnable r) { PENDING.add(r); }

    /** Uploads at most {@code max} queued textures; called once per frame so warm-up never causes a hitch. */
    public static void drain(int max) {
        for (int i = 0; i < max; i++) {
            Runnable r = PENDING.poll();
            if (r == null) return;
            r.run();
        }
    }

    public static Identifier upload(String name, BufferedImage img, boolean whiten) {
        int w = img.getWidth(), h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                ni.setPixel(x, y, whiten ? (argb & 0xFF000000) | 0x00FFFFFF : argb);
            }
        Identifier id = Identifier.fromNamespaceAndPath("lock", name);
        Minecraft.getInstance().getTextureManager().register(id, new LinearTexture("lock:" + name, ni));
        return id;
    }
}
