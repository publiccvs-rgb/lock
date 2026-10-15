package dev.lock.launcher;

import dev.lock.core.ui.IconSet;
import dev.lock.launcher.pages.DiagnosticsPage;
import dev.lock.launcher.pages.HomePage;
import dev.lock.launcher.pages.KeybindsPage;
import dev.lock.launcher.pages.ModulesPage;
import dev.lock.launcher.pages.ProfilesPage;
import dev.lock.launcher.pages.SettingsPage;
import dev.lock.launcher.pages.WaypointsPage;
import dev.lock.launcher.ui.Components;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main window: Lock sidebar (wordmark, navigation, live game status) and pages. */
public final class LauncherFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, Components.NavItem> nav = new LinkedHashMap<>();
    private final LauncherState state;

    public LauncherFrame(LauncherState state) {
        super("Lock");
        this.state = state;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 620));
        setSize(1120, 700);
        setLocationRelativeTo(null);
        setIconImage(ProfileIcon.image(64));
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.BG);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(sidebar(), BorderLayout.WEST);
        pages.setOpaque(false);
        page("home", new HomePage(state));
        page("modules", new ModulesPage(state));
        page("profiles", new ProfilesPage(state));
        page("waypoints", new WaypointsPage(state));
        page("keybinds", new KeybindsPage(state));
        page("diagnostics", new DiagnosticsPage(state));
        page("settings", new SettingsPage(state));
        root.add(pages, BorderLayout.CENTER);
        setContentPane(root);
        show("home");
    }

    private void page(String id, JComponent c) { pages.add(c, id); }

    private void show(String id) {
        cards.show(pages, id);
        nav.forEach((k, v) -> v.setSelected(k.equals(id)));
    }

    private JComponent sidebar() {
        JPanel side = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.PANEL);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.LINE);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(236, 100));
        side.setBorder(BorderFactory.createEmptyBorder(18, 6, 14, 6));
        JComponent word = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.aa(g2);
                g2.setFont(Theme.font(26, 2));
                g2.setColor(Theme.TEXT);
                g2.drawString("lock", 18, 30);
                int w = g2.getFontMetrics().stringWidth("lock");
                g2.setColor(Theme.ACCENT);
                g2.drawString(".", 18 + w, 30);
                g2.dispose();
            }
        };
        word.setPreferredSize(new Dimension(220, 44));
        word.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        side.add(word);
        side.add(Box.createVerticalStrut(10));
        addNav(side, "home", "lock", "Home");
        addNav(side, "modules", "hud", "Modules");
        addNav(side, "profiles", "layers", "Profiles");
        addNav(side, "waypoints", "pin", "Waypoints");
        addNav(side, "keybinds", "keyboard", "Keybinds");
        JComponent misc = Components.section("System");
        misc.setBorder(BorderFactory.createEmptyBorder(12, 20, 4, 0));
        side.add(misc);
        addNav(side, "diagnostics", "pulse", "Diagnostics");
        addNav(side, "settings", "gear", "Settings");
        side.add(Box.createVerticalGlue());
        side.add(statusChip());
        state.onConfig(() -> { nav.get("profiles").setPill(state.config().activeName()); repaint(); });
        nav.get("profiles").setPill(state.config().activeName());
        return side;
    }

    private void addNav(JPanel side, String id, String icon, String label) {
        Components.NavItem item = new Components.NavItem(icon, label, () -> show(id));
        item.setAlignmentX(0);
        nav.put(id, item);
        side.add(item);
    }

    /** Live game connection indicator at the bottom of the sidebar. */
    private JComponent statusChip() {
        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.aa(g2);
                var st = state.status();
                boolean ready = state.connected();
                String title = ready ? "Game connected" : st != null ? "Game starting" : "Game not running";
                String detail = ready ? st.get("fps") + " FPS · " + st.get("profile") : st != null ? String.valueOf(st.get("state")).toLowerCase().replace('_', ' ') : "Start the Lock profile";
                Color dot = ready ? Theme.OK : st != null ? Theme.WARN : Theme.FAINT;
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(10, 0, getWidth() - 20, getHeight(), 14, 14));
                g2.setColor(new Color(dot.getRed(), dot.getGreen(), dot.getBlue(), 60));
                g2.fill(new Ellipse2D.Float(22, getHeight() / 2f - 8, 16, 16));
                g2.setColor(dot);
                g2.fill(new Ellipse2D.Float(26, getHeight() / 2f - 4, 8, 8));
                g2.setFont(Theme.font(12, 1));
                g2.setColor(Theme.TEXT);
                g2.drawString(title, 48, getHeight() / 2 - 2);
                g2.setFont(Theme.font(10.5f, 0));
                g2.setColor(Theme.DIM);
                g2.drawString(detail, 48, getHeight() / 2 + 13);
                g2.setColor(Theme.FAINT);
                IconSet.paint(g2, "server", getWidth() - 40, getHeight() / 2.0 - 7, 14);
                g2.dispose();
            }
        };
        c.setPreferredSize(new Dimension(220, 50));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        c.setAlignmentX(0);
        state.onStatus(c::repaint);
        return c;
    }
}
