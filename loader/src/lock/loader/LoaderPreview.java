package lock.loader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Headless renders of the loader (dev tool). Args: outDir stillTime | outDir video */
public final class LoaderPreview {
    static BufferedImage frame(LoaderPanel p, double t) {
        int w = LoaderPanel.W + 80, h = LoaderPanel.H + 80;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x3B2A52), w, h, new Color(0xC2633A)));
        g.fillRect(0, 0, w, h);
        g.translate(40, 40);
        p.paintLoader(g, t);
        g.dispose();
        return img;
    }

    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(a[0]); dir.mkdirs();
        LoaderPanel p = new LoaderPanel();
        if (a[1].equals("video")) {
            for (int f = 0; f < 240; f++) ImageIO.write(frame(p, f / 30.0), "png", new File(dir, String.format("l%04d.png", f)));
        } else {
            ImageIO.write(frame(p, Double.parseDouble(a[1])), "png", new File(dir, "loader.png"));
        }
    }
}
