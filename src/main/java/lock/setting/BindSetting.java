package lock.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Keybind with hold/toggle behaviour. Key -1 = unbound. */
public class BindSetting extends Setting<Integer> {
    public boolean hold;

    public BindSetting(String name) { super(name, -1); }

    @Override public JsonElement save() {
        JsonObject o = new JsonObject();
        o.addProperty("key", value);
        o.addProperty("hold", hold);
        return o;
    }

    @Override public void load(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        value = o.get("key").getAsInt();
        hold = o.get("hold").getAsBoolean();
    }

    public String keyName() {
        if (value < 0) return "None";
        String n = org.lwjgl.glfw.GLFW.glfwGetKeyName(value, 0);
        return n != null ? n.toUpperCase() : "Key " + value;
    }
}
