package lock.loader;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.InputStream;

/**
 * Loading page. Proportions are measured from the reference window (1885x1087): wordmark ~13% of the width at 39%
 * of the height, progress bar 30% wide and only ~0.8% tall at 56%, window controls at 92% / 96% of the width.
 */
public class LoaderPanel extends JPanel {
    // window size, plus a transparent margin that holds the drop shadow
    public static final int WIN_W = 960, WIN_H = 554, MARGIN = 26, ARC = 12;
    public static final int W = WIN_W + MARGIN * 2, H = WIN_H + MARGIN * 2;

    static final Color BASE = new Color(0x1B1B1B);
    static final Color TRACK = new Color(0x151515);
    static final Color FILL = new Color(0x0F8F73);
    static final Color CTRL = new Color(0x8E8E8E);
    static final Color CTRL_HI = new Color(0xF0F0F0);

    private final BufferedImage logo = loadLogo();
    private BufferedImage shading;     // cached, pre-blurred corner shading
    private double time;
    private int hover;                 // 0 none, 1 minimise, 2 close

    public LoaderPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(W, H));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = hit(e.getX(), e.getY());
                if (h != hover) { hover = h; repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { if (hover != 0) { hover = 0; repaint(); } }
        };
        addMouseMotionListener(m);
        addMouseListener(m);
    }

    /** Seconds since the window appeared; drives every animation. */
    public void setTime(double t) { time = t; repaint(); }

    // ---- animation curves ----
    static double easeOut(double t) { t = Math.max(0, Math.min(1, t)); return 1 - Math.pow(1 - t, 3); }
    static double easeInOut(double t) { t = Math.max(0, Math.min(1, t)); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }

    /** Progress 0..1: eased fill over ~5.2s with two short stalls, like real work happening. */
    public static double progress(double t) {
        double u = Math.max(0, Math.min(1, (t - 0.55) / 5.2));
        double base = easeInOut(u);
        double stall = 0.025 * Math.sin(u * Math.PI * 5) * (1 - u);
        return Math.max(0, Math.min(1, base + stall));
    }

    // ---- geometry (window-relative) ----
    static Rectangle minBox() { return new Rectangle(MARGIN + (int) (WIN_W * 0.924) - 14, MARGIN + (int) (WIN_H * 0.052) - 14, 28, 28); }
    static Rectangle closeBox() { return new Rectangle(MARGIN + (int) (WIN_W * 0.960) - 14, MARGIN + (int) (WIN_H * 0.052) - 14, 28, 28); }

    int hit(int x, int y) {
        if (minBox().contains(x, y)) return 1;
        if (closeBox().contains(x, y)) return 2;
        return 0;
    }

    private static BufferedImage loadLogo() {
        try (InputStream in = LoaderPanel.class.getResourceAsStream("/wordmark.png")) {
            return in == null ? null : crop(ImageIO.read(in));
        } catch (Exception e) { return null; }
    }

    private static BufferedImage crop(BufferedImage src) {
        int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                if ((src.getRGB(x, y) >>> 24) > 12) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        return maxX < 0 ? src : src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Soft darker wedges in two corners, blurred once and cached - painting is then a single drawImage. */
    private BufferedImage shading() {
        if (shading != null) return shading;
        BufferedImage img = new BufferedImage(WIN_W, WIN_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x161616));
        Path2D tl = new Path2D.Float();
        tl.moveTo(-40, -40);
        tl.lineTo(WIN_W * 0.185, -40);
        tl.curveTo(WIN_W * 0.17, WIN_H * 0.10, WIN_W * 0.09, WIN_H * 0.27, -40, WIN_H * 0.40);
        tl.closePath();
        g.fill(tl);
        g.setColor(new Color(0x171717));
        Path2D br = new Path2D.Float();
        br.moveTo(WIN_W + 40, WIN_H * 0.62);
        br.curveTo(WIN_W * 0.985, WIN_H * 0.78, WIN_W * 0.95, WIN_H * 0.90, WIN_W * 0.915, WIN_H + 40);
        br.lineTo(WIN_W + 40, WIN_H + 40);
        br.closePath();
        g.fill(br);
        g.dispose();
        shading = blur(blur(blur(img, 40), 40), 40);
        return shading;
    }

    private static BufferedImage blur(BufferedImage src, int r) {
        int n = r * 2 + 1;
        float[] k = new float[n];
        java.util.Arrays.fill(k, 1f / n);
        BufferedImage mid = new ConvolveOp(new Kernel(n, 1, k), ConvolveOp.EDGE_NO_OP, null).filter(src, null);
        return new ConvolveOp(new Kernel(1, n, k), ConvolveOp.EDGE_NO_OP, null).filter(mid, null);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        paintLoader((Graphics2D) g0, time);
    }

    public void paintLoader(Graphics2D g0, double t) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double fade = easeOut(t / 0.35);                 // whole window fades in
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fade));

        // drop shadow: stacked translucent rounded rects
        for (int i = 9; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 5 + (10 - i)));
            g.fill(new RoundRectangle2D.Float(MARGIN - i * 1.4f, MARGIN - i * 1.4f + 7, WIN_W + i * 2.8f, WIN_H + i * 2.8f, ARC + i * 2, ARC + i * 2));
        }

        g.translate(MARGIN, MARGIN);
        Shape window = new RoundRectangle2D.Float(0, 0, WIN_W, WIN_H, ARC, ARC);
        g.setColor(BASE);
        g.fill(window);
        Shape old = g.getClip();
        g.clip(window);
        g.drawImage(shading(), 0, 0, null);
        g.setClip(old);

        g.setColor(new Color(255, 255, 255, 9));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, WIN_W - 1, WIN_H - 1, ARC, ARC));
        g.translate(-MARGIN, -MARGIN);

        drawMinimise(g, minBox(), hover == 1 ? CTRL_HI : CTRL);
        drawClose(g, closeBox(), hover == 2 ? CTRL_HI : CTRL);

        // wordmark: rises into place
        double lp = easeOut((t - 0.1) / 0.6);
        if (logo != null) {
            int lw = 112;
            int lh = Math.round(lw * logo.getHeight() / (float) logo.getWidth());
            int lx = MARGIN + (WIN_W - lw) / 2;
            int ly = MARGIN + Math.round(WIN_H * 0.393f) - lh / 2 + (int) Math.round((1 - lp) * 8);
            Composite c = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (fade * lp)));
            g.drawImage(logo, lx, ly, lw, lh, null);
            g.setComposite(c);
        }

        // progress bar
        double bp = easeOut((t - 0.25) / 0.5);
        int bw = Math.round(WIN_W * 0.301f), bh = 7;
        int bx = MARGIN + (WIN_W - bw) / 2, by = MARGIN + Math.round(WIN_H * 0.557f) - bh / 2;
        Composite c = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (fade * bp)));
        g.setColor(TRACK);
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        double p = progress(t);
        float fw = (float) Math.max(0, bw * p);
        if (fw >= 1f) {
            Shape fillShape = new RoundRectangle2D.Float(bx, by, Math.max(fw, bh), bh, bh, bh);
            g.setColor(FILL);
            g.fill(fillShape);
            // slow shimmer sweeping across the filled part
            Shape o2 = g.getClip();
            g.clip(fillShape);
            double sp = ((t * 0.7) % 1.6) / 1.6;
            float sx = (float) (bx - 50 + sp * (Math.max(fw, bh) + 100));
            g.setPaint(new LinearGradientPaint(sx, 0, sx + 50, 0, new float[] {0f, 0.5f, 1f},
                new Color[] {new Color(255, 255, 255, 0), new Color(255, 255, 255, 30), new Color(255, 255, 255, 0)}));
            g.fillRect(bx, by, bw, bh);
            g.setClip(o2);
        }
        g.setComposite(c);
        g.dispose();
    }

    private static void drawMinimise(Graphics2D g, Rectangle b, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = b.x + b.width / 2, cy = b.y + b.height / 2;
        g.drawLine(cx - 4, cy, cx + 4, cy);
    }

    private static void drawClose(Graphics2D g, Rectangle b, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = b.x + b.width / 2, cy = b.y + b.height / 2;
        g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
        g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
    }
}
