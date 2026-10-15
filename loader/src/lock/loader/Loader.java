package lock.loader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Frameless loading window. Visual shell only: the progress is an animation, nothing is loaded yet. */
public final class Loader {
    private Loader() {}

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("lock.");
            f.setUndecorated(true);
            f.setBackground(new Color(0, 0, 0, 0));
            LoaderPanel p = new LoaderPanel();
            f.setContentPane(p);
            f.pack();
            f.setLocationRelativeTo(null);

            MouseAdapter drag = new MouseAdapter() {
                Point start;
                @Override public void mousePressed(MouseEvent e) {
                    int h = p.hit(e.getX(), e.getY());
                    if (h == 1) { f.setState(Frame.ICONIFIED); return; }
                    if (h == 2) { System.exit(0); return; }
                    start = e.getPoint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (start == null) return;
                    Point l = f.getLocation();
                    f.setLocation(l.x + e.getX() - start.x, l.y + e.getY() - start.y);
                }
            };
            p.addMouseListener(drag);
            p.addMouseMotionListener(drag);

            final long t0 = System.nanoTime();
            new Timer(8, e -> {
                double t = (System.nanoTime() - t0) / 1e9;
                p.setTime(t);
                Toolkit.getDefaultToolkit().sync();
            }).start();
            f.setVisible(true);
        });
    }
}
