package dev.lock.launcher.ui;

import dev.lock.core.module.Keys;

import java.awt.event.KeyEvent;

/** Converts AWT key events to the GLFW key codes the game (and Lock's config) uses. */
public final class KeyMap {
    private KeyMap() {}

    public static int toGlfw(KeyEvent e) {
        int k = e.getKeyCode();
        boolean right = e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT;
        if (k >= KeyEvent.VK_A && k <= KeyEvent.VK_Z) return k;
        if (k >= KeyEvent.VK_0 && k <= KeyEvent.VK_9) return k;
        if (k >= KeyEvent.VK_F1 && k <= KeyEvent.VK_F12) return 290 + (k - KeyEvent.VK_F1);
        if (k >= KeyEvent.VK_F13 && k <= KeyEvent.VK_F24) return 302 + (k - KeyEvent.VK_F13);
        if (k >= KeyEvent.VK_NUMPAD0 && k <= KeyEvent.VK_NUMPAD9) return 320 + (k - KeyEvent.VK_NUMPAD0);
        return switch (k) {
            case KeyEvent.VK_SHIFT -> right ? 344 : 340;
            case KeyEvent.VK_CONTROL -> right ? 345 : 341;
            case KeyEvent.VK_ALT -> right ? 346 : 342;
            case KeyEvent.VK_ALT_GRAPH -> 346;
            case KeyEvent.VK_META, KeyEvent.VK_WINDOWS -> right ? 347 : 343;
            case KeyEvent.VK_SPACE -> 32;
            case KeyEvent.VK_ENTER -> e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD ? 335 : 257;
            case KeyEvent.VK_TAB -> 258;
            case KeyEvent.VK_BACK_SPACE -> 259;
            case KeyEvent.VK_INSERT -> 260;
            case KeyEvent.VK_DELETE -> 261;
            case KeyEvent.VK_RIGHT -> 262;
            case KeyEvent.VK_LEFT -> 263;
            case KeyEvent.VK_DOWN -> 264;
            case KeyEvent.VK_UP -> 265;
            case KeyEvent.VK_PAGE_UP -> 266;
            case KeyEvent.VK_PAGE_DOWN -> 267;
            case KeyEvent.VK_HOME -> 268;
            case KeyEvent.VK_END -> 269;
            case KeyEvent.VK_CAPS_LOCK -> 280;
            case KeyEvent.VK_SCROLL_LOCK -> 281;
            case KeyEvent.VK_NUM_LOCK -> 282;
            case KeyEvent.VK_PRINTSCREEN -> 283;
            case KeyEvent.VK_PAUSE -> 284;
            case KeyEvent.VK_MINUS -> 45;
            case KeyEvent.VK_EQUALS -> 61;
            case KeyEvent.VK_COMMA -> 44;
            case KeyEvent.VK_PERIOD -> 46;
            case KeyEvent.VK_SLASH -> 47;
            case KeyEvent.VK_SEMICOLON -> 59;
            case KeyEvent.VK_OPEN_BRACKET -> 91;
            case KeyEvent.VK_CLOSE_BRACKET -> 93;
            case KeyEvent.VK_BACK_SLASH -> 92;
            case KeyEvent.VK_QUOTE -> 39;
            case KeyEvent.VK_BACK_QUOTE -> 96;
            case KeyEvent.VK_MULTIPLY -> 332;
            case KeyEvent.VK_ADD -> 334;
            case KeyEvent.VK_SUBTRACT -> 333;
            case KeyEvent.VK_DIVIDE -> 331;
            case KeyEvent.VK_DECIMAL -> 330;
            default -> Keys.NONE;
        };
    }
}
