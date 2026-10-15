package dev.lock.launcher;

import dev.lock.core.ipc.RuntimeClient;
import dev.lock.core.json.Json;
import dev.lock.core.util.LockPaths;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Lock Launcher entry point. Without arguments it opens the desktop app; with --cli it runs
 * headless commands (useful for scripting and testing):
 * <pre>
 *   --cli check [--mc DIR]       installation and compatibility checks
 *   --cli install [--mc DIR]     add/update the Lock profile in the official launcher
 *   --cli uninstall [--mc DIR]   remove it
 *   --cli processes              running Minecraft processes and Lock status
 *   --cli status                 live status of the running Lock game
 * </pre>
 */
public final class LauncherMain {
    private LauncherMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--cli")) {
            System.exit(cli(args));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            LauncherState state = new LauncherState();
            Theme.install(); // re-apply with the profile's accent
            new LauncherFrame(state).setVisible(true);
        });
    }

    static int cli(String[] args) throws Exception {
        String cmd = args.length > 1 ? args[1] : "check";
        Path mc = LockPaths.defaultMinecraftDir();
        for (int i = 2; i < args.length - 1; i++) if (args[i].equals("--mc")) mc = Paths.get(args[i + 1]);
        Installer in = new Installer(mc);
        switch (cmd) {
            case "check" -> {
                boolean ok = true;
                for (Installer.Check c : in.checks()) {
                    System.out.println((c.ok() ? "[ok]   " : "[warn] ") + c.label() + ": " + c.detail());
                    ok &= c.ok();
                }
                return ok ? 0 : 1;
            }
            case "install" -> { System.out.println(in.install(Installer.DEFAULT_JVM)); return 0; }
            case "uninstall" -> { System.out.println(in.uninstall()); return 0; }
            case "processes" -> {
                List<ProcessScanner.Game> games = ProcessScanner.scan();
                if (games.isEmpty()) System.out.println("No Minecraft process found.");
                for (ProcessScanner.Game g : games)
                    System.out.println("pid " + g.pid() + "  Minecraft " + g.version() + "  " + (g.lock() ? "Lock loaded (IPC port " + g.runtime().port() + ")" : "without Lock"));
                return 0;
            }
            case "status" -> {
                for (ProcessScanner.Game g : ProcessScanner.scan()) {
                    if (!g.lock()) continue;
                    try (RuntimeClient c = new RuntimeClient(g.runtime())) {
                        Map<String, Object> s = c.send("status", null);
                        System.out.println(Json.pretty(s));
                        return 0;
                    }
                }
                System.out.println("No running game with Lock.");
                return 1;
            }
            default -> {
                System.err.println("unknown command " + cmd);
                return 2;
            }
        }
    }
}
