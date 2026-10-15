package dev.lock.launcher;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import dev.lock.core.ui.IconSet;
import dev.lock.core.ui.Palette;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.InputStream;
import java.util.Map;

/** Launcher look: FlatLaf dark tuned to Lock's palette, Poppins type and the shared line icons. */
public final class Theme {
    public static Color ACCENT = c(Palette.accent("Teal", 0));
    public static final Color BG = c(Palette.BG), PANEL = c(Palette.PANEL), PANEL_2 = c(Palette.PANEL_2), SUNKEN = c(Palette.SUNKEN),
            FIELD = c(Palette.FIELD), TEXT = c(Palette.TEXT), DIM = c(Palette.DIM), FAINT = c(Palette.FAINT),
            OK = c(Palette.OK), WARN = c(Palette.WARN), ERROR = c(Palette.ERROR), LINE = new Color(255, 255, 255, 20);
    private static final Font[] FONTS = new Font[3];

    private Theme() {}

    public static Color c(int argb) { return new Color(argb, true); }

    public static Color onAccent() { return c(Palette.onColor(ACCENT.getRGB())); }

    public static void setAccent(int argb) { ACCENT = c(argb); }

    /** weight: 0 regular, 1 medium, 2 semibold. */
    public static Font font(float size, int weight) {
        if (FONTS[weight] == null) {
            String file = new String[]{"Regular", "Medium", "SemiBold"}[weight];
            try (InputStream in = Theme.class.getClassLoader().getResourceAsStream("fonts/Poppins-" + file + ".ttf")) {
                FONTS[weight] = in == null ? new Font(Font.SANS_SERIF, weight == 0 ? Font.PLAIN : Font.BOLD, 12) : Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                FONTS[weight] = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
        }
        return FONTS[weight].deriveFont(size);
    }

    public static void install() {
        System.setProperty("flatlaf.useWindowDecorations", "true");
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", hex(ACCENT), "@background", hex(BG), "@foreground", hex(TEXT)));
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", font(12.5f, 0));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Button.borderWidth", 0);
        UIManager.put("Button.background", FIELD);
        UIManager.put("Button.hoverBackground", new Color(0x2C2E33));
        UIManager.put("Button.default.background", ACCENT);
        UIManager.put("Button.default.foreground", onAccent());
        UIManager.put("TextField.background", FIELD);
        UIManager.put("ComboBox.background", FIELD);
        UIManager.put("Spinner.background", FIELD);
        UIManager.put("Component.borderColor", new Color(0x2A2C31));
        UIManager.put("Panel.background", BG);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.alternateRowColor", new Color(0x1A1B1E));
        UIManager.put("Table.selectionBackground", ACCENT.darker());
        UIManager.put("TableHeader.background", PANEL_2);
        UIManager.put("TitlePane.background", BG);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("Slider.trackWidth", 3);
        UIManager.put("Slider.thumbSize", new java.awt.Dimension(12, 12));
        UIManager.put("ToolTip.background", new Color(0x222327));
        UIManager.put("OptionPane.background", PANEL);
    }

    private static String hex(Color c) { return String.format("#%06X", c.getRGB() & 0xFFFFFF); }

    public static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** Swing icon drawn from the shared vector set. */
    public static Icon icon(String name, int size, Color color) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color);
                IconSet.paint(g2, name, x, y, size);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }
}
