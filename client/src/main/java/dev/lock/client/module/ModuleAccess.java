package dev.lock.client.module;

/** Lets the module manager drive lifecycle methods without making them public API. */
public final class ModuleAccess {
    private ModuleAccess() {}

    public static void setActive(ClientModule m, boolean a) { m.active = a; }

    public static void enable(ClientModule m) { m.onEnable(); }

    public static void disable(ClientModule m) { m.onDisable(); }

    public static int error(ClientModule m) { return ++m.errors; }
}
