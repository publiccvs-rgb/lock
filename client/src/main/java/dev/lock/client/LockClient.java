package dev.lock.client;

import dev.lock.client.core.ConfigManager;
import dev.lock.client.core.Diagnostics;
import dev.lock.client.core.HudManager;
import dev.lock.client.core.KeybindManager;
import dev.lock.client.core.ModuleManager;
import dev.lock.client.core.NotificationManager;
import dev.lock.client.core.Stats;
import dev.lock.client.core.ThemeManager;
import dev.lock.client.core.VisibilityManager;
import dev.lock.client.core.WaypointManager;
import dev.lock.client.gui.HudEditorScreen;
import dev.lock.client.module.ClientModule;
import dev.lock.client.modules.Modules;
import dev.lock.client.modules.VisualModules;
import dev.lock.client.modules.MovementModules;
import dev.lock.client.modules.InventoryModules;
import dev.lock.client.render.Gfx;
import dev.lock.core.bridge.AgentContext;
import dev.lock.core.bridge.ClientBridge;
import dev.lock.core.bridge.VanillaElement;
import dev.lock.core.config.LockConfig;
import dev.lock.core.config.ModuleState;
import dev.lock.core.ipc.RuntimeServer;
import dev.lock.core.json.Json;
import dev.lock.core.json.JsonUtil;
import dev.lock.core.module.Keys;
import dev.lock.core.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** The in-game half of Lock. Created by the agent on the first client tick. */
public final class LockClient implements ClientBridge {
    private static LockClient instance;

    public final Minecraft mc = Minecraft.getInstance();
    public final AgentContext agent;
    public final NotificationManager notifications = new NotificationManager();
    public final ConfigManager config;
    public final ThemeManager theme;
    public final VisibilityManager visibility;
    public final ModuleManager modules;
    public final KeybindManager keys;
    public final HudManager hud;
    public final WaypointManager waypoints;
    public final Stats stats = new Stats();
    public final Diagnostics diagnostics;
    /** Field of view of the last rendered frame (after zoom), used for world-to-screen projection. */
    public volatile float lastFov = 70f;
    private boolean checked;

    public LockClient(AgentContext agent) {
        instance = this;
        this.agent = agent;
        this.config = new ConfigManager();
        notifications.bind(config);
        this.theme = new ThemeManager(config);
        this.visibility = new VisibilityManager(config, notifications);
        this.modules = new ModuleManager(this);
        this.keys = new KeybindManager(this);
        this.hud = new HudManager(this);
        this.waypoints = new WaypointManager(this);
        this.diagnostics = new Diagnostics(this);
        Modules.registerAll(this);
        modules.finish();
        if (config.recoveredFrom() != null) notifications.warn("Configuration", "Recovered from an unreadable config file");
        Log.info("Lock client ready: " + modules.all().size() + " modules, profile '" + config.profile().name() + "'");
    }

    public static LockClient get() { return instance; }

    /** Records a setting edit made through any UI. */
    public void settingChanged(ClientModule m, String key) {
        config.changed();
        modules.guard(m, "settings", () -> m.onSettingChanged(key));
    }

    public void switchProfile(String name) {
        if (!config.config().switchTo(name)) return;
        config.changed();
        modules.syncAll();
        if (config.state("profiles").bool("notify")) notifications.system("Profile", name);
    }

    // ------------------------------------------------------------------ bridge

    @Override
    public void tick() {
        if (!checked) {
            checked = true;
            diagnostics.runCompatibilityChecks();
        }
        stats.tick();
        modules.tick();
        config.tick();
    }

    @Override
    public void renderHud(Object graphics, Object delta) {
        stats.frame();
        if (mc.player == null) return;
        if (mc.screen instanceof HudEditorScreen) return; // the editor draws the HUD itself
        ModuleState hs = config.state("hud_settings");
        if (hs.bool("hideInDebug") && mc.debugEntries.isOverlayVisible()) return;
        if (hs.bool("hideInScreens") && mc.screen != null) return;
        Gfx gfx = new Gfx((GuiGraphics) graphics);
        VisualModules.Crosshair ch = modules.get("crosshair", VisualModules.Crosshair.class);
        if (ch.active() && visibility.visible("crosshair")) modules.guard(ch, "render", () -> ch.render(gfx));
        VisualModules.WaypointMarkers wm = modules.get("waypoint_markers", VisualModules.WaypointMarkers.class);
        if (wm.active() && visibility.visible("world")) modules.guard(wm, "render", () -> wm.render(gfx));
        hud.render(gfx, false);
    }

