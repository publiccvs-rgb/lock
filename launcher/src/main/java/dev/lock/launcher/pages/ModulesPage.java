package dev.lock.launcher.pages;

import dev.lock.core.config.ModuleState;
import dev.lock.core.module.Catalog;
import dev.lock.core.module.Category;
import dev.lock.core.module.Keys;
import dev.lock.core.module.ModuleDef;
import dev.lock.core.module.Support;
import dev.lock.core.ui.IconSet;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;
import dev.lock.launcher.ui.SettingsEditor;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

/** Searchable module manager: category chips, Vape-style rows, and a generated settings editor. */
public final class ModulesPage extends JPanel {
    private static final Map<Category, String> ICONS = Map.of(
            Category.VISUALS, "eye", Category.HUD, "hud", Category.MOVEMENT, "move", Category.PLAYER, "user",
            Category.WORLD, "globe", Category.UTILITY, "wrench", Category.INVENTORY, "box", Category.INTERFACE, "palette",
            Category.PERFORMANCE, "gauge", Category.RECORDING, "video");

    private final LauncherState state;
    private final JTextField search = new JTextField();
    private final JPanel chips = new JPanel(new dev.lock.launcher.ui.WrapLayout(FlowLayout.LEFT, 6, 4));
    private final JPanel list = new JPanel();
    private final JPanel settings = new JPanel(new BorderLayout());
    private Category category = Category.HUD;
    private String selected = "fps";

