package dev.lock.launcher.pages;

import dev.lock.core.config.ConfigStore;
import dev.lock.core.config.LockConfig;
import dev.lock.core.util.LockPaths;
import dev.lock.launcher.Installer;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/** Launcher and data settings: Minecraft folder, JVM arguments, theme, import/export/reset. */
public final class SettingsPage extends JPanel {
    private final LauncherState state;

    public SettingsPage(LauncherState state) {
        super(new BorderLayout(0, 14));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        add(Components.h1("Settings"), BorderLayout.NORTH);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel mc = card();
        mc.add(left(Components.section("Minecraft")));
        JTextField dir = new JTextField(state.minecraftDir().toString());
        dir.setPreferredSize(new Dimension(420, 30));
        mc.add(left(field("Minecraft folder", dir, Components.button("Browse…", "folder", () -> {
            JFileChooser fc = new JFileChooser(state.minecraftDir().toFile());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dir.setText(fc.getSelectedFile().toString());
                state.edit(() -> state.config().launcher.put("minecraftDir", dir.getText()));
            }
        }))));
        dir.addActionListener(e -> state.edit(() -> state.config().launcher.put("minecraftDir", dir.getText().trim())));
        JTextField jvm = new JTextField(state.jvmArgs());
        jvm.setPreferredSize(new Dimension(420, 30));
        jvm.addActionListener(e -> state.edit(() -> state.config().launcher.put("jvmArgs", jvm.getText().trim())));
        mc.add(left(field("Profile JVM arguments", jvm, Components.button("Default", "refresh", () -> {
            jvm.setText(Installer.DEFAULT_JVM);
            state.edit(() -> state.config().launcher.put("jvmArgs", Installer.DEFAULT_JVM));
        }))));
        mc.add(left(Components.sub("Applied the next time you install or update the Lock profile. Lock adds its -javaagent argument itself.")));
        body.add(mc);
        body.add(javax.swing.Box.createVerticalStrut(12));

        JPanel look = card();
        look.add(left(Components.section("Appearance")));
        JComboBox<String> preset = new JComboBox<>(new String[]{"Teal", "Blue", "Mint", "Rose", "Amber", "Violet", "Mono", "Custom"});
        preset.setSelectedItem(state.config().active().module("theme").str("preset"));
        preset.addActionListener(e -> {
            state.edit(() -> state.config().active().module("theme").set("preset", preset.getSelectedItem()));
            JOptionPane.showMessageDialog(this, "Theme applied in game immediately; restart the launcher to recolour it.", "Theme", JOptionPane.INFORMATION_MESSAGE);
        });
        look.add(left(field("Theme accent (active profile)", preset, null)));
        body.add(look);
        body.add(javax.swing.Box.createVerticalStrut(12));

        JPanel data = card();
        data.add(left(Components.section("Configuration")));
        data.add(left(Components.sub("Stored in " + LockPaths.config() + " with automatic backups in " + LockPaths.backups())));
        data.add(left(Components.row(8,
                Components.button("Export everything…", "upload", this::exportAll),
                Components.button("Import…", "download", this::importAll),
                Components.button("Open Lock folder", "folder", () -> HomePage.open(LockPaths.home())),
                Components.button("Reset all settings", "trash", this::resetAll))));
        if (!state.loadProblems().isEmpty())
            data.add(left(Components.statusLine("Configuration was repaired on load", String.join("; ", state.loadProblems()), Theme.WARN)));
        body.add(data);
        add(Components.scroll(body), BorderLayout.CENTER);
    }

    private static JPanel card() {
        JPanel c = new Components.Card(null, Theme.PANEL, 16);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static <T extends java.awt.Component> T left(T c) {
        if (c instanceof javax.swing.JComponent j) j.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JPanel field(String label, java.awt.Component input, java.awt.Component extra) {
        JPanel p = new JPanel(new BorderLayout(10, 4));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
        p.add(Components.label(label, 12, 1, Theme.TEXT), BorderLayout.NORTH);
        p.add(input, BorderLayout.CENTER);
        if (extra != null) p.add(extra, BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        return p;
    }

    private void exportAll() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("lock-config.json"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            ConfigStore.exportAll(state.config(), fc.getSelectedFile().toPath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importAll() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String[] msg = {null};
        state.edit(() -> {
            try { msg[0] = ConfigStore.importFile(state.config(), fc.getSelectedFile().toPath()); } catch (Exception e) { msg[0] = "Import failed: " + e.getMessage(); }
        });
        JOptionPane.showMessageDialog(this, msg[0], "Import", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resetAll() {
        if (JOptionPane.showConfirmDialog(this, "Reset every profile, waypoint and setting to defaults? A backup of the current file is kept.",
                "Reset all", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        LockConfig fresh = LockConfig.defaults();
        fresh.launcher.putAll(state.config().launcher);
        state.replaceConfig(fresh);
    }
}