    @Override
    public boolean cancelVanilla(int element) {
        if (visibility.hideVanilla()) return true;
        VanillaElement e = VanillaElement.values()[element];
        if (!config.state("vanilla_hud").bool(e.setting)) return true;
        if (e == VanillaElement.CROSSHAIR) {
            VisualModules.Crosshair ch = modules.get("crosshair", VisualModules.Crosshair.class);
            return ch.active() && visibility.visible("crosshair") && ch.replacesVanilla();
        }
        return false;
    }

    @Override
    public float modifyFov(float fov, boolean world) {
        if (!world) return fov;
        float f = modules.get("zoom", MovementModules.Zoom.class).apply(fov);
        lastFov = f;
        return f;
    }

    @Override
    public double modifyTurn(double delta) {
        return delta * modules.get("zoom", MovementModules.Zoom.class).sensitivityFactor();
    }

    @Override
    public void mouseButton(Object info, int action) {
        int button = ((MouseButtonInfo) info).button();
        if (action == 1 && mc.screen == null) stats.click(button);
        if (action == 1 || action == 0) keys.onInput(Keys.MOUSE_BASE + button, action == 1);
    }

    @Override
    public void key(Object event, int action) {
        if (action == 2) return; // repeat
        keys.onInput(((KeyEvent) event).key(), action == 1);
    }

    @Override
    public boolean mouseScroll(double amount) {
        if (mc.screen != null) return false;
        return modules.get("zoom", MovementModules.Zoom.class).scroll(amount);
    }

    @Override
    public void tooltip(Object stack, Object lines) {
        modules.get("item_tooltips", InventoryModules.Tooltips.class).append(stack, lines);
    }

    // ------------------------------------------------------------------ IPC

    @Override
    public Map<String, Object> handleCommand(Map<String, Object> req) {
        CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                f.complete(command(req));
            } catch (Throwable t) {
                Log.error("IPC command failed", t);
                f.complete(RuntimeServer.error(t.toString()));
            }
        });
        try {
            return f.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return RuntimeServer.error("game thread busy");
        }
    }

    private Map<String, Object> command(Map<String, Object> req) {
        String cmd = JsonUtil.str(req, "cmd", "");
        Map<String, Object> r = RuntimeServer.ok();
        switch (cmd) {
            case "status" -> {
                r.put("clientReady", true);
                r.put("profile", config.profile().name());
                r.put("mode", visibility.mode());
                r.put("configVersion", config.version());
                r.put("fps", mc.getFps());
                r.put("inWorld", mc.player != null);
                r.put("hudMicros", Math.round(hud.averageCostMicros()));
                r.put("modules", diagnostics.moduleStatus());
                r.put("compatibility", new ArrayList<>(diagnostics.compatibility));
            }
            case "getConfig" -> {
                r.put("configVersion", config.version());
                r.put("config", config.config().toJson());
            }
            case "setConfig" -> {
                List<String> problems = new ArrayList<>();
                LockConfig c = LockConfig.fromJson(JsonUtil.obj(req.get("config")), problems);
                config.replace(c);
                modules.syncAll();
                for (ClientModule m : modules.all()) modules.guard(m, "settings", () -> m.onSettingChanged("*"));
                r.put("problems", problems);
                r.put("configVersion", config.version());
            }
            case "setEnabled" -> modules.setEnabled(JsonUtil.str(req, "module", ""), JsonUtil.bool(req, "enabled", false));
            case "setSetting" -> {
                ClientModule m = modules.get(JsonUtil.str(req, "module", ""));
                if (m == null || !m.state().set(JsonUtil.str(req, "key", ""), req.get("value"))) return RuntimeServer.error("unknown module or setting");
                settingChanged(m, JsonUtil.str(req, "key", ""));
            }
            case "switchProfile" -> switchProfile(JsonUtil.str(req, "name", ""));
            case "setMode" -> visibility.setMode(JsonUtil.str(req, "mode", ""));
            case "openGui" -> mc.setScreen(new dev.lock.client.gui.LockScreen(null));
            case "openHudEditor" -> mc.setScreen(new HudEditorScreen(null));
            case "closeGui" -> mc.setScreen(null);
            case "report" -> {
                try {
                    r.put("path", diagnostics.writeReport().toString());
                } catch (Exception e) {
                    return RuntimeServer.error(e.toString());
                }
            }
            default -> {
                return RuntimeServer.error("unknown command " + cmd);
            }
        }
        return r;
    }

    /** Small helper for debugging from tests. */
    public String dump() { return Json.write(new LinkedHashMap<>(config.config().toJson())); }
}
