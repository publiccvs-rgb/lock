package dev.lock.core.module;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GLFW key codes and their display names. Lock stores keybinds by name ("RIGHT_SHIFT") in
 * configuration so files stay readable; the integer codes are the GLFW constants the game uses.
 * Mouse buttons are encoded as {@code MOUSE_BASE + button}.
 */
public final class Keys {
    public static final int NONE = -1;
    public static final int MOUSE_BASE = 1000;

    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, Integer> CODES = new LinkedHashMap<>();

    static {
        put(NONE, "NONE");
        for (char c = 'A'; c <= 'Z'; c++) put(c, String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) put(c, String.valueOf(c));
        for (int i = 1; i <= 25; i++) put(289 + i, "F" + i);
        for (int i = 0; i <= 9; i++) put(320 + i, "KP_" + i);
        put(32, "SPACE"); put(39, "APOSTROPHE"); put(44, "COMMA"); put(45, "MINUS"); put(46, "PERIOD");
        put(47, "SLASH"); put(59, "SEMICOLON"); put(61, "EQUAL"); put(91, "LEFT_BRACKET"); put(92, "BACKSLASH");
        put(93, "RIGHT_BRACKET"); put(96, "GRAVE"); put(256, "ESCAPE"); put(257, "ENTER"); put(258, "TAB");
        put(259, "BACKSPACE"); put(260, "INSERT"); put(261, "DELETE"); put(262, "RIGHT"); put(263, "LEFT");
        put(264, "DOWN"); put(265, "UP"); put(266, "PAGE_UP"); put(267, "PAGE_DOWN"); put(268, "HOME");
        put(269, "END"); put(280, "CAPS_LOCK"); put(281, "SCROLL_LOCK"); put(282, "NUM_LOCK");
        put(283, "PRINT_SCREEN"); put(284, "PAUSE"); put(330, "KP_DECIMAL"); put(331, "KP_DIVIDE");
        put(332, "KP_MULTIPLY"); put(333, "KP_SUBTRACT"); put(334, "KP_ADD"); put(335, "KP_ENTER");
        put(336, "KP_EQUAL"); put(340, "LEFT_SHIFT"); put(341, "LEFT_CONTROL"); put(342, "LEFT_ALT");
        put(343, "LEFT_SUPER"); put(344, "RIGHT_SHIFT"); put(345, "RIGHT_CONTROL"); put(346, "RIGHT_ALT");
        put(347, "RIGHT_SUPER"); put(348, "MENU");
        for (int b = 0; b < 8; b++) put(MOUSE_BASE + b, "MOUSE_" + (b + 1));
    }

    private static void put(int code, String name) {
        NAMES.put(code, name);
        CODES.put(name, code);
    }

    private Keys() {}

    public static String name(int code) {
        String n = NAMES.get(code);
        return n != null ? n : "KEY_" + code;
    }

    public static Integer byName(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase().replace(' ', '_');
        Integer c = CODES.get(n);
        if (c != null) return c;
        if (n.startsWith("KEY_")) {
            try {
                int v = Integer.parseInt(n.substring(4));
                return isValid(v) ? v : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static boolean isValid(int code) {
        return code == NONE || (code >= 32 && code <= 348) || (code >= MOUSE_BASE && code < MOUSE_BASE + 8);
    }

    public static boolean isMouse(int code) { return code >= MOUSE_BASE && code < MOUSE_BASE + 8; }

    /** Human-friendly label ("Right Shift"). */
    public static String display(int code) {
        if (code == NONE) return "None";
        String n = name(code);
        StringBuilder sb = new StringBuilder();
        for (String part : n.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static Map<Integer, String> all() { return java.util.Collections.unmodifiableMap(NAMES); }
}
