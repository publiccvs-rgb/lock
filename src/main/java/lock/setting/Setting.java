package lock.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    public final String name;
    protected T value;
    protected final T def;

    protected Setting(String name, T def) {
        this.name = name;
        this.value = def;
        this.def = def;
    }

    public T get() { return value; }
    public void set(T v) { value = v; }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);
}
