package dev.lock.launcher.pages;

import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.config.Profile;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;

/** Profile manager: switch, create, duplicate, rename, delete, reset, export and import. */
public final class ProfilesPage extends JPanel {
    private final LauncherState state;
    private final JPanel list = new JPanel();
    private String selected;

    public ProfilesPage(LauncherState state) {
        super(new BorderLayout(0, 14));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(Components.h1("Profiles"), BorderLayout.NORTH);
        head.add(Components.sub("Each profile stores module states, settings, keybinds, HUD layout and visibility policy."), BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel card = new Components.Card(new BorderLayout(), Theme.PANEL, 16);
        card.add(Components.scroll(list));
        add(card, BorderLayout.CENTER);
        JPanel actions = Components.row(8,
                Components.primary("Switch to", "check", () -> act(() -> state.config().switchTo(selected) ? null : "Select a profile")),
                Components.button("New", "plus", () -> named("New profile", null, n -> state.config().create(n))),
                Components.button("Duplicate", "layers", () -> named("Duplicate as", selected + " copy", n -> state.config().duplicate(selected, n))),
                Components.button("Rename", "edit", () -> named("Rename to", selected, n -> state.config().rename(selected, n))),
                Components.button("Reset", "refresh", () -> act(() -> { state.config().profiles().get(selected).resetAll(); return null; })),
                Components.button("Delete", "trash", () -> act(() -> state.config().delete(selected))),
                Components.button("Export…", "upload", this::export),
                Components.button("Import…", "download", this::importFile));
        add(actions, BorderLayout.SOUTH);
        state.onConfig(this::refresh);
        refresh();
    }

    private interface Op { String run(); }

    private void act(Op op) {
        if (selected == null) return;
        String[] err = {null};
        state.edit(() -> err[0] = op.run());
        if (err[0] != null) JOptionPane.showMessageDialog(this, err[0], "Profiles", JOptionPane.WARNING_MESSAGE);
    }

    private void named(String prompt, String init, java.util.function.Function<String, String> op) {
        String n = (String) JOptionPane.showInputDialog(this, prompt, "Profiles", JOptionPane.PLAIN_MESSAGE, null, null, init);
        if (n == null) return;
        String[] err = {null};
        state.edit(() -> err[0] = op.apply(n));
        if (err[0] != null) JOptionPane.showMessageDialog(this, err[0], "Profiles", JOptionPane.WARNING_MESSAGE);
        else selected = n.trim();
        refresh();
    }

    private void export() {
        Profile p = state.config().profiles().get(selected);
        if (p == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File(p.name() + ".json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            ConfigStore.exportProfile(p, fc.getSelectedFile().toPath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Lock profile or config (*.json)", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path f = fc.getSelectedFile().toPath();
        String[] msg = {null};
        state.edit(() -> {
            try { msg[0] = ConfigStore.importFile(state.config(), f); } catch (Exception e) { msg[0] = "Import failed: " + e.getMessage(); }
        });
        JOptionPane.showMessageDialog(this, msg[0], "Import", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refresh() {
        LockConfig cfg = state.config();
        if (selected == null || !cfg.profiles().containsKey(selected)) selected = cfg.activeName();
        list.removeAll();
        for (Profile p : cfg.profiles().values()) list.add(row(p, p.name().equals(cfg.activeName())));
        list.add(javax.swing.Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    private JComponent row(Profile p, boolean active) {
        JComponent r = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.aa(g2);
                boolean sel = p.name().equals(selected);
                if (active) { g2.setColor(Theme.ACCENT); g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 12, 12)); }
                else if (sel) { g2.setColor(new Color(255, 255, 255, 16)); g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 12, 12)); }
                if (sel) { g2.setColor(active ? new Color(255, 255, 255, 140) : Theme.ACCENT); g2.draw(new RoundRectangle2D.Float(2.5f, 2.5f, getWidth() - 5, getHeight() - 5, 12, 12)); }
                Color fg = active ? Theme.onAccent() : Theme.TEXT;
                g2.setColor(fg);
                g2.setFont(Theme.font(13.5f, 1));
                g2.drawString(p.name(), 18, 25);
                g2.setFont(Theme.font(11, 0));
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 160));
                long on = p.modules().values().stream().filter(m -> m.def.toggleable() && m.enabled()).count();
                g2.drawString((p.builtIn() ? "Built-in" : "Custom") + " · " + on + " modules enabled · mode " + p.module("visibility").str("mode") + (active ? " · active" : ""), 18, 42);
                g2.dispose();
            }
        };
        r.setPreferredSize(new Dimension(400, 56));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        r.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selected = p.name();
                if (e.getClickCount() == 2) state.edit(() -> state.config().switchTo(p.name()));
                list.repaint();
            }
        });
        return r;
    }
}
