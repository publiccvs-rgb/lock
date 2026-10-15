package lock.gui;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The menu's text face: Arial, loaded straight from the system's fonts (it is not bundled), rendered through the
 * game's own TrueType glyph provider. Falls back to Minecraft's default font if no usable face is found.
 */
public final class UiFont {
    private static final String[] CANDIDATES = {
        "C:/Windows/Fonts/arial.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/msttcorefonts/Arial.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
    };

    /** Em size in GUI pixels (Arial cap height ~0.72em, which matches the reference's text/row ratio). */
    private static final float SIZE = 8.6f, OVERSAMPLE = 4f;

    private static final String[] BOLD_CANDIDATES = {
        "C:/Windows/Fonts/arialbd.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/usr/share/fonts/truetype/msttcorefonts/Arial_Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/liberation/LiberationSans-Bold.ttf",
    };

    private static Font font, boldFont;
    private static boolean tried, triedBold;

    private UiFont() {}

    public static Font get() {
        if (!tried) {
            tried = true;
            try { font = build(CANDIDATES, "font/ui"); } catch (Throwable t) { font = null; }
        }
        return font != null ? font : Minecraft.getInstance().font;
    }

    /** Arial Bold for titles; falls back to the regular face. */
    public static Font bold() {
        if (!triedBold) {
            triedBold = true;
            try { boldFont = build(BOLD_CANDIDATES, "font/ui_bold"); } catch (Throwable t) { boldFont = null; }
        }
        return boldFont != null ? boldFont : get();
    }

    private static Font build(String[] candidates, String id) throws Exception {
        Path file = null;
        for (String c : candidates) if (Files.isRegularFile(Path.of(c))) { file = Path.of(c); break; }
        if (file == null) return null;

        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer mem = MemoryUtil.memAlloc(bytes.length);
        mem.put(bytes).flip();

        FT_Face face;
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer out = stack.mallocPointer(1);
                FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), mem, 0L, out), "Initializing font face");
                face = FT_Face.create(out.get());
            }
            FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
        }

        TrueTypeGlyphProvider provider = new TrueTypeGlyphProvider(mem, face, SIZE, OVERSAMPLE, 0f, 0f, "");
        GlyphStitcher stitcher = new GlyphStitcher(Minecraft.getInstance().getTextureManager(), Identifier.fromNamespaceAndPath("lock", id));
        FontSet set = new FontSet(stitcher);
        set.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());

        return new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription description) { return set.source(false); }
            @Override public EffectGlyph effect() { return set.whiteGlyph(); }
        });
    }
}
