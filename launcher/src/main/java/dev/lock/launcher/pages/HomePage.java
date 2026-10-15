package dev.lock.launcher.pages;

import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.OfficialMappings;
import dev.lock.launcher.Installer;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.ProcessScanner;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;

/** Install status, compatibility checks and running games. */
public final class HomePage extends JPanel {
    private final LauncherState state;
    private final JPanel checks = new JPanel();
    private final JPanel games = new JPanel();
    private final JPanel actions = new JPanel();
    private final JLabel headline = Components.label("", 13, 0, Theme.DIM);
    private boolean busy;

    public HomePage(LauncherState state) {
        super(new BorderLayout(0, 16));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel hero = new Components.Card(new BorderLayout(0, 8), Theme.PANEL, 16);
        hero.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        JLabel title = Components.label("Lock for Minecraft " + OfficialMappings.VERSION, 22, 2, Theme.TEXT);
        hero.add(title, BorderLayout.NORTH);
        hero.add(headline, BorderLayout.CENTER);
        actions.setOpaque(false);
        actions.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        hero.add(actions, BorderLayout.SOUTH);
        add(hero, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 2, 14, 0));
        grid.setOpaque(false);
        JPanel checkCard = new Components.Card(new BorderLayout(0, 6), Theme.PANEL, 16);
        checkCard.add(Components.section("Installation & compatibility"), BorderLayout.NORTH);
        checks.setOpaque(false);
        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        checkCard.add(Components.scroll(checks), BorderLayout.CENTER);
        JPanel gameCard = new Components.Card(new BorderLayout(0, 6), Theme.PANEL, 16);
        JPanel gh = new JPanel(new BorderLayout());
        gh.setOpaque(false);
        gh.add(Components.section("Running Minecraft"), BorderLayout.WEST);
        gameCard.add(gh, BorderLayout.NORTH);
        games.setOpaque(false);
        games.setLayout(new BoxLayout(games, BoxLayout.Y_AXIS));
        gameCard.add(Components.scroll(games), BorderLayout.CENTER);
        grid.add(checkCard);
        grid.add(gameCard);
        add(grid, BorderLayout.CENTER);
        state.onStatus(this::refreshGames);
        state.onConfig(this::refreshChecks);
        refreshChecks();
        refreshGames();
    }

    private Installer installer() { return new Installer(state.minecraftDir()); }

    private void refreshChecks() {
        Installer in = installer();
        checks.removeAll();
        for (Installer.Check c : in.checks())
            checks.add(Components.statusLine(c.label(), c.detail(), c.ok() ? Theme.OK : Theme.WARN));
        checks.revalidate();
        checks.repaint();
        actions.removeAll();
        boolean installed = in.profileInstalled();
        boolean current = in.agentCurrent();
        headline.setText(installed && current ? "Installed. Start the \"Lock " + OfficialMappings.VERSION + "\" profile in the Minecraft Launcher."
                : installed ? "Installed, but the Lock runtime in .minecraft is out of date." : "Adds a Lock profile to the official Minecraft Launcher. Sign-in and downloads stay with Mojang.");
        if (busy) actions.add(Components.label("Working…", 12, 1, Theme.ACCENT));
        else {
            actions.add(Components.primary(installed ? (current ? "Reinstall" : "Update Lock") : "Install Lock profile", "download", this::install));
            if (installed) actions.add(Components.button("Uninstall", "trash", this::uninstall));
            actions.add(Components.button("Open Minecraft folder", "folder", () -> open(state.minecraftDir())));
        }
        actions.revalidate();
        actions.repaint();
    }

    private void install() { run(() -> installer().install(state.jvmArgs())); }

    private void uninstall() {
        if (JOptionPane.showConfirmDialog(this, "Remove the Lock profile from the Minecraft Launcher?", "Uninstall", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        run(() -> installer().uninstall());
    }

    private interface Task { String call() throws Exception; }

    private void run(Task t) {
        busy = true;
        refreshChecks();
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return t.call(); }
            @Override protected void done() {
                busy = false;
                try {
                    JOptionPane.showMessageDialog(HomePage.this, get(), "Lock", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(HomePage.this, c.getMessage(), "Lock", JOptionPane.ERROR_MESSAGE);
                }
                refreshChecks();
            }
        }.execute();
    }

    private void refreshGames() {
        games.removeAll();
        List<ProcessScanner.Game> list = state.games();
        if (list.isEmpty()) {
            games.add(Components.statusLine("No running game found", "Start Minecraft " + OfficialMappings.VERSION + " with the Lock profile; it appears here automatically.", Theme.FAINT));
        }
        Map<String, Object> st = state.status();
        games.add(javax.swing.Box.createVerticalStrut(2));
        for (ProcessScanner.Game g : list) {
            if (g.lock()) {
                String s = st == null ? "connecting…" : JsonUtil.str(st, "state", "?");
                String detail = "PID " + g.pid() + " · Minecraft " + g.version();
                if (st != null) {
                    detail += " · " + JsonUtil.str(st, "message", "");
                    if (st.get("fps") != null) detail += " " + st.get("fps") + " FPS · profile " + st.get("profile") + " · mode " + st.get("mode");
                }
                Color c = "READY".equals(s) ? Theme.OK : s.contains("FAIL") || s.contains("UNSUPPORTED") ? Theme.ERROR : Theme.WARN;
                games.add(Components.statusLine("Lock " + s.toLowerCase().replace('_', ' '), detail, c));
                if ("READY".equals(s)) {
                    JPanel r = Components.row(6,
                            Components.button("Open Click GUI in game", "layout", () -> state.command("openGui", null, null)),
                            Components.button("HUD editor", "hud", () -> state.command("openHudEditor", null, null)));
                    r.setAlignmentX(LEFT_ALIGNMENT);
                    r.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
                    games.add(r);
                }
            } else {
                games.add(Components.statusLine("Minecraft " + g.version() + " without Lock", "PID " + g.pid() + " · Lock loads at game start only: quit and start the Lock profile.", Theme.WARN));
            }
        }
        games.revalidate();
        games.repaint();
    }

    static void open(java.nio.file.Path p) {
        try {
            java.nio.file.Files.createDirectories(p);
            java.awt.Desktop.getDesktop().open(p.toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, p.toString(), "Folder", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
