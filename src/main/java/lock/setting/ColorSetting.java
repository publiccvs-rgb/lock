package lock.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** RGB colour stored as 0xRRGGBB; edited in the GUI through a small preset swatch row. */
public class ColorSetting extends Setting<Integer> {
    public static final int[] PRESETS = {
        0x22D3EE, 0x19F2BE, 0x4F8CFF, 0x9B7BFF, 0xFF6BD6, 0xFFD479, 0xFF5C5C, 0x4DFF6B, 0xFFFFFF
    };

    public ColorSetting(String name, int def) { super(name, def); }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { value = e.getAsInt() & 0xFFFFFF; }
}
