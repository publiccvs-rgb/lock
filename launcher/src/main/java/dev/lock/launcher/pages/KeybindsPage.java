package dev.lock.launcher.pages;

import dev.lock.core.config.ModuleState;
import dev.lock.core.module.Keys;
import dev.lock.core.module.ModuleDef;
import dev.lock.core.module.Setting;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;
import dev.lock.launcher.ui.SettingsEditor;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every Lock keybind in one list, with conflict warnings between Lock binds. */
public final class KeybindsPage extends JPanel {
    private final LauncherState state;
    private final JPanel list = new JPanel();

    public KeybindsPage(LauncherState state) {
        super(new BorderLayout(0, 14));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(Components.h1("Keybinds"), BorderLayout.NORTH);
        head.add(Components.sub("Click a key, then press the new key. Escape clears it. Conflicts with vanilla keys are shown in game."), BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel card = new Components.Card(new BorderLayout(), Theme.PANEL, 16);
        card.add(Components.scroll(list));
        add(card, BorderLayout.CENTER);
        state.onConfig(this::refresh);
        refresh();
    }

    private Object shown;

    private void refresh() {
        if (shown == state.config()) return;
        shown = state.config();
        rebuild();
    }

    private void rebuild() {
        list.removeAll();
        Map<Integer, List<String>> uses = new HashMap<>();
        List<Object[]> binds = new ArrayList<>();
        for (ModuleState ms : state.config().active().modules().values())
            for (Setting<?> s : ms.def.settings().values())
                if (s instanceof Setting.Key k) {
                    String label = k.id.equals(ModuleDef.KEYBIND) ? ms.def.name : ms.def.name + ": " + k.name;
                    int code = ms.get(k);
                    if (code != Keys.NONE) uses.computeIfAbsent(code, x -> new ArrayList<>()).add(label);
                    binds.add(new Object[]{ms, k, label});
                }
        for (Object[] b : binds) {
            ModuleState ms = (ModuleState) b[0];
            Setting.Key k = (Setting.Key) b[1];
            String label = (String) b[2];
            int code = ms.get(k);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            JLabel l = Components.label(label, 12.5f, code == Keys.NONE ? 0 : 1, code == Keys.NONE ? Theme.DIM : Theme.TEXT);
            List<String> u = code == Keys.NONE ? null : uses.get(code);
            if (u != null && u.size() > 1) {
                l.setIcon(Theme.icon("info", 14, Theme.WARN));
                l.setToolTipText("Also bound: " + String.join(", ", u));
            }
            row.add(l, BorderLayout.CENTER);
            row.add(SettingsEditor.keyButton(code, nc -> { state.edit(() -> ms.set(k.id, nc)); rebuild(); }), BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            list.add(row);
        }
        list.revalidate();
        list.repaint();
    }
}
