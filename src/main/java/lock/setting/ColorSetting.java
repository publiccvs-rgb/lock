package lock.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** RGB colour stored as 0xRRGGBB; edited in the GUI through the named scheme strip (Theme.SCHEME_*). */
public class ColorSetting extends Setting<Integer> {
    public static final int[] PRESETS = {
        0x3B82F6, 0x1CE8A6, 0xE8B84B, 0xFF7A3D, 0xFF4D6D, 0xA368FF, 0xFF5DA0, 0xD8D8D8
    };

    public ColorSetting(String name, int def) { super(name, def); }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { value = e.getAsInt() & 0xFFFFFF; }
}
