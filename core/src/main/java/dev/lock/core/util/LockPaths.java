package dev.lock.core.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Locations of Lock's own files and of the default Minecraft installation. */
public final class LockPaths {
    private LockPaths() {}

    public static String os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "osx";
        return "linux";
    }

    /** Lock's data directory. Overridable with the LOCK_HOME environment variable or -Dlock.home. */
    public static Path home() {
        String override = System.getProperty("lock.home");
        if (override == null || override.isBlank()) override = System.getenv("LOCK_HOME");
        if (override != null && !override.isBlank()) return Paths.get(override);
        String userHome = System.getProperty("user.home");
        return switch (os()) {
            case "windows" -> {
                String appData = System.getenv("APPDATA");
                yield Paths.get(appData != null ? appData : userHome, "Lock");
            }
            case "osx" -> Paths.get(userHome, "Library", "Application Support", "Lock");
            default -> {
                String xdg = System.getenv("XDG_CONFIG_HOME");
                yield (xdg != null && !xdg.isBlank() ? Paths.get(xdg) : Paths.get(userHome, ".config")).resolve("lock");
            }
        };
    }

    public static Path config() { return home().resolve("config.json"); }
    public static Path logs() { return home().resolve("logs"); }
    public static Path cache() { return home().resolve("cache"); }
    public static Path runtime() { return home().resolve("runtime"); }
    public static Path backups() { return home().resolve("backups"); }

    /** The default .minecraft directory of the official launcher. */
    public static Path defaultMinecraftDir() {
        String userHome = System.getProperty("user.home");
        return switch (os()) {
            case "windows" -> {
                String appData = System.getenv("APPDATA");
                yield Paths.get(appData != null ? appData : userHome, ".minecraft");
            }
            case "osx" -> Paths.get(userHome, "Library", "Application Support", "minecraft");
            default -> Paths.get(userHome, ".minecraft");
        };
    }
}
