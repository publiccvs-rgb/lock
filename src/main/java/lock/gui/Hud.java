package lock.gui;

import lock.Core;
import lock.basefinder.Verdict;
import lock.module.BaseFinder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Everything drawn on the in-game HUD by the client (finder overlay, tags, pointers). */
public final class Hud {
    private Hud() {}

    private static float cardAlpha;
    private static Verdict lastHover;
    private static long last = System.nanoTime();

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || Core.modules == null) return;
        long now = System.nanoTime();
        float dt = Math.min((now - last) / 1e9f, 0.1f);
        last = now;
        McGfx gfx = new McGfx(g);
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int accent = 0xFF000000 | Core.modules.general.accent.get();

        BaseFinder bf = Core.modules.baseFinder;
        if (bf.enabled()) finder(gfx, bf, mc, sw, sh, accent, dt);
        lock.gui.OverlayHud.render(gfx, g, mc, sw, sh, accent, dt);
    }

    private static void finder(Gfx g, BaseFinder bf, Minecraft mc, int sw, int sh, int accent, float dt) {
        double px = mc.player.getX(), pz = mc.player.getZ();
        List<Verdict> list = bf.shown;
        int low = bf.low.get(), high = bf.high.get();
        if (bf.targets.get()) {
            float x = bf.panelX.i() < 0 ? sw - FinderHud.LIST_W - 10 : bf.panelX.i();
            float y = bf.panelY.i() < 0 ? 10 : bf.panelY.i();
            FinderHud.targets(g, list, bf.targetCount.i(), x, y, accent, low, high, px, pz, bf.scannedChunks, bf.queued, bf.hover);
        }
        Verdict hv = bf.hover;
        if (hv != null) lastHover = hv;
        cardAlpha += ((hv != null ? 1f : 0f) - cardAlpha) * (1f - (float) Math.exp(-dt * 16f));
        if (cardAlpha > 0.02f && lastHover != null) {
            float[] s = bf.hoverScreen != null ? bf.hoverScreen : new float[] {sw / 2f, sh / 2f};
            float ch = FinderHud.cardHeight(lastHover);
            float x = s[0] + 18, y = s[1] - ch / 2f;
            if (x + FinderHud.CARD_W > sw - 6) x = s[0] - 18 - FinderHud.CARD_W;
            y = Math.max(6, Math.min(sh - ch - 6, y));
            FinderHud.card(g, lastHover, x, y + (1f - cardAlpha) * 4f, accent, low, high, px, pz, cardAlpha);
        }
    }
}
