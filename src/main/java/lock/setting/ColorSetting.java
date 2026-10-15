package lock.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** RGB colour stored as 0xRRGGBB; edited in the GUI through a small preset swatch row. */
public class ColorSetting extends Setting<Integer> {
    public static final int[] PRESETS = {
        0x14F2BE, 0x8FD3FF, 0xB9A8FF, 0xFFE08A, 0xFF9DB5, 0xFFFFFF, 0xFF5C5C
    };

    public ColorSetting(String name, int def) { super(name, def); }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { value = e.getAsInt() & 0xFFFFFF; }
}
