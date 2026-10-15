package dev.lock.core.ui;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lock's line icons as Java2D drawing code on a 24-unit grid (2-unit round strokes). Shared by the
 * launcher (drawn directly) and the in-game client (rasterised into atlases).
 */
public final class IconSet {
    private static final Map<String, Consumer<Pen>> DEFS = new LinkedHashMap<>();

    /** Drawing helper in 24-unit icon space. */
    public static final class Pen {
        final Graphics2D g;
        public Pen(Graphics2D g) { this.g = g; }
        Pen line(double x1, double y1, double x2, double y2) { g.draw(new Line2D.Double(x1, y1, x2, y2)); return this; }
        Pen poly(double... p) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
            g.draw(path);
            return this;
        }
        Pen closed(double... p) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
            path.closePath();
            g.draw(path);
            return this;
        }
        Pen circle(double cx, double cy, double r) { g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)); return this; }
        Pen dot(double cx, double cy, double r) { g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)); return this; }
        Pen rect(double x, double y, double w, double h, double r) { g.draw(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)); return this; }
        Pen fillRect(double x, double y, double w, double h, double r) { g.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2)); return this; }
        Pen arc(double cx, double cy, double r, double start, double extent) { g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, extent, Arc2D.OPEN)); return this; }
        Pen fillPoly(double... p) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(p[0], p[1]);
            for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
            path.closePath();
            g.fill(path);
            return this;
        }
    }

    static {
        def("lock", p -> p.rect(5, 11, 14, 10, 2).arc(12, 11, 4.5, 0, 180).line(7.5, 11, 7.5, 8).line(16.5, 11, 16.5, 8).dot(12, 16, 1.4));
        def("eye", p -> p.poly(2, 12, 6, 6.5, 12, 4.5, 18, 6.5, 22, 12, 18, 17.5, 12, 19.5, 6, 17.5, 2, 12).circle(12, 12, 3));
        def("hud", p -> p.rect(3, 3, 8, 9, 1.5).rect(13, 3, 8, 5, 1.5).rect(13, 10, 8, 11, 1.5).rect(3, 14, 8, 7, 1.5));
        def("move", p -> p.poly(13, 4, 9, 12, 14, 12, 10, 20).poly(5, 8, 3, 12, 5, 16).poly(19, 8, 21, 12, 19, 16));
        def("user", p -> p.circle(12, 8, 4).poly(4, 21, 4, 19, 6.5, 15.5, 12, 14, 17.5, 15.5, 20, 19, 20, 21));
        def("globe", p -> p.circle(12, 12, 9).line(3, 12, 21, 12).arc(12, 12, 4.5, 90, 180).arc(12, 12, 4.5, 270, 180).line(12, 3, 12, 21));
        def("wrench", p -> p.poly(14.5, 6.5, 17.5, 3.5, 20.5, 6.5, 20.5, 9, 17.5, 11.5, 15, 11.5, 5.5, 21, 3, 18.5, 12.5, 9, 12.5, 6.5, 14.5, 4.5));
        def("box", p -> p.closed(12, 2.5, 21, 7, 21, 17, 12, 21.5, 3, 17, 3, 7).poly(3, 7, 12, 11.5, 21, 7).line(12, 11.5, 12, 21.5));
        def("layout", p -> p.rect(3, 4, 18, 16, 2).line(3, 9, 21, 9).line(9, 9, 9, 20));
        def("gauge", p -> p.arc(12, 14, 9, -20, 220).line(12, 14, 16, 9).dot(12, 14, 1.6));
        def("video", p -> p.rect(2.5, 6, 13, 12, 2).closed(15.5, 10.5, 21.5, 7, 21.5, 17, 15.5, 13.5));
        def("star", p -> p.closed(12, 3, 14.6, 9, 21, 9.5, 16, 13.8, 17.6, 20.3, 12, 16.8, 6.4, 20.3, 8, 13.8, 3, 9.5, 9.4, 9));
        def("star_fill", p -> p.fillPoly(12, 3, 14.6, 9, 21, 9.5, 16, 13.8, 17.6, 20.3, 12, 16.8, 6.4, 20.3, 8, 13.8, 3, 9.5, 9.4, 9));
        def("search", p -> p.circle(10.5, 10.5, 6.5).line(15.5, 15.5, 20.5, 20.5));
        def("gear", p -> {
            p.circle(12, 12, 3);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                p.line(12 + Math.cos(a) * 6.2, 12 + Math.sin(a) * 6.2, 12 + Math.cos(a) * 9, 12 + Math.sin(a) * 9);
            }
            p.circle(12, 12, 6.2);
        });
        def("nav", p -> p.fillPoly(21, 12, 5, 4.5, 8.5, 12, 5, 19.5));
        def("chevron_right", p -> p.poly(9, 5, 16, 12, 9, 19));
        def("chevron_down", p -> p.poly(5, 9, 12, 16, 19, 9));
        def("chevron_up", p -> p.poly(5, 15, 12, 8, 19, 15));
        def("chevron_left", p -> p.poly(15, 5, 8, 12, 15, 19));
        def("dots", p -> p.dot(12, 5, 1.9).dot(12, 12, 1.9).dot(12, 19, 1.9));
        def("layers", p -> p.closed(12, 3, 21, 8, 12, 13, 3, 8).poly(3, 12.5, 12, 17.5, 21, 12.5).poly(3, 16.5, 12, 21.5, 21, 16.5));
        def("pin", p -> p.poly(12, 21.5, 6.5, 14.5, 5, 10, 6.5, 5.5, 12, 2.5, 17.5, 5.5, 19, 10, 17.5, 14.5, 12, 21.5).circle(12, 10, 2.8));
        def("keyboard", p -> {
            p.rect(2, 6, 20, 12, 2).line(7, 14.5, 17, 14.5);
            for (int i = 0; i < 4; i++) p.dot(6 + i * 4, 10, 0.9);
        });
        def("pulse", p -> p.poly(2, 12, 7, 12, 9.5, 5, 14.5, 19, 17, 12, 22, 12));
        def("grid", p -> p.rect(3, 3, 18, 18, 2).line(3, 9, 21, 9).line(3, 15, 21, 15).line(9, 3, 9, 21).line(15, 3, 15, 21));
        def("x", p -> p.line(6, 6, 18, 18).line(18, 6, 6, 18));
        def("check", p -> p.poly(4.5, 12.5, 9.5, 17.5, 19.5, 6.5));
        def("plus", p -> p.line(12, 5, 12, 19).line(5, 12, 19, 12));
        def("trash", p -> p.line(4, 6.5, 20, 6.5).poly(9, 6.5, 9, 3.5, 15, 3.5, 15, 6.5).poly(6, 6.5, 7, 20.5, 17, 20.5, 18, 6.5));
        def("refresh", p -> p.arc(12, 12, 8, 30, 300).poly(20, 4.5, 20, 9.5, 15, 9.5));
        def("mouse", p -> p.rect(6, 2.5, 12, 19, 6).line(12, 6.5, 12, 10));
        def("compass", p -> p.circle(12, 12, 9).closed(15.5, 8.5, 13.5, 13.5, 8.5, 15.5, 10.5, 10.5));
        def("signal", p -> p.line(5, 20, 5, 16).line(10, 20, 10, 12).line(15, 20, 15, 8).line(20, 20, 20, 4));
        def("server", p -> p.rect(3, 3, 18, 8, 2).rect(3, 13, 18, 8, 2).dot(7, 7, 1).dot(7, 17, 1));
        def("shield", p -> p.poly(12, 21.5, 5.5, 18, 4, 12.5, 4, 5.5, 12, 2.5, 20, 5.5, 20, 12.5, 18.5, 18, 12, 21.5));
        def("hammer", p -> p.line(14, 10, 4.5, 19.5).poly(10.5, 6.5, 14, 3, 21, 10, 17.5, 13.5).line(12, 8, 16, 12));
        def("flask", p -> p.poly(9, 3, 9, 9, 4, 19, 5, 21, 19, 21, 20, 19, 15, 9, 15, 3).line(7.5, 3, 16.5, 3).line(6.5, 15, 17.5, 15));
        def("heart", p -> p.poly(12, 20.5, 4, 13, 2.8, 9.5, 4, 6, 7.5, 4.5, 10.5, 5.5, 12, 7.5, 13.5, 5.5, 16.5, 4.5, 20, 6, 21.2, 9.5, 20, 13, 12, 20.5));
        def("clock", p -> p.circle(12, 12, 9).poly(12, 7, 12, 12, 15.5, 14));
        def("list", p -> p.line(9, 6, 21, 6).line(9, 12, 21, 12).line(9, 18, 21, 18).dot(4.5, 6, 1.2).dot(4.5, 12, 1.2).dot(4.5, 18, 1.2));
        def("text", p -> p.poly(4, 7, 4, 4, 20, 4, 20, 7).line(12, 4, 12, 20).line(9, 20, 15, 20));
        def("info", p -> p.circle(12, 12, 9).line(12, 11, 12, 16.5).dot(12, 7.8, 1.2));
        def("hand", p -> p.poly(7, 13, 7, 6, 9.5, 6, 9.5, 11).poly(9.5, 11, 9.5, 4, 12, 4, 12, 11).poly(12, 11, 12, 5, 14.5, 5, 14.5, 11).poly(14.5, 11, 14.5, 7, 17, 7, 17, 15, 14, 21, 9, 21, 5, 15.5, 7, 13));
        def("bell", p -> p.poly(6, 16, 6, 10, 8, 6, 12, 4.5, 16, 6, 18, 10, 18, 16, 20, 18, 4, 18, 6, 16).arc(12, 18, 2.2, 180, 180));
        def("zap", p -> p.closed(13, 2.5, 4.5, 13.5, 11.5, 13.5, 10.5, 21.5, 19.5, 10.5, 12.5, 10.5));
        def("sun", p -> {
            p.circle(12, 12, 4);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                p.line(12 + Math.cos(a) * 6.5, 12 + Math.sin(a) * 6.5, 12 + Math.cos(a) * 9, 12 + Math.sin(a) * 9);
            }
        });
        def("map", p -> p.closed(3, 6, 9, 3.5, 15, 6, 21, 3.5, 21, 18, 15, 20.5, 9, 18, 3, 20.5).line(9, 3.5, 9, 18).line(15, 6, 15, 20.5));
        def("mountain", p -> p.closed(2.5, 20, 9.5, 7, 13.5, 13.5, 16, 10.5, 21.5, 20));
        def("cpu", p -> {
            p.rect(6, 6, 12, 12, 2).rect(9.5, 9.5, 5, 5, 1);
            for (int i = 0; i < 3; i++) {
                double v = 9 + i * 3;
                p.line(v, 3, v, 6).line(v, 18, v, 21).line(3, v, 6, v).line(18, v, 21, v);
            }
        });
        def("chart", p -> p.line(4, 20, 20, 20).line(7, 20, 7, 13).line(11.5, 20, 11.5, 7).line(16, 20, 16, 10));
        def("bug", p -> p.rect(7, 7, 10, 13, 5).line(12, 11, 12, 20).line(4, 12, 7, 12).line(17, 12, 20, 12).line(4, 17, 7.5, 16).line(20, 17, 16.5, 16).poly(9, 7, 9, 5, 15, 5, 15, 7));
        def("crosshair", p -> p.circle(12, 12, 8).line(12, 2, 12, 7).line(12, 17, 12, 22).line(2, 12, 7, 12).line(17, 12, 22, 12));
        def("timer", p -> p.circle(12, 13.5, 7.5).line(12, 13.5, 12, 9.5).line(10, 3, 14, 3).line(12, 3, 12, 6));
        def("palette", p -> p.poly(12, 3, 17.5, 4.5, 21, 9, 21, 13, 18, 15, 15, 15, 14, 17, 15, 19.5, 13, 21, 7.5, 19.5, 3.5, 15, 3, 9.5, 6, 5, 12, 3).dot(8, 10, 1.3).dot(12, 7, 1.3).dot(16, 9.5, 1.3));
        def("volume", p -> p.closed(4, 9.5, 8, 9.5, 13, 5, 13, 19, 8, 14.5, 4, 14.5).arc(13, 12, 4, -45, 90).arc(13, 12, 7.5, -45, 90));
        def("save", p -> p.closed(4, 4, 17, 4, 20, 7, 20, 20, 4, 20).rect(8, 4, 8, 5, 0).rect(7.5, 13, 9, 7, 1));
        def("upload", p -> p.poly(4, 15, 4, 20, 20, 20, 20, 15).poly(7, 8, 12, 3, 17, 8).line(12, 3, 12, 15));
        def("download", p -> p.poly(4, 15, 4, 20, 20, 20, 20, 15).poly(7, 10, 12, 15, 17, 10).line(12, 3, 12, 15));
        def("moon", p -> p.poly(20, 14.5, 16, 16.5, 11.5, 16, 8, 13, 7, 8.5, 8.5, 4, 5, 6.5, 3.5, 11, 4.5, 15.5, 7.5, 19, 12, 20.5, 16.5, 19.5, 20, 14.5));
        def("camera", p -> p.rect(2.5, 7, 19, 13, 2).circle(12, 13.5, 3.5).poly(8, 7, 9.5, 4, 14.5, 4, 16, 7));
        def("folder", p -> p.closed(3, 6, 3, 19, 21, 19, 21, 8.5, 11.5, 8.5, 9.5, 5.5, 3, 5.5));
        def("edit", p -> p.closed(16, 3.5, 20.5, 8, 9, 19.5, 3.5, 20.5, 4.5, 15).line(13.5, 6, 18, 10.5));
    }

    private static void def(String name, Consumer<Pen> d) { DEFS.put(name, d); }

    private IconSet() {}

    public static Map<String, Consumer<Pen>> all() { return Collections.unmodifiableMap(DEFS); }

    public static boolean has(String name) { return DEFS.containsKey(name); }

    /** Draws icon {@code name} with the current colour of {@code g} into a size x size box at (x, y). */
    public static void paint(Graphics2D g, String name, double x, double y, double size) {
        Consumer<Pen> d = DEFS.get(name);
        if (d == null) return;
        Graphics2D cg = (Graphics2D) g.create();
        cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        cg.translate(x, y);
        cg.scale(size / 24.0, size / 24.0);
        cg.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.accept(new Pen(cg));
        cg.dispose();
    }
}
