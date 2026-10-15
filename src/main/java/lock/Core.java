package lock;

import lock.module.ModuleManager;

public final class Core {
    public static ModuleManager modules;

    private Core() {}

    public static void init() {
        modules = new ModuleManager();
        modules.load();
    }
}
