package lock.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ModeSetting extends Setting<String> {
    public final String[] modes;

    public ModeSetting(String name, String def, String... modes) {
        super(name, def);
        this.modes = modes;
    }

    public boolean is(String m) { return value.equals(m); }

    public void cycle() { cycle(1); }

    public void cycle(int dir) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(value)) { value = modes[((i + dir) % modes.length + modes.length) % modes.length]; return; }
        }
        value = modes[0];
    }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) {
        String s = e.getAsString();
        for (String m : modes) if (m.equals(s)) { value = s; return; }
    }
}
