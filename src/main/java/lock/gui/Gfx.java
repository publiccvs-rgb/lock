package lock.gui;

/** Minimal 2D surface the menu draws on, so the same layout code runs in-game and in the preview tool. */
public interface Gfx {
    void rect(float x, float y, float w, float h, int argb);
    void round(float x, float y, float w, float h, float r, int argb);
    /** Rounded rect with an individual radius per corner (top-left, top-right, bottom-right, bottom-left). */
    void roundEx(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb);
    /** Vertical gradient inside a rounded rect (per-corner radii). */
    void gradient(float x, float y, float w, float h, float tl, float tr, float br, float bl, int top, int bottom);
    void text(String s, float x, float y, int argb, float scale, boolean bold);
    float width(String s, float scale, boolean bold);
    void clip(float x, float y, float w, float h);
    void unclip();
    /** Multiplies the alpha of everything drawn afterwards (used for fade in/out). */
    void alpha(float a);

    default void text(String s, float x, float y, int argb, float scale) { text(s, x, y, argb, scale, false); }
    default float width(String s, float scale) { return width(s, scale, false); }
    default void gradient(float x, float y, float w, float h, float r, int top, int bottom) { gradient(x, y, w, h, r, r, r, r, top, bottom); }
}
