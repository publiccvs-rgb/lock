package dev.lock.launcher.pages;

import dev.lock.core.config.Waypoint;
import dev.lock.launcher.LauncherState;
import dev.lock.launcher.Theme;
import dev.lock.launcher.ui.Components;

import javax.swing.BorderFactory;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Saved locations: create, edit, rename, delete, colour, visibility; filtered by world/server. */
public final class WaypointsPage extends JPanel {
    private static final String[] COLS = {"", "Name", "X", "Y", "Z", "Dimension", "World / server", "Visible"};
    private final LauncherState state;
    private final JComboBox<String> scope = new JComboBox<>();
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private List<Waypoint> rows = new ArrayList<>();

    public WaypointsPage(LauncherState state) {
        super(new BorderLayout(0, 14));
        this.state = state;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel t = new JPanel(new BorderLayout(0, 4));
        t.setOpaque(false);
        t.add(Components.h1("Waypoints"), BorderLayout.NORTH);
        t.add(Components.sub("Double-click a cell to edit. Waypoints are created in game at your position, or here."), BorderLayout.SOUTH);
        head.add(t, BorderLayout.WEST);
        scope.addActionListener(e -> reload());
        head.add(scope, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setFont(Theme.font(12.5f, 0));
        table.getTableHeader().setFont(Theme.font(11, 2));
        table.getColumnModel().getColumn(0).setMaxWidth(36);
        table.getColumnModel().getColumn(7).setMaxWidth(70);
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, "", s, f, r, c);
                l.setIcon(Components.dot(new Color((Integer) v | 0xFF000000), 12));
                l.setHorizontalAlignment(CENTER);
                return l;
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c != 0 || e.getClickCount() < 2) return;
                Waypoint w = rows.get(r);
                Color chosen = JColorChooser.showDialog(WaypointsPage.this, "Waypoint colour", new Color(w.color));
                if (chosen != null) state.edit(() -> w.color = chosen.getRGB() | 0xFF000000);
            }
        });
        JPanel card = new Components.Card(new BorderLayout(), Theme.PANEL, 16);
        card.add(Components.scroll(table));
        add(card, BorderLayout.CENTER);
        add(Components.row(8,
                Components.primary("Add", "plus", this::add),
                Components.button("Delete", "trash", this::delete),
                Components.button("Set as target", "pin", () -> {
                    int r = table.getSelectedRow();
                    if (r >= 0) state.edit(() -> state.config().selectedWaypoint = rows.get(r).id);
                })), BorderLayout.SOUTH);
        state.onConfig(this::refreshScopes);
        refreshScopes();
    }

    private void refreshScopes() {
        Object cur = scope.getSelectedItem();
        TreeSet<String> scopes = new TreeSet<>();
        for (Waypoint w : state.config().waypoints) scopes.add(w.scope);
        scope.removeAllItems();
        scope.addItem("All worlds");
        for (String s : scopes) scope.addItem(s);
        if (cur != null) scope.setSelectedItem(cur);
        reload();
    }

    private void reload() {
        String sc = (String) scope.getSelectedItem();
        rows = new ArrayList<>();
        for (Waypoint w : state.config().waypoints) if (sc == null || sc.equals("All worlds") || w.scope.equals(sc)) rows.add(w);
        model.fireTableDataChanged();
    }

    private void add() {
        String sc = (String) scope.getSelectedItem();
        if (sc == null || sc.equals("All worlds")) sc = "*";
        String scopeFinal = sc;
        state.edit(() -> state.config().waypoints.add(new Waypoint("Waypoint " + (state.config().waypoints.size() + 1), 0, 64, 0, "minecraft:overworld", scopeFinal, 0xFF26A884)));
        reload();
    }

    private void delete() {
        int r = table.getSelectedRow();
        if (r < 0) return;
        Waypoint w = rows.get(r);
        if (JOptionPane.showConfirmDialog(this, "Delete \"" + w.name + "\"?", "Waypoints", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        state.edit(() -> state.config().waypoints.remove(w));
        reload();
    }

    private final class Model extends AbstractTableModel {
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 7 ? Boolean.class : c == 0 ? Integer.class : Object.class; }
        @Override public boolean isCellEditable(int r, int c) { return c > 0; }

        @Override public Object getValueAt(int r, int c) {
            Waypoint w = rows.get(r);
            return switch (c) {
                case 0 -> w.color;
                case 1 -> w.name;
                case 2 -> w.x;
                case 3 -> w.y;
                case 4 -> w.z;
                case 5 -> w.dimension;
                case 6 -> w.scope;
                default -> w.visible;
            };
        }

        @Override public void setValueAt(Object v, int r, int c) {
            Waypoint w = rows.get(r);
            state.edit(() -> {
                try {
                    switch (c) {
                        case 1 -> { String s = String.valueOf(v).trim(); if (!s.isEmpty()) w.name = s.length() > 48 ? s.substring(0, 48) : s; }
                        case 2 -> w.x = Integer.parseInt(String.valueOf(v).trim());
                        case 3 -> w.y = Integer.parseInt(String.valueOf(v).trim());
                        case 4 -> w.z = Integer.parseInt(String.valueOf(v).trim());
                        case 5 -> w.dimension = String.valueOf(v).trim();
                        case 6 -> w.scope = String.valueOf(v).trim();
                        case 7 -> w.visible = Boolean.TRUE.equals(v);
                        default -> { }
                    }
                } catch (NumberFormatException ignored) {
                    // invalid coordinate: keep the previous value
                }
            });
        }
    }

}
