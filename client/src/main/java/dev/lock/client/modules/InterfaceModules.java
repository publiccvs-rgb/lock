package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.gui.LockScreen;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.HudModule;
import dev.lock.client.render.Gfx;
import dev.lock.client.render.LockFont;

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

        private float size() { return 13f * textScale(); }

        @Override public int[] size(Gfx gfx) {
            return new int[]{(int) Math.ceil(gfx.width("lock.", size(), LockFont.Weight.SEMIBOLD)) + 1, Math.round(size() * 0.8f)};
        }

        @Override public void draw(Gfx gfx, int w, int h) {
            float x = gfx.textMid("lock", 0, h / 2f, size(), textColor(), LockFont.Weight.SEMIBOLD);
            gfx.textMid(".", x, h / 2f, size(), lock.theme.accent(), LockFont.Weight.SEMIBOLD);
        }
    }
}
