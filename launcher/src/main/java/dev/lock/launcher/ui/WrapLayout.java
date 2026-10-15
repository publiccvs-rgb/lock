package dev.lock.launcher.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/** FlowLayout whose preferred size accounts for wrapping, so rows of chips grow downwards. */
public final class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override public Dimension preferredLayoutSize(Container target) { return size(target); }

    @Override public Dimension minimumLayoutSize(Container target) { return size(target); }

    private Dimension size(Container target) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth();
            if (width == 0 && target.getParent() != null) width = target.getParent().getWidth();
            if (width == 0) width = Integer.MAX_VALUE;
            Insets in = target.getInsets();
            int max = width - in.left - in.right - getHgap() * 2;
            int rowW = 0, rowH = 0, w = 0, h = in.top + in.bottom + getVgap() * 2;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (rowW + d.width > max && rowW > 0) {
                    w = Math.max(w, rowW);
                    h += rowH + getVgap();
                    rowW = 0;
                    rowH = 0;
                }
                rowW += d.width + getHgap();
                rowH = Math.max(rowH, d.height);
            }
            w = Math.max(w, rowW);
            h += rowH;
            return new Dimension(w + in.left + in.right, h);
        }
    }
}
