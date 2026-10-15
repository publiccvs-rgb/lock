package lock.gui;

/** Minimal 2D surface the menu draws on, so the same layout code runs in-game and in the preview tool. */
public interface Gfx {
    void rect(float x, float y, float w, float h, int argb);
    void round(float x, float y, float w, float h, float r, int argb);
    /** Rounded rect with an individual radius per corner (top-left, top-right, bottom-right, bottom-left). */
    void roundEx(float x, float y, float w, float h, float tl, float tr, float br, float bl, int argb);
    void text(String s, float x, float y, int argb, float scale);
    float width(String s, float scale);
    void clip(float x, float y, float w, float h);
    void unclip();
    /** Multiplies the alpha of everything drawn afterwards (used for fade in/out). */
    void alpha(float a);
}
