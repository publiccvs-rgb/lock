package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.core.ModuleManager;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.SimpleModule;

/** Binds every catalog entry to its implementation. */
public final class Modules {
    private Modules() {}

    public static void registerAll(LockClient l) {
        ModuleManager m = l.modules;
        // Visuals
        m.register(new VisualModules.Crosshair(l));
        m.register(new VisualModules.Hitboxes(l));
        m.register(new VisualModules.WaypointMarkers(l));
        m.register(new VisualModules.Notifications(l));
        // HUD
        m.register(new HudModules.Fps(l));
        m.register(new HudModules.Cps(l));
        m.register(new HudModules.Keystrokes(l));
        m.register(new HudModules.MouseButtons(l));
        m.register(new HudModules.Coordinates(l));
        m.register(new HudModules.Compass(l));
        m.register(new HudModules.Ping(l));
        m.register(new HudModules.ServerStatus(l));
        m.register(new HudModules.ArmorHud(l));
        m.register(new HudModules.Durability(l));
        m.register(new HudModules.PotionEffects(l));
        m.register(new HudModules.Status(l));
        m.register(new HudModules.Timers(l));
        m.register(new HudModules.ModuleList(l));
        m.register(new HudModules.ModuleIndicators(l));
        m.register(new HudModules.CustomText(l));
        m.register(new HudModules.InfoPanel(l));
        m.register(new HudModules.HeldItem(l));
        m.register(new HudModules.InventoryHud(l));
        m.register(new HudModules.NotificationFeed(l));
        // Movement
        m.register(new MovementModules.ToggleSprint(l));
        m.register(new MovementModules.ToggleSneak(l));
        m.register(new MovementModules.Zoom(l));
        m.register(new MovementModules.Perspective(l));
        m.register(new MovementModules.Fov(l));
        m.register(new MovementModules.ViewBobbing(l));
        m.register(new MovementModules.SmoothCamera(l));
        m.register(new MovementModules.SensitivityProfiles(l));
        m.register(new MovementModules.AutoJump(l));
        m.register(new MovementModules.MovementIndicators(l));
        m.register(new MovementModules.SprintStatus(l));
        m.register(new MovementModules.CameraControls(l));
        // Player
        m.register(new PlayerModules.HealthInfo(l));
        m.register(new PlayerModules.GameplayStats(l));
        m.register(new PlayerModules.SessionStats(l));
        // World
        m.register(new WorldModules.Waypoints(l));
        m.register(new WorldModules.DeathWaypoint(l));
        m.register(new WorldModules.WaypointDistance(l));
        m.register(new WorldModules.Dimension(l));
        m.register(new WorldModules.Biome(l));
        m.register(new WorldModules.TimeInfo(l));
        m.register(new WorldModules.Environment(l));
        m.register(new WorldModules.WorldInfo(l));
        m.register(simple(l, "navigation"));
        // Inventory
        m.register(new InventoryModules.Tooltips(l));
        m.register(new InventoryModules.ContainerVisuals(l));
        m.register(new InventoryModules.QuickKeys(l));
        // Interface
        m.register(new InterfaceModules.ClickGui(l));
        m.register(simple(l, "hud_editor"));
        m.register(simple(l, "hud_settings"));
        m.register(simple(l, "theme"));
        m.register(new InterfaceModules.Watermark(l));
        m.register(simple(l, "vanilla_hud"));
        m.register(simple(l, "sounds"));
        // Utility
        m.register(simple(l, "keybinds"));
        m.register(simple(l, "profiles"));
        m.register(simple(l, "config"));
        m.register(simple(l, "diagnostics"));
        // Performance
        m.register(new PerformanceModules.Performance(l));
        m.register(new PerformanceModules.FrameGraph(l));
        m.register(new PerformanceModules.Memory(l));
        m.register(new PerformanceModules.DiagOverlay(l));
        m.register(new PerformanceModules.PerfLog(l));
        // Recording & visibility
        m.register(new Visibility(l));
        m.register(simple(l, "capture_status"));
    }

    private static ClientModule simple(LockClient l, String id) { return new SimpleModule(l, id); }

    /** Mode keys for the visibility manager. */
    public static final class Visibility extends ClientModule {
        public Visibility(LockClient l) { super(l, "visibility"); }

        @Override public void onBind(String id, boolean pressed) {
            if (!pressed) return;
            if (id.equals("cleanKey")) lock.visibility.toggleClean();
            else if (id.equals("cycleKey")) lock.visibility.cycle();
        }
    }
}
