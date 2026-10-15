package dev.lock.launcher;

import dev.lock.core.ui.IconSet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/** The 128x128 icon shown for the Lock profile in the official launcher. */
final class ProfileIcon {
    private ProfileIcon() {}

    static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x17181B));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.3, size * 0.3));
        g.setColor(new Color(0x26A884));
        IconSet.paint(g, "lock", size * 0.18, size * 0.16, size * 0.64);
        g.dispose();
        return img;
    }

    static String base64() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image(128), "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