    public ModulesPage(LauncherState state) {
        super(new BorderLayout(0, 12));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Components.h1("Modules"), BorderLayout.WEST);
        search.putClientProperty("JTextField.placeholderText", "Search modules");
        search.putClientProperty("JTextField.leadingIcon", Theme.icon("search", 14, Theme.DIM));
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(260, 32));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuildList(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuildList(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuildList(); }
        });
        head.add(search, BorderLayout.EAST);
        top.add(head, BorderLayout.NORTH);
        chips.setOpaque(false);
        top.add(chips, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JPanel listCard = new Components.Card(new BorderLayout(), Theme.PANEL, 14);
        listCard.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        JScrollPane ls = Components.scroll(list);
        listCard.add(ls);
        listCard.setPreferredSize(new Dimension(300, 100));
        JPanel center = new JPanel(new BorderLayout(14, 0));
        center.setOpaque(false);
        center.add(listCard, BorderLayout.WEST);
        settings.setOpaque(false);
        center.add(settings, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        state.onConfig(this::refresh);
        refresh();
    }

    private Object shownConfig;

    /** Local edits only repaint; a replaced configuration (sync from the game, import) rebuilds the editors. */
    private void refresh() {
        if (shownConfig == state.config()) { list.repaint(); return; }
        shownConfig = state.config();
        rebuildChips();
        rebuildList();
        rebuildSettings();
    }

    private void rebuildChips() {
        chips.removeAll();
        for (Category c : Category.values()) chips.add(chip(c));
        chips.revalidate();
        chips.repaint();
    }

    private JComponent chip(Category c) {
        boolean sel = c == category && search.getText().isBlank();
        JComponent chip = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.aa(g2);
                g2.setColor(sel ? Theme.ACCENT : new Color(255, 255, 255, 14));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                Color fg = sel ? Theme.onAccent() : Theme.TEXT;
                g2.setColor(fg);
                IconSet.paint(g2, ICONS.get(c), 11, getHeight() / 2.0 - 7, 14);
                g2.setFont(Theme.font(12, 1));
                g2.drawString(c.label, 31, getHeight() / 2 + 5);
                g2.dispose();
            }
        };
        int w = getFontMetrics(Theme.font(12, 1)).stringWidth(c.label) + 44;
        chip.setPreferredSize(new Dimension(w, 30));
        chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        chip.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                category = c;
                search.setText("");
                ModuleDef first = Catalog.byCategory(c).get(0);
                selected = first.id;
                rebuildChips();
                rebuildList();
                rebuildSettings();
            }
        });
        return chip;
    }

    private void rebuildList() {
        list.removeAll();
        String q = search.getText();
        for (ModuleDef d : Catalog.all().values()) {
            boolean show = q.isBlank() ? d.category == category : d.matches(q);
            if (show) list.add(new Row(d));
        }
        list.add(javax.swing.Box.createVerticalGlue());
        list.revalidate();
        list.repaint();
    }

    private void rebuildSettings() {
        settings.removeAll();
        ModuleDef d = Catalog.get(selected);
        if (d == null) return;
        ModuleState st = state.config().active().module(d.id);
        JPanel card = new Components.Card(new BorderLayout(0, 10), Theme.PANEL, 14);
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel head = new JPanel(new BorderLayout(10, 4));
        head.setOpaque(false);
        JLabel title = Components.label(d.name, 17, 2, Theme.TEXT);
        title.setIcon(Theme.icon(ICONS.get(d.category), 18, Theme.ACCENT));
        title.setIconTextGap(10);
        head.add(title, BorderLayout.WEST);
        if (d.toggleable()) {
            Components.Toggle t = new Components.Toggle(st.enabled());
            t.onChange(v -> state.edit(() -> st.setEnabled(v)));
            head.add(t, BorderLayout.EAST);
        }
        JTextArea desc = new JTextArea(d.description + (d.support != Support.SUPPORTED ? "\n\n" + d.support.label + ": " + d.supportNote : ""));
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setEditable(false);
        desc.setOpaque(false);
        desc.setFont(Theme.font(12, 0));
        desc.setForeground(d.support == Support.SUPPORTED ? Theme.DIM : Theme.WARN);
        desc.setBorder(BorderFactory.createEmptyBorder());
        JLabel meta = Components.label(d.category.label + "  ·  " + (d.toggleable() ? "toggleable" : "always on") + "  ·  " + (d.changesBehavior ? "changes game options" : "display only"), 11, 0, Theme.FAINT);
        JPanel headAll = new JPanel(new BorderLayout(0, 6));
        headAll.setOpaque(false);
        headAll.add(head, BorderLayout.NORTH);
        headAll.add(meta, BorderLayout.CENTER);
        headAll.add(desc, BorderLayout.SOUTH);
        card.add(headAll, BorderLayout.NORTH);
        JPanel editor = SettingsEditor.build(st, (k, v) -> state.edit(() -> st.set(k, v)));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(editor, BorderLayout.NORTH);
        JPanel foot = Components.row(8, Components.button("Reset module", "refresh", () -> state.edit(st::reset)));
        foot.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        wrap.add(foot, BorderLayout.CENTER);
        card.add(Components.scroll(wrap), BorderLayout.CENTER);
        settings.add(card);
        settings.revalidate();
        settings.repaint();
    }

    /** One module row: accent fill when enabled, outline when selected. Click selects; the right edge toggles. */
    private final class Row extends JComponent {
        private final ModuleDef d;
        private boolean hover;

        Row(ModuleDef d) {
            this.d = d;
            setPreferredSize(new Dimension(280, 34));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(d.description);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    ModuleState st = state.config().active().module(d.id);
                    boolean toggleZone = e.getX() > getWidth() - 46 || e.getButton() == MouseEvent.BUTTON3;
                    if (toggleZone && d.toggleable()) state.edit(() -> st.setEnabled(!st.enabled()));
                    else { selected = d.id; rebuildSettings(); list.repaint(); }
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.aa(g2);
            ModuleState st = state.config().active().module(d.id);
            boolean on = d.toggleable() && st.enabled();
            int h = getHeight() - 4;
            if (on) { g2.setColor(Theme.ACCENT); g2.fill(new RoundRectangle2D.Float(4, 2, getWidth() - 8, h, 10, 10)); }
            else if (hover) { g2.setColor(new Color(255, 255, 255, 12)); g2.fill(new RoundRectangle2D.Float(4, 2, getWidth() - 8, h, 10, 10)); }
            if (d.id.equals(selected)) {
                g2.setColor(on ? new Color(255, 255, 255, 140) : Theme.ACCENT);
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(4.75f, 2.75f, getWidth() - 9.5f, h - 1.5f, 10, 10));
            }
            Color fg = on ? Theme.onAccent() : d.toggleable() ? Theme.TEXT : Theme.DIM;
            g2.setColor(fg);
            g2.setFont(Theme.font(12.5f, 1));
            g2.drawString(d.name, 16, getHeight() / 2 + 5);
            if (d.toggleable()) {
                int key = st.key(ModuleDef.KEYBIND);
                if (key != Keys.NONE) {
                    g2.setFont(Theme.font(10.5f, 0));
                    String k = Keys.display(key);
                    g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 150));
                    g2.drawString(k, getWidth() - 52 - g2.getFontMetrics().stringWidth(k), getHeight() / 2 + 4);
                }
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 170));
                IconSet.paint(g2, on ? "check" : "plus", getWidth() - 34, getHeight() / 2.0 - 7, 14);
            } else {
                g2.setColor(Theme.FAINT);
                IconSet.paint(g2, "gear", getWidth() - 34, getHeight() / 2.0 - 7, 14);
            }
            g2.dispose();
        }
    }
}
