package dev.lock.launcher.pages;

import dev.lock.core.json.JsonUtil;
import dev.lock.core.util.LockPaths;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Runtime integration status, hook status, module faults, compatibility checks and logs. */
public final class DiagnosticsPage extends JPanel {
    private final LauncherState state;
    private final JPanel runtime = new JPanel();
    private final JTextArea log = new JTextArea();

    public DiagnosticsPage(LauncherState state) {
        super(new BorderLayout(0, 14));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(Components.h1("Diagnostics"), BorderLayout.NORTH);
        head.add(Components.sub("Live status reported by the running game, plus Lock's log files."), BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(1, 2, 14, 0));
        grid.setOpaque(false);
        JPanel rc = new Components.Card(new BorderLayout(), Theme.PANEL, 16);
        runtime.setOpaque(false);
        runtime.setLayout(new BoxLayout(runtime, BoxLayout.Y_AXIS));
        rc.add(Components.scroll(runtime));
        JPanel lc = new Components.Card(new BorderLayout(0, 6), Theme.PANEL, 16);
        lc.add(Components.section("runtime.log (latest)"), BorderLayout.NORTH);
        log.setEditable(false);
        log.setOpaque(false);
        log.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11));
        log.setForeground(Theme.DIM);
        lc.add(Components.scroll(log), BorderLayout.CENTER);
        grid.add(rc);
        grid.add(lc);
        add(grid, BorderLayout.CENTER);
        add(Components.row(8,
                Components.button("Refresh log", "refresh", this::loadLog),
                Components.button("Write report in game", "save", () -> state.command("report", null, r ->
                        JOptionPane.showMessageDialog(this, Boolean.TRUE.equals(r.get("ok")) ? "Saved " + r.get("path") : r.get("error"), "Diagnostics", JOptionPane.INFORMATION_MESSAGE))),
                Components.button("Open logs folder", "folder", () -> HomePage.open(LockPaths.logs()))), BorderLayout.SOUTH);
        state.onStatus(this::refresh);
        refresh();
        loadLog();
    }

    private void loadLog() {
        Path p = LockPaths.logs().resolve("runtime.log");
        try {
            List<String> lines = Files.exists(p) ? Files.readAllLines(p) : List.of("No runtime log yet. It is created when the game starts with Lock.");
            log.setText(String.join("\n", lines.subList(Math.max(0, lines.size() - 300), lines.size())));
            log.setCaretPosition(log.getDocument().getLength());
        } catch (Exception e) {
            log.setText("Could not read " + p + ": " + e.getMessage());
        }
    }

    private void refresh() {
        runtime.removeAll();
        Map<String, Object> s = state.status();
        runtime.add(Components.section("Runtime integration"));
        if (s == null) {
            runtime.add(Components.statusLine("No running game with Lock", "Diagnostics appear when Minecraft is started with the Lock profile.", Theme.FAINT));
        } else {
            String st = JsonUtil.str(s, "state", "?");
            runtime.add(Components.statusLine("State: " + st, JsonUtil.str(s, "message", "") + " Lock " + s.get("lockVersion") + " · Minecraft " + s.get("minecraftVersion"),
                    "READY".equals(st) ? Theme.OK : Theme.WARN));
            Map<String, Object> startup = JsonUtil.obj(s.get("startup"));
            if (startup != null) runtime.add(Components.statusLine("Java " + startup.get("java"), startup.get("os") + " " + startup.get("arch")
                    + " · agent " + startup.get("agentInitMs") + " ms · client " + startup.get("clientInitMs") + " ms · mappings " + startup.get("mappingsSource"), Theme.OK));
            if (s.get("hudMicros") != null) runtime.add(Components.statusLine("Rendering", "HUD " + s.get("hudMicros") + " µs/frame · " + s.get("fps") + " FPS", Theme.OK));
            runtime.add(Components.section("Hooks"));
            List<Object> hooks = JsonUtil.arr(s.get("hooks"));
            if (hooks != null) for (Object o : hooks) {
                Map<String, Object> h = JsonUtil.obj(o);
                boolean ok = JsonUtil.bool(h, "applied", false);
                String d = JsonUtil.str(h, "detail", "");
                Color c = ok ? Theme.OK : d.startsWith("pending") ? Theme.FAINT : Theme.ERROR;
                runtime.add(Components.statusLine(JsonUtil.str(h, "name", "?"), d, c));
            }
            Map<String, Object> mods = JsonUtil.obj(s.get("modules"));
            if (mods != null) {
                runtime.add(Components.section("Module status"));
                int active = 0, faulted = 0;
                for (Object o : mods.values()) {
                    Map<String, Object> m = JsonUtil.obj(o);
                    if (JsonUtil.bool(m, "active", false)) active++;
                    if (JsonUtil.bool(m, "faulted", false)) faulted++;
                }
                runtime.add(Components.statusLine(active + " active of " + mods.size(), faulted == 0 ? "No module errors" : faulted + " module(s) stopped after errors", faulted == 0 ? Theme.OK : Theme.ERROR));
            }
            List<Object> compat = JsonUtil.arr(s.get("compatibility"));
            if (compat != null) {
                runtime.add(Components.section("Compatibility"));
                for (Object o : compat) runtime.add(Components.statusLine(String.valueOf(o), "", Theme.DIM));
            }
        }
        runtime.revalidate();
        runtime.repaint();
    }
}
