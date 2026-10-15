package lock.gui;

import lock.Core;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class LockScreen extends Screen {
    private final GuiView view = new GuiView(Core.modules);
    private long last = System.nanoTime();
    private float dim;
    private boolean closing;

    public LockScreen() { super(Component.literal("lock.")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        long now = System.nanoTime();
        float dt = Math.min((now - last) / 1e9f, 0.1f);
        last = now;
        // background dim follows the menu fade
        dim += ((closing ? 0f : 1f) - dim) * (1f - (float) Math.exp(-dt * 14f));
        int a = (int) (0x2E * dim);
        if (a > 0) g.fill(0, 0, width, height, a << 24);
        view.render(new McGfx(g), mx, my, dt, width, height);
        if (closing && view.closed()) {
            Core.modules.save();
            super.onClose();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return view.click(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        view.drag(e.x(), e.y());
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        view.release();
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        view.scroll(mx, my, sy);
        return true;
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        view.close();
    }
}
