import lock.gui.Gfx;
import lock.gui.GuiView;
import lock.module.Module;
import lock.module.ModuleManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;

/** Renders the menu with Java2D at 1920x1080 / GUI scale 2 (dev tool; in-game text uses Minecraft's font). */
public final class GuiPreview {
    static final int S = 2, W = 960, H = 540;

    static final class G2 implements Gfx {
        final Graphics2D g;
        float alpha = 1f;
        final ArrayDeque<Shape> clips = new ArrayDeque<>();
        G2(Graphics2D g) { this.g = g; }
        Color c(int argb) {
            int a = Math.round(((argb >>> 24) & 255) * alpha);
            return new Color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, a);
        }
        @Override public void alpha(float a) { alpha = a; }
        void fill(int x0, int y0, int x1, int y1, int argb) {
            g.setColor(c(argb)); g.fillRect(x0 * S, y0 * S, (x1 - x0) * S, (y1 - y0) * S);
        }
        @Override public void rect(float x, float y, float w, float h, int argb) {
            int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
            if (x1 > x0 && y1 > y0 && (argb >>> 24) != 0) fill(x0, y0, x1, y1, argb);
        }
        @Override public void round(float x, float y, float w, float h, float r, int argb) { roundEx(x, y, w, h, r, r, r, r, argb); }
        static int radius(float r, int W, int H) { return Math.max(0, Math.min(Math.min((int) Math.floor(r + 0.35f), 8), Math.min(W, H) / 2)); }
        static int[] inset(int r) {
            int[] t = new int[r];
            for (int i = 0; i < r; i++) { double dy = r - i - 0.5; t[i] = r - (int) Math.round(Math.sqrt(r * r - dy * dy)); }
            return t;
        }
        // identical to McGfx.roundEx so the preview shows the exact stepped corners the game will draw
        @Override public void roundEx(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb) {
            if ((argb >>> 24) == 0) return;
            int x0 = Math.round(x), y0 = Math.round(y);
            int W = Math.round(x + w) - x0, H = Math.round(y + h) - y0;
            if (W <= 0 || H <= 0) return;
            int rtl = radius(tl, W, H), rtr = radius(tr, W, H), rbr = radius(br, W, H), rbl = radius(bl, W, H);
            int top = Math.max(rtl, rtr), bot = Math.max(rbl, rbr);
            if (top + bot > H) { top = Math.min(top, H / 2); bot = Math.min(bot, H - top); }
            if (H - top - bot > 0) fill(x0, y0 + top, x0 + W, y0 + H - bot, argb);
            for (int i = 0; i < top; i++) {
                int l = i < rtl ? inset(rtl)[i] : 0, r = i < rtr ? inset(rtr)[i] : 0;
                fill(x0 + l, y0 + i, x0 + W - r, y0 + i + 1, argb);
            }
            for (int i = 0; i < bot; i++) {
                int l = i < rbl ? inset(rbl)[i] : 0, r = i < rbr ? inset(rbr)[i] : 0;
                fill(x0 + l, y0 + H - 1 - i, x0 + W - r, y0 + H - i, argb);
            }
        }
        // Liberation Sans is metric-compatible with Arial, which is what the game loads from the system
        static final Font BASE = new Font("Liberation Sans", Font.PLAIN, 12), BOLD = new Font("Liberation Sans", Font.BOLD, 12);
        Font face(float scale, boolean bold) { return (bold ? BOLD : BASE).deriveFont(8.6f * S * scale); }
        @Override public void text(String s, float x, float y, int argb, float scale, boolean bold) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(face(scale, bold)); g.setColor(c(argb));
            if (scale == 1f) { x = Math.round(x); y = Math.round(y); }
            g.drawString(s, x * S, (y + 7f * scale) * S);   // the game puts the baseline 7 px below the text's top
        }
        @Override public float width(String s, float scale, boolean bold) {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            return (float) g.getFontMetrics(face(scale, bold)).getStringBounds(s, g).getWidth() / S;
        }
        static int lerp(int a, int b, float t) {
            int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
            int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
            return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
        }
        @Override public void gradient(float x, float y, float w, float h, float tl, float tr, float br, float bl, int top, int bottom) {
            int x0 = Math.round(x), y0 = Math.round(y);
            int W = Math.round(x + w) - x0, H = Math.round(y + h) - y0;
            if (W <= 0 || H <= 0) return;
            int rtl = radius(tl, W, H), rtr = radius(tr, W, H), rbr = radius(br, W, H), rbl = radius(bl, W, H);
            for (int i = 0; i < H; i++) {
                int l = 0, r = 0;
                if (i < Math.max(rtl, rtr)) { l = i < rtl ? inset(rtl)[i] : 0; r = i < rtr ? inset(rtr)[i] : 0; }
                int fromBottom = H - 1 - i;
                if (fromBottom < Math.max(rbl, rbr)) { l = Math.max(l, fromBottom < rbl ? inset(rbl)[fromBottom] : 0); r = Math.max(r, fromBottom < rbr ? inset(rbr)[fromBottom] : 0); }
                fill(x0 + l, y0 + i, x0 + W - r, y0 + i + 1, lerp(top, bottom, (i + 0.5f) / H));
            }
        }
        @Override public void clip(float x, float y, float w, float h) {
            Shape cur = g.getClip();
            clips.push(cur == null ? new Rectangle(0, 0, 1 << 14, 1 << 14) : cur);
            Rectangle2D r = new Rectangle2D.Float(Math.round(x) * S, Math.round(y) * S, (Math.round(x + w) - Math.round(x)) * S, (Math.round(y + h) - Math.round(y)) * S);
            if (cur == null) g.setClip(r); else g.clip(r);
        }
        @Override public void unclip() { Shape s = clips.pop(); g.setClip(s.getBounds().width >= 1 << 14 ? null : s); }
    }

    static BufferedImage scene() {
        BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x8DB8F2), 0, 700, new Color(0xC9DDF6)));
        g.fillRect(0, 0, W * S, H * S);
        g.setColor(new Color(0xB7794A)); g.fillRect(0, 760, W * S, 320);
        g.setColor(new Color(0x7A5230)); for (int i = 0; i < 40; i++) g.fillRect(i * 52, 760, 3, 320);
        g.dispose();
        return img;
    }

    static void frame(GuiView v, BufferedImage bg, float dt, int mx, int my, File out) throws Exception {
        BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(bg, 0, 0, null);
                g.setColor(new Color(0, 0, 0, 0x2E)); g.fillRect(0, 0, W * S, H * S);
        v.render(new G2(g), mx, my, dt, W, H);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String mode = a[0];
        File dir = new File(a[1]); dir.mkdirs();
        ModuleManager m = new ModuleManager();
        m.charms.setEnabled(false); m.outlines.setEnabled(false); m.items.setEnabled(false);
        m.boxes.setEnabled(true); m.tracers.setEnabled(true); m.storage.setEnabled(true);
        m.nameTags.setEnabled(true); m.fullbright.setEnabled(true); m.baseFinder.setEnabled(true);
        BufferedImage bg = scene();
        GuiView v = new GuiView(m);

        if (mode.equals("still") || mode.equals("open")) {
            if (mode.equals("open")) { v.open(m.baseFinder); }
            int mx = mode.equals("open") ? 330 : 150, my = mode.equals("open") ? 160 : 95;
            // advance simulated time so animations and the tooltip settle
            BufferedImage scratch = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
            Gfx nul = new G2(scratch.createGraphics());
            for (int i = 0; i < 120; i++) v.render(nul, mx, my, 1 / 60f, W, H);
            frame(v, bg, 1 / 60f, mx, my, new File(dir, mode + ".png"));
        } else { // "video": scripted interaction, 30 fps
            int n = 0;
            float dt = 1 / 30f;
            int mx = 300, my = 200;
            for (int f = 0; f < 150; f++) {
                float t = f * dt;
                if (t > 1.3f && t < 1.35f) { mx = 175; my = 96; }
                if (Math.abs(t - 1.5f) < dt / 2) { v.click(175, 96, 0); }          // toggle Chams
                if (t > 2.2f) { mx = 232; my = 54; }
                if (Math.abs(t - 2.4f) < dt / 2) { v.click(232, 54, 0); }          // open Boxes settings
                if (t > 3.6f) { mx = 175; my = 140; }
                frame(v, bg, dt, mx, my, new File(dir, String.format("f%04d.png", n++)));
            }
        }
    }
}
