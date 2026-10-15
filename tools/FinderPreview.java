import lock.basefinder.*;
import lock.gui.FinderHud;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Renders the chunk hover card and target list from real engine verdicts over a grass backdrop. */
public final class FinderPreview {
    static final int S = 2, W = 960, H = 540;

    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Model model = new Model();
        Engine.Options opt = new Engine.Options();

        // 1. a base behind anti-xray, found through the server's light
        ChunkSnapshot s1 = BaseFinderTest.natural(777, 6);
        BaseFinderTest.plantUndergroundBase(s1, true); BaseFinderTest.plantTunnel(s1); BaseFinderTest.finish(s1); BaseFinderTest.antiXray(s1, 1, true);
        s1.entities.add(new ChunkSnapshot.Ent(ChunkSnapshot.ENT_PLAYER, 5, -29, 5));
        Verdict v1 = Engine.analyze(s1, model, opt);

        // 2. a plain surface base
        ChunkSnapshot s2 = BaseFinderTest.natural(778, 7);
        BaseFinderTest.plantSurfaceBase(s2); BaseFinderTest.finish(s2);
        Verdict v2 = Engine.analyze(s2, model, opt);

        // 3. a strip mine
        ChunkSnapshot s3 = BaseFinderTest.natural(779, 8);
        BaseFinderTest.plantStripMine(s3); BaseFinderTest.finish(s3);
        Verdict v3 = Engine.analyze(s3, model, opt);

        // 4. a village: should be absorbed
        ChunkSnapshot s4 = BaseFinderTest.natural(780, 9);
        BaseFinderTest.naturalVillage(s4, new java.util.Random(1)); BaseFinderTest.finish(s4);
        Verdict v4 = Engine.analyze(s4, model, opt);

        Map<Long, Verdict> map = new HashMap<>();
        for (Verdict v : List.of(v1, v2, v3, v4)) map.put(Sites.key(v.cx, v.cz), v);
        Sites.link(map);
        List<Verdict> list = new ArrayList<>(map.values());
        list.sort((x, y) -> Double.compare(y.p, x.p));
        for (Verdict v : list) System.out.printf("%-12s p=%.2f points=%d %s%n", v.type.label, v.p, v.points, v.confidence);

        BufferedImage img = new BufferedImage(W * S, H * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // grass backdrop with a flagged chunk tile in perspective-ish
        g.setPaint(new GradientPaint(0, 0, new Color(0x6FB43C), 0, H * S, new Color(0x3F7A22)));
        g.fillRect(0, 0, W * S, H * S);
        java.util.Random r = new java.util.Random(4);
        for (int i = 0; i < 4000; i++) { g.setColor(new Color(0, 0, 0, r.nextInt(28))); g.fillRect(r.nextInt(W * S), r.nextInt(H * S), 22, 22); }
        g.setColor(new Color(0x4DFF6B)); g.setStroke(new BasicStroke(2));
        int[] tile = {150, 330, 330, 330, 380, 440, 120, 440};
        g.setColor(new Color(0x4DFF6B33, true)); g.fillPolygon(new int[] {300, 640, 700, 180}, new int[] {520, 520, 700, 700}, 4);
        g.setColor(new Color(0x4DFF6B)); g.drawPolygon(new int[] {300, 640, 700, 180}, new int[] {520, 520, 700, 700}, 4);
        g.fillOval(430, 590, 18, 18);
        g.setColor(new Color(0xFFFFFF)); g.drawOval(425, 585, 28, 28);

        GuiPreview.G2 gfx = new GuiPreview.G2(g);
        int accent = 0xFF22D3EE, low = 0xFF3B30, high = 0x4DFF6B;
        double px = 6 * 16 + 8 - 60, pz = -75;
        FinderHud.targets(gfx, list, 5, W - FinderHud.LIST_W - 10, 10, accent, low, high, px, pz, 412, 6, v1);
        float cx = 438 / 2f + 14, cy = 598 / 2f - FinderHud.cardHeight(v1) / 2f;
        FinderHud.card(gfx, v1, 449 / 2f + 14, 300 / 2f + 20, accent, low, high, px, pz, 1f);
        g.dispose();
        File out = new File(a[0]); out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);

        // second card: the village-absorbed one and the strip mine side by side
        BufferedImage img2 = new BufferedImage(W * S, 300 * S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img2.createGraphics();
        g2.setPaint(new GradientPaint(0, 0, new Color(0x2A3B52), 0, 300 * S, new Color(0x1B2738)));
        g2.fillRect(0, 0, W * S, 300 * S);
        GuiPreview.G2 gf2 = new GuiPreview.G2(g2);
        FinderHud.card(gf2, v3, 20, 20, accent, low, high, px, pz, 1f);
        FinderHud.card(gf2, v2, 20 + 226, 20, accent, low, high, px, pz, 1f);
        FinderHud.card(gf2, v4, 20 + 452, 20, accent, low, high, px, pz, 1f);
        g2.dispose();
        ImageIO.write(img2, "png", new File(out.getParentFile(), "cards.png"));
    }
}
