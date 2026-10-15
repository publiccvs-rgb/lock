package dev.lock.client.module;

import dev.lock.client.LockClient;
import dev.lock.client.render.Gfx;
import dev.lock.core.module.Anchor;
import dev.lock.core.module.ModuleDef;

/**
 * A positionable HUD element. Subclasses report their unscaled size and draw at (0, 0); the HUD
 * manager applies anchor, offset, scale, opacity, background and border from the module's settings.
 */
public abstract class HudModule extends ClientModule {
    /** Last on-screen bounds (GUI pixels), used by the HUD editor for hit testing. */
    public int lastX, lastY, lastW, lastH;
    public long lastRenderNanos;
    /** True while the HUD editor draws this element; dynamic elements then show placeholder content. */
    public boolean editing;

    protected HudModule(LockClient lock, String id) { super(lock, id); }

    /** Unscaled content size {width, height}, excluding padding. */
    public abstract int[] size(Gfx gfx);

    /** Draws content at (0,0) in unscaled coordinates. {@code w,h} is the content box. */
    public abstract void draw(Gfx gfx, int w, int h);

    /** True when there is nothing worth drawing (the element is skipped entirely, except in the editor). */
    public boolean empty() { return false; }

    /** Content shown in the HUD editor when the live content is empty. */
    public void preview() {}

    public Anchor anchor() {
        try { return Anchor.valueOf(s(ModuleDef.H_ANCHOR)); } catch (IllegalArgumentException e) { return Anchor.TOP_LEFT; }
    }

    public int padding() { return i(ModuleDef.H_PADDING); }
    public float textScale() { return (float) n(ModuleDef.H_TEXT_SCALE); }
    public int textColor() { return color(ModuleDef.H_COLOR); }
    public boolean shadow() { return b(ModuleDef.H_SHADOW); }
    public boolean compact() { return b(ModuleDef.H_COMPACT); }
    public String align() { return s(ModuleDef.H_ALIGN); }

    public int labelColor() { return b(ModuleDef.H_ACCENT) ? lock.theme.accent() : dev.lock.client.core.ThemeManager.withAlpha(textColor(), 0xA0); }
}
