package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.gui.LockScreen;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Gfx;

public final class InterfaceModules {
    private InterfaceModules() {}

    public static final class ClickGui extends ClientModule {
        public ClickGui(LockClient l) { super(l, "click_gui"); }

        @Override public void onBind(String id, boolean pressed) {
            if (pressed && id.equals("openKey") && mc.screen == null) mc.setScreen(new LockScreen(null));
        }
    }

    public static final class Watermark extends HudModule {
        public Watermark(LockClient l) { super(l, "watermark"); }

        @Override public int[] size(Gfx gfx) { return new int[]{Math.round(gfx.width("lock.") * 1.5f * textScale()), Math.round(12 * textScale())}; }

        @Override public void draw(Gfx gfx, int w, int h) {
            float s = 1.5f * textScale();
            gfx.push();
            gfx.scale(s);
            int x = gfx.text("lock", 0, 0, textColor(), shadow());
            gfx.text(".", x, 0, lock.theme.accent(), shadow());
            gfx.pop();
        }
    }
}
