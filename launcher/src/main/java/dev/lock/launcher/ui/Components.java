package dev.lock.launcher.ui;

import dev.lock.core.ui.IconSet;
import dev.lock.launcher.Theme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/** Small custom-painted building blocks matching the in-game UI. */
public final class Components {
    private static final float LEFT_ALIGNMENT_VALUE = 0f;

    private Components() {}

    /** Rounded surface. */
    public static class Card extends JPanel {
        private final Color bg;
        private final int arc;

        public Card(LayoutManager l, Color bg, int arc) {
            super(l);
            this.bg = bg;
            this.arc = arc;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            g2.dispose();
        }
    }

    /** Pill switch with an animated round knob. */
    public static class Toggle extends JComponent {
        private boolean on;
        private float t;
        private final Timer anim;
        private Consumer<Boolean> listener;

        public Toggle(boolean on) {
            this.on = on;
            this.t = on ? 1 : 0;
            setPreferredSize(new Dimension(34, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            anim = new Timer(12, e -> {
                float target = this.on ? 1 : 0;
                t += (target - t) * 0.3f;
                if (Math.abs(target - t) < 0.01f) { t = target; ((Timer) e.getSource()).stop(); }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    setOn(!Toggle.this.on);
                    if (listener != null) listener.accept(Toggle.this.on);
                }
            });
        }

        public void onChange(Consumer<Boolean> l) { listener = l; }

        public void setOn(boolean v) {
            if (v == on) return;
            on = v;
            anim.start();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            int w = 34, h = 18, y = (getHeight() - h) / 2;
            g2.setColor(blend(new Color(0x3A3C42), Theme.ACCENT, t));
            g2.fill(new RoundRectangle2D.Float(0, y, w, h, h, h));
            g2.setColor(Color.WHITE);
            float kx = 3 + (w - h) * t;
            g2.fill(new Ellipse2D.Float(kx, y + 3, h - 6, h - 6));
            g2.dispose();
        }
    }

    public static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static JLabel label(String text, float size, int weight, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(size, weight));
        l.setForeground(color);
        return l;
    }

    public static JLabel h1(String text) { return label(text, 20, 2, Theme.TEXT); }

    public static JLabel sub(String text) { return label(text, 12, 0, Theme.DIM); }

    public static JLabel section(String text) {
        JLabel l = label(text.toUpperCase(), 10, 2, Theme.FAINT);
        l.setBorder(BorderFactory.createEmptyBorder(10, 2, 4, 0));
        return l;
    }

    public static JButton button(String text, String icon, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(Theme.font(12, 1));
        if (icon != null) {
            b.setIcon(Theme.icon(icon, 14, Theme.TEXT));
            b.setIconTextGap(7);
        }
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.addActionListener(e -> action.run());
        return b;
    }

    public static JButton primary(String text, String icon, Runnable action) {
        JButton b = button(text, icon, action);
        b.setBackground(Theme.ACCENT);
        b.setForeground(Theme.onAccent());
        if (icon != null) b.setIcon(Theme.icon(icon, 14, Theme.onAccent()));
        return b;
    }

    public static JPanel row(int gap, JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (JComponent c : cs) p.add(c);
        return p;
    }

    /** Panel that always matches the viewport width, so long text truncates instead of scrolling sideways. */
    static final class FitWidth extends JPanel implements javax.swing.Scrollable {
        FitWidth(JComponent c) {
            super(new BorderLayout());
            setOpaque(false);
            add(c, BorderLayout.NORTH);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 30; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    public static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c instanceof javax.swing.JTable || c instanceof javax.swing.JTextArea ? c : new FitWidth(c));
        s.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getViewport().setOpaque(false);
        s.setOpaque(false);
        s.getVerticalScrollBar().setUnitIncrement(18);
        return s;
    }

    /** A status line with a coloured dot: green ok, amber warning, red error. */
    public static JPanel statusLine(String title, String detail, Color dot) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JComponent d = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.aa(g2);
                g2.setColor(new Color(dot.getRed(), dot.getGreen(), dot.getBlue(), 50));
                g2.fill(new Ellipse2D.Float(0, getHeight() / 2f - 7, 14, 14));
                g2.setColor(dot);
                g2.fill(new Ellipse2D.Float(4, getHeight() / 2f - 3, 6, 6));
                g2.dispose();
            }
        };
        d.setPreferredSize(new Dimension(14, 14));
        p.add(d, BorderLayout.WEST);
        JPanel text = new JPanel(new BorderLayout());
        text.setOpaque(false);
        text.add(label(title, 12.5f, 1, Theme.TEXT), BorderLayout.NORTH);
        JLabel det = label(detail, 11, 0, Theme.DIM);
        det.setToolTipText(detail.isEmpty() ? null : detail);
        if (!detail.isEmpty()) text.add(det, BorderLayout.SOUTH);
        p.add(text, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        p.setAlignmentX(LEFT_ALIGNMENT_VALUE);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** Navigation entry for the sidebar: icon, label, chevron; accent when selected. */
    public static class NavItem extends JComponent {
        private final String icon, text;
        private boolean selected, hover;
        private String pill;

        public NavItem(String icon, String text, Runnable onClick) {
            this.icon = icon;
            this.text = text;
            setPreferredSize(new Dimension(200, 34));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
        }

        public void setSelected(boolean s) { selected = s; repaint(); }

        public void setPill(String p) { pill = p; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            if (selected || hover) {
                g2.setColor(new Color(255, 255, 255, selected ? 16 : 10));
                g2.fill(new RoundRectangle2D.Float(8, 2, getWidth() - 16, getHeight() - 4, 10, 10));
            }
            Color c = selected ? Theme.ACCENT : Theme.TEXT;
            g2.setColor(selected ? Theme.ACCENT : Theme.DIM);
            IconSet.paint(g2, icon, 20, getHeight() / 2.0 - 8, 16);
            g2.setColor(c);
            g2.setFont(Theme.font(13, 1));
            int ty = getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
            g2.drawString(text, 46, ty);
            if (pill != null && !pill.isEmpty()) {
                g2.setFont(Theme.font(10.5f, 1));
                int pw = g2.getFontMetrics().stringWidth(pill) + 14;
                int px = getWidth() - 40 - pw;
                g2.setColor(new Color(255, 255, 255, 26));
                g2.fill(new RoundRectangle2D.Float(px, getHeight() / 2f - 9, pw, 18, 18, 18));
                g2.setColor(Theme.DIM);
                g2.drawString(pill, px + 7, getHeight() / 2 + 4);
            }
            g2.setColor(selected ? Theme.ACCENT : Theme.FAINT);
            IconSet.paint(g2, "chevron_right", getWidth() - 34, getHeight() / 2.0 - 6, 12);
            g2.dispose();
        }
    }

    public static Icon dot(Color c, int size) {
        return new Icon() {
            @Override public void paintIcon(java.awt.Component comp, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.aa(g2);
                g2.setColor(c);
                g2.fill(new Ellipse2D.Float(x, y, size, size));
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    public static JLabel centered(String text) {
        JLabel l = label(text, 12, 0, Theme.DIM);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }
}
