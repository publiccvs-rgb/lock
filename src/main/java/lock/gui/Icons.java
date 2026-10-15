package lock.gui;

/**
 * Pixel-art glyphs. The game's 2D layer snaps to whole GUI pixels, so hand-drawn bitmaps read cleaner than
 * composed shapes. Runs of pixels in a row are merged into one fill.
 */
final class Icons {
    private Icons() {}

    private static final String[] EYE = {
        "   #####   ",
        " ##     ## ",
        "##  ###  ##",
        "##  ###  ##",
        " ##     ## ",
        "   #####   ",
    };
    private static final String[] GEAR = {
        "    #    ",
        " #  #  # ",
        "  #####  ",
        "  #   #  ",
        "###   ###",
        "  #   #  ",
        "  #####  ",
        " #  #  # ",
        "    #    ",
    };
    private static final String[] SEARCH = {
        " #####   ",
        "#     #  ",
        "#     #  ",
        "#     #  ",
        "#     #  ",
        "#     #  ",
        " ######  ",
        "       # ",
        "        #",
    };
    private static final String[] LIST = {
        "#########",
        "         ",
        "######   ",
        "         ",
        "#########",
    };
    private static final String[] USER = {
        "   ###   ",
        "  #####  ",
        "  #####  ",
        "   ###   ",
        "         ",
        " ####### ",
        "#########",
        "#########",
    };
    private static final String[] STAR = {
        "    #    ",
        "   ###   ",
        "   ###   ",
        "#########",
        " ####### ",
        "  #####  ",
        " ### ### ",
        " ##   ## ",
    };
    private static final String[] BARS = {
        " ## ## ",
        " ## ## ",
        " ## ## ",
        " ## ## ",
        " ## ## ",
        " ## ## ",
        " ## ## ",
    };
    private static final String[] GLOBE = {
        "  #####  ",
        " #  #  # ",
        "#   #   #",
        "#########",
        "#   #   #",
        " #  #  # ",
        "  #####  ",
    };
    private static final String[] KEYBIND = {
        " ####### ",
        "#       #",
        "#       #",
        "#   #   #",
        "#       #",
        "#       #",
        " ####### ",
    };
    private static final String[] DISC = {
        "  #####  ",
        " ####### ",
        "#########",
        "#########",
        "#########",
        "#########",
        "#########",
        " ####### ",
        "  #####  ",
    };
    private static final String[] PLUS = {
        "  #  ",
        "  #  ",
        "#####",
        "  #  ",
        "  #  ",
    };

    static void draw(Gfx g, String[] bmp, float x, float y, int argb) {
        x = Math.round(x); y = Math.round(y);
        for (int r = 0; r < bmp.length; r++) {
            String row = bmp[r];
            int c = 0, n = row.length();
            while (c < n) {
                if (row.charAt(c) != '#') { c++; continue; }
                int s = c;
                while (c < n && row.charAt(c) == '#') c++;
                g.rect(x + s, y + r, c - s, 1, argb);
            }
        }
    }

    static void eye(Gfx g, float x, float y, int c) { draw(g, EYE, x - 1, y + 1.5f, c); }
    static void gear(Gfx g, float x, float y, int c) { draw(g, GEAR, x, y, c); }
    static void search(Gfx g, float x, float y, int c) { draw(g, SEARCH, x, y, c); }
    static void list(Gfx g, float x, float y, int c) { draw(g, LIST, x, y + 2, c); }
    static void user(Gfx g, float x, float y, int c) { draw(g, USER, x, y + 0.5f, c); }
    static void star(Gfx g, float x, float y, int c) { draw(g, STAR, x, y + 0.5f, c); }
    static void bars(Gfx g, float x, float y, int c) { draw(g, BARS, x, y + 1, c); }
    static void globe(Gfx g, float x, float y, int c) { draw(g, GLOBE, x, y + 1, c); }
    static void keybind(Gfx g, float x, float y, int c) { draw(g, KEYBIND, x, y + 1, c); }

    static void plusCircle(Gfx g, float x, float y, int c, int ink) {
        draw(g, DISC, x, y, c);
        draw(g, PLUS, x + 2, y + 2, ink);
    }

    /** dir: -1 up, 1 down, 2 right. 5x3 (or 3x5) px, 1px stroke. */
    static void chevron(Gfx g, float x, float y, int dir, int c) {
        x = Math.round(x); y = Math.round(y);
        if (dir == 2) {
            g.rect(x, y, 1, 1, c); g.rect(x + 1, y + 1, 1, 1, c); g.rect(x + 2, y + 2, 1, 1, c);
            g.rect(x + 1, y + 3, 1, 1, c); g.rect(x, y + 4, 1, 1, c);
            return;
        }
        for (int i = 0; i < 3; i++) {
            float row = dir < 0 ? 2 - i : i;
            g.rect(x + i, y + row, 1, 1, c);
            g.rect(x + 4 - i, y + row, 1, 1, c);
        }
    }

    /** Vertical kebab: three 2x2 squares. */
    static void dots(Gfx g, float x, float y, int c) {
        x = Math.round(x); y = Math.round(y);
        for (int i = 0; i < 3; i++) g.rect(x, y + i * 3, 2, 2, c);
    }
}
