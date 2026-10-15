package lock.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumSetting extends Setting<Double> {
    public final double min, max, step;

    public NumSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public float f() { return value.floatValue(); }
    public int i() { return (int) Math.round(value); }

    @Override public void set(Double v) {
        double snapped = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, snapped));
    }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) { set(e.getAsDouble()); }
}
