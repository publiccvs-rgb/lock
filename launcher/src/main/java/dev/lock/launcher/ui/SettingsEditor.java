package dev.lock.launcher.ui;

import dev.lock.core.config.ModuleState;
import dev.lock.core.module.Keys;
import dev.lock.core.module.Setting;
import dev.lock.launcher.Theme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.BiConsumer;

/** Builds an editor for a module's settings from its schema. Every change is reported to {@code onChange}. */
public final class SettingsEditor {
    private SettingsEditor() {}

    public static JPanel build(ModuleState st, BiConsumer<String, Object> onChange) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        String group = null;
        for (Setting<?> s : st.def.settings().values()) {
            if (!s.group.equals(group)) {
                group = s.group;
                if (!group.isEmpty() && !group.equals("Module")) p.add(left(Components.section(group)));
            }
            p.add(left(row(st, s, onChange)));
        }
        return p;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JPanel row(ModuleState st, Setting<?> s, BiConsumer<String, Object> onChange) {
        JPanel r = new JPanel(new BorderLayout(12, 2));
        r.setOpaque(false);
        r.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        JLabel name = Components.label(s.name, 12.5f, 0, Theme.TEXT);
        if (!s.description.isEmpty()) name.setToolTipText(s.description);
        r.add(name, BorderLayout.WEST);
        switch (s) {
            case Setting.Bool b -> {
                Components.Toggle t = new Components.Toggle(st.get(b));
                t.onChange(v -> onChange.accept(s.id, v));
                r.add(t, BorderLayout.EAST);
            }
            case Setting.Num n -> {
                double v = st.get(n);
                if (n.max - n.min > 500 || n.step <= 0) {
                    JSpinner sp = new JSpinner(new SpinnerNumberModel(v, n.min, n.max, Math.max(n.step, 0.01)));
                    sp.setPreferredSize(new Dimension(110, 28));
                    sp.addChangeListener(e -> onChange.accept(s.id, ((Number) sp.getValue()).doubleValue()));
                    r.add(sp, BorderLayout.EAST);
                } else {
                    int steps = (int) Math.round((n.max - n.min) / n.step);
                    JSlider sl = new JSlider(0, steps, (int) Math.round((v - n.min) / n.step));
                    sl.setOpaque(false);
                    sl.setPreferredSize(new Dimension(180, 24));
                    JLabel val = Components.label(n.format(v), 12, 1, Theme.ACCENT);
                    val.setPreferredSize(new Dimension(44, 20));
                    sl.addChangeListener(e -> {
                        double nv = n.min + sl.getValue() * n.step;
                        val.setText(n.format(nv));
                        if (!sl.getValueIsAdjusting()) onChange.accept(s.id, nv);
                    });
                    JPanel east = new JPanel(new BorderLayout(6, 0));
                    east.setOpaque(false);
                    east.add(sl, BorderLayout.CENTER);
                    east.add(val, BorderLayout.EAST);
                    r.add(east, BorderLayout.EAST);
                }
            }
            case Setting.Choice c -> {
                JComboBox<String> cb = new JComboBox<>(c.options.toArray(String[]::new));
                cb.setSelectedItem(st.get(c));
                cb.setPreferredSize(new Dimension(170, 28));
                cb.addActionListener(e -> onChange.accept(s.id, cb.getSelectedItem()));
                r.add(cb, BorderLayout.EAST);
            }
            case Setting.Color c -> {
                int argb = st.get(c);
                JButton sw = new JButton(Setting.Color.format(argb));
                sw.setFont(Theme.font(11.5f, 1));
                sw.setIcon(Components.dot(new Color(argb, true), 14));
                sw.setFocusPainted(false);
                sw.addActionListener(e -> {
                    Color chosen = JColorChooser.showDialog(sw, s.name, new Color(argb, true));
                    if (chosen == null) return;
                    int nv = (argb & 0xFF000000) | (chosen.getRGB() & 0xFFFFFF);
                    sw.setText(Setting.Color.format(nv));
                    sw.setIcon(Components.dot(new Color(nv, true), 14));
                    onChange.accept(s.id, nv);
                });
                r.add(sw, BorderLayout.EAST);
            }
            case Setting.Text t -> {
                JTextField f = new JTextField(st.get(t));
                f.setPreferredSize(new Dimension(240, 28));
                f.getDocument().addDocumentListener(new DocumentListener() {
                    void push() { onChange.accept(s.id, f.getText()); }
                    @Override public void insertUpdate(DocumentEvent e) { push(); }
                    @Override public void removeUpdate(DocumentEvent e) { push(); }
                    @Override public void changedUpdate(DocumentEvent e) { push(); }
                });
                r.add(f, BorderLayout.EAST);
            }
            case Setting.Key k -> r.add(keyButton(st.get(k), code -> onChange.accept(s.id, code)), BorderLayout.EAST);
        }
        return r;
    }

    /** Button that captures the next key press. Escape clears the binding. */
    public static JButton keyButton(int current, java.util.function.IntConsumer onBind) {
        JButton b = new JButton(Keys.display(current));
        b.setFont(Theme.font(11.5f, 1));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(130, 28));
        final boolean[] listening = {false};
        b.addActionListener(e -> {
            listening[0] = true;
            b.setText("Press a key…");
            b.setBackground(Theme.ACCENT);
            b.setForeground(Theme.onAccent());
            b.requestFocusInWindow();
        });
        b.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!listening[0]) return;
                listening[0] = false;
                int code = e.getKeyCode() == KeyEvent.VK_ESCAPE ? Keys.NONE : KeyMap.toGlfw(e);
                b.setText(Keys.display(code));
                b.setBackground(null);
                b.setForeground(null);
                onBind.accept(code);
                e.consume();
            }
        });
        return b;
    }

    static Component gap(int h) { return Box.createVerticalStrut(h); }
}
