package dev.lock.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.lock.core.module.Anchor.*;
import static dev.lock.core.module.Category.*;

/**
 * The complete Lock module catalog. Every entry here is bound to a real implementation in the
 * in-game runtime ({@code dev.lock.client.modules}); the runtime's compatibility checker reports
 * any catalog entry without a bound implementation as a failure.
 */
public final class Catalog {
    public static final int KEY_RIGHT_SHIFT = 344, KEY_C = 67, KEY_F7 = 296, KEY_F8 = 297;

    /** Visibility groups managed by the visibility manager. */
    public static final List<String> GROUPS = List.of("hud", "sensitive", "crosshair", "world", "branding",
            "notifications", "modulelist", "customtext", "diagnostics");
    public static final Map<String, String> GROUP_LABELS = Map.of(
            "hud", "HUD elements", "sensitive", "Location & server info", "crosshair", "Custom crosshair",
            "world", "In-world markers", "branding", "Branding / watermark", "notifications", "Notifications",
            "modulelist", "Module list & indicators", "customtext", "Custom text", "diagnostics", "Diagnostic overlays");
    public static final List<String> MODES = List.of("Normal", "Minimal", "Recording", "Streamer", "Clean");

    private static final Map<String, ModuleDef> MODULES;

    static {
        List<ModuleDef> l = new ArrayList<>();

        // ---------------------------------------------------------------- Visuals
        l.add(ModuleDef.builder("crosshair", "Custom Crosshair", VISUALS).group("crosshair")
                .desc("Replaces the vanilla crosshair with a configurable one.").tags("aim", "reticle")
                .section("Shape")
                .choice("shape", "Shape", "Cross", "", "Cross", "Dot", "Circle", "Square", "T", "Cross + Dot")
                .num("size", "Size", 5, 1, 20, 1).num("thickness", "Thickness", 1, 1, 5, 1).num("gap", "Gap", 2, 0, 10, 1)
                .section("Colour")
                .color("color", "Colour", 0xFFFFFFFF).num("opacity", "Opacity", 1.0, 0.1, 1.0, 0.05)
                .bool("outline", "Outline", true).color("outlineColor", "Outline colour", 0xC0000000)
                .section("Dynamic states")
                .bool("dynamicGap", "Expand while moving", false, "Gap widens while you move.")
                .bool("targetColor", "Recolour on target", true, "Use the target colour while looking at an entity.")
                .color("targetColorValue", "Target colour", 0xFFFF5555)
                .bool("hideThirdPerson", "Hide in third person", true)
                .build());
        l.add(ModuleDef.builder("hitboxes", "Hitbox Overlay", VISUALS).group("world")
                .desc("Toggles the game's own entity hitbox renderer (the same as F3+B).").tags("debug", "bounding box")
                .support(Support.PARTIAL, "Uses the vanilla hitbox renderer; appearance is not customisable and only entities the game is already rendering are shown.")
                .build());
        l.add(ModuleDef.builder("waypoint_markers", "Waypoint Markers", VISUALS).group("world").enabled()
                .desc("Draws your saved waypoints as on-screen markers at their world positions.").tags("waypoint", "beacon")
                .bool("showName", "Show name", true).bool("showDistance", "Show distance", true)
                .num("maxDistance", "Max distance", 5000, 16, 100000, 16).num("scale", "Marker scale", 1.0, 0.5, 2.5, 0.05)
                .bool("fadeCenter", "Fade near crosshair", true, "Fades markers close to the screen centre.")
                .bool("edgeArrows", "Off-screen arrows", false, "Clamp markers behind you to the screen edge.")
                .build());
        l.add(ModuleDef.builder("notifications", "Visual Notifications", VISUALS).group("notifications").enabled()
                .desc("Short toasts for module toggles, profile/mode switches, waypoints and warnings.").tags("toast", "alert")
                .hudLayout(TOP_RIGHT, 4, 24, true)
                .section("Behaviour")
                .num("duration", "Duration (s)", 2.5, 0.5, 10, 0.5).num("max", "Max visible", 4, 1, 8, 1)
                .bool("moduleToggles", "Module toggles", true).bool("warnings", "Warnings", true).bool("system", "Profile & mode changes", true)
                .build());

        // ---------------------------------------------------------------- HUD
        l.add(hud("fps", "FPS Counter", "Frames per second.", TOP_LEFT, 4, 16).enabled()
                .bool("label", "Show label", true).bool("colorize", "Colour by value", false).build());
        l.add(hud("cps", "CPS Counter", "Clicks per second for left and right mouse buttons.", TOP_LEFT, 4, 30)
                .bool("label", "Show label", true).bool("right", "Show right button", true).build());
        l.add(hud("keystrokes", "Keystrokes", "Shows movement keys, jump and mouse buttons as you press them.", BOTTOM_LEFT, 4, 4)
                .bg(false)
                .section("Keys").bool("mouse", "Show mouse buttons", true).bool("space", "Show space bar", true).bool("cps", "CPS on mouse keys", true)
                .num("keySize", "Key size", 20, 12, 32, 1)
                .color("pressed", "Pressed colour", 0xC0FFFFFF).color("released", "Released colour", 0x80101114)
                .color("pressedText", "Pressed text", 0xFF101114).build());
        l.add(hud("mouse_buttons", "Mouse Buttons", "Live state of the left, middle and right mouse buttons.", BOTTOM_LEFT, 4, 90)
                .bool("middle", "Show middle button", true).color("pressed", "Pressed colour", 0xC0FFFFFF).build());
        l.add(hud("coordinates", "Coordinates", "Your position, block position and facing.", TOP_LEFT, 4, 44).group("sensitive")
                .bool("block", "Block coordinates", false, "Integer block coordinates instead of decimals.")
                .bool("facing", "Show facing", true).bool("nether", "Show Nether/Overworld conversion", false)
                .bool("chunk", "Show chunk", false).build());
        l.add(hud("compass", "Direction & Compass", "Heading as a compass strip or text.", TOP_CENTER, 0, 4)
                .choice("style", "Style", "Strip", "", "Strip", "Text")
                .num("width", "Strip width", 160, 60, 360, 10).bool("degrees", "Show degrees", true).build());
        l.add(hud("ping", "Ping", "Your latency as reported by the server's player list.", TOP_LEFT, 4, 72)
                .support(Support.PARTIAL, "Uses the latency the server publishes in the tab list; shows '--' in singleplayer or when the server hides it.")
                .bool("label", "Show label", true).bool("colorize", "Colour by value", true).build());
        l.add(hud("server_status", "Server & Connection", "Current server, connection type and player count.", TOP_LEFT, 4, 86).group("sensitive")
                .bool("address", "Show address", true).bool("players", "Show player count", true).build());
        l.add(hud("armor_hud", "Armor HUD", "Equipped armour with durability.", MIDDLE_RIGHT, 4, 0).bg(false)
                .choice("orientation", "Orientation", "Vertical", "", "Vertical", "Horizontal")
                .choice("durability", "Durability", "Percent", "", "Percent", "Value", "None")
                .bool("hands", "Include hands", true).build());
        l.add(hud("durability", "Durability HUD", "Damageable items you are wearing or holding, with warnings.", BOTTOM_RIGHT, 4, 40)
                .num("warn", "Warn below (%)", 10, 1, 50, 1).bool("notify", "Notify when low", true).bool("onlyDamaged", "Only damaged items", true).build());
        l.add(hud("potion_effects", "Potion Effects", "Active potion effects with level and remaining time.", MIDDLE_LEFT, 4, 0)
                .choice("sort", "Sort", "Duration", "", "Duration", "Name").bool("ambient", "Include beacon effects", true)
                .bool("blink", "Blink when expiring", true).build());
        l.add(hud("status", "Player Status", "Conditions such as burning, freezing, air, flying and swimming.", MIDDLE_LEFT, 4, 60)
                .bool("air", "Air supply", true).bool("fire", "Burning", true).bool("freeze", "Freezing", true)
                .bool("motion", "Flying / gliding / swimming", true).build());
        l.add(hud("timers", "Timers", "A stopwatch and a countdown timer.", TOP_RIGHT, 4, 4)
                .section("Stopwatch").key("swKey", "Start/stop key", Keys.NONE, "").key("swReset", "Reset key", Keys.NONE, "")
                .section("Countdown").num("countdown", "Countdown (min)", 5, 0.5, 120, 0.5).key("cdKey", "Start/stop key", Keys.NONE, "")
                .bool("cdNotify", "Notify when finished", true).build());
        l.add(hud("module_list", "Active Module List", "Lists enabled modules.", TOP_RIGHT, 2, 2).group("modulelist").bg(false)
                .choice("sort", "Sort", "Length", "", "Length", "Alphabetical").bool("hideHud", "Hide HUD modules", true)
                .bool("bars", "Accent bars", true).build());
        l.add(hud("module_indicators", "Module Status Indicators", "Compact chips for sprint, sneak, zoom, profile and mode.", BOTTOM_CENTER, 0, 50).group("modulelist")
                .bool("profile", "Show profile", false).bool("mode", "Show visibility mode", true).build());
        l.add(hud("custom_text", "Custom Text", "Your own text with placeholders like {fps}, {ping}, {x}, {biome}, {time}, {profile}.", TOP_LEFT, 4, 100).group("customtext")
                .text("text", "Text", "lock. | {fps} fps", 120, "Placeholders: {fps} {ping} {x} {y} {z} {biome} {dimension} {time} {day} {server} {profile} {mode} {clock}")
                .text("text2", "Second line", "", 120, "").build());
        l.add(hud("info_panel", "Custom Information Panel", "A panel built from the fields you choose.", TOP_LEFT, 4, 120)
                .text("title", "Title", "Info", 24, "").bool("fps", "FPS", true).bool("ping", "Ping", true).bool("coords", "Coordinates", false)
                .bool("direction", "Direction", true).bool("biome", "Biome", false).bool("dimension", "Dimension", false)
                .bool("time", "Game time", false).bool("speed", "Speed", false).bool("memory", "Memory", false).build());
        l.add(hud("held_item", "Held-Item Information", "Name, total count in inventory and durability of the held item.", BOTTOM_CENTER, 0, 64)
                .bool("total", "Total in inventory", true).bool("durability", "Durability", true).bool("icon", "Item icon", true).build());
        l.add(hud("inventory_hud", "Inventory HUD", "Shows the 27 main inventory slots on screen.", BOTTOM_RIGHT, 4, 4)
                .bool("emptyHide", "Hide when empty", true).build());
        l.add(hud("notification_feed", "Notification Feed", "A persistent list of the most recent notifications.", BOTTOM_RIGHT, 4, 120).group("notifications")
                .num("lines", "Lines", 5, 1, 12, 1).bool("timestamps", "Timestamps", true).build());

        // ---------------------------------------------------------------- Movement
        l.add(ModuleDef.builder("toggle_sprint", "Toggle Sprint", MOVEMENT).behavior()
                .desc("Press your sprint key once to keep sprinting.").tags("sprint")
                .choice("mode", "Mode", "Toggle", "Toggle: sprint key toggles. Always: sprint whenever moving forward.", "Toggle", "Always")
                .build());
        l.add(ModuleDef.builder("toggle_sneak", "Toggle Sneak", MOVEMENT).behavior()
                .desc("Press your sneak key once to keep sneaking.").tags("sneak", "crouch").build());
        l.add(ModuleDef.builder("zoom", "Zoom", MOVEMENT).behavior().enabled()
                .desc("Hold a key to zoom the camera.").tags("optifine", "spyglass")
                .key("zoomKey", "Zoom key", KEY_C, "").choice("mode", "Mode", "Hold", "", "Hold", "Toggle")
                .num("level", "Zoom level", 4, 1.5, 15, 0.5).bool("scroll", "Scroll to adjust", true)
                .num("sensitivity", "Sensitivity while zoomed", 0.5, 0.1, 1.0, 0.05, "Mouse sensitivity multiplier while zoomed.")
                .bool("smooth", "Smooth transition", true).bool("cinematic", "Cinematic camera while zoomed", false).build());
        l.add(ModuleDef.builder("perspective", "Perspective Toggle", MOVEMENT).behavior()
                .desc("Switch camera perspective with your own key.").tags("f5", "third person")
                .key("perspectiveKey", "Perspective key", Keys.NONE, "")
                .choice("mode", "Mode", "Cycle", "Cycle through views, or hold for a view and return on release.", "Cycle", "Hold")
                .choice("holdView", "Hold view", "Third person back", "", "Third person back", "Third person front").build());
        l.add(ModuleDef.builder("fov", "FOV Settings", MOVEMENT).behavior()
                .desc("Applies your field-of-view preferences while enabled; restores the previous values when disabled.").tags("field of view")
                .num("fov", "FOV", 70, 30, 110, 1).num("effects", "FOV effects", 1.0, 0, 1, 0.05, "Vanilla 'FOV Effects' (speed/sprint FOV changes).").build());
        l.add(ModuleDef.builder("view_bobbing", "View Bobbing", MOVEMENT).behavior()
                .desc("Controls the vanilla view-bobbing option.").bool("bobbing", "View bobbing", false).build());
        l.add(ModuleDef.builder("smooth_camera", "Smooth Camera", MOVEMENT).behavior()
                .desc("Toggles the vanilla cinematic (smooth) camera with a key.").tags("cinematic")
                .key("smoothKey", "Smooth camera key", Keys.NONE, "").build());
        l.add(ModuleDef.builder("sensitivity_profiles", "Sensitivity Profiles", MOVEMENT).behavior()
                .desc("Save three mouse sensitivities and switch between them.").tags("mouse", "dpi")
                .num("s1", "Profile 1 (%)", 100, 0, 200, 1).num("s2", "Profile 2 (%)", 70, 0, 200, 1).num("s3", "Profile 3 (%)", 130, 0, 200, 1)
                .num("active", "Active profile", 1, 1, 3, 1).key("cycleKey", "Cycle key", Keys.NONE, "").build());
        l.add(ModuleDef.builder("auto_jump", "Auto Jump Control", MOVEMENT).behavior()
                .desc("Controls the vanilla auto-jump option.").bool("autoJump", "Auto jump", false).build());
        l.add(hud("movement_indicators", "Movement Indicators", "Sprinting, sneaking, airborne, flying and speed.", BOTTOM_LEFT, 4, 110)
                .category(MOVEMENT).bool("speed", "Show speed", true).choice("units", "Speed units", "blocks/s", "", "blocks/s", "km/h").build());
        l.add(hud("sprint_status", "Sprint Status HUD", "Shows whether you are sprinting and whether toggle sprint is active.", BOTTOM_LEFT, 4, 124)
                .category(MOVEMENT).bool("sneak", "Include sneak", true).build());
        l.add(ModuleDef.builder("camera_controls", "Camera Controls", MOVEMENT).behavior()
                .desc("Camera comfort options: damage tilt, distortion effects and darkness pulsing.").tags("accessibility")
                .num("damageTilt", "Damage tilt", 1.0, 0, 1, 0.05).num("distortion", "Distortion effects", 1.0, 0, 1, 0.05)
                .num("darkness", "Darkness pulsing", 1.0, 0, 1, 0.05).build());

        // ---------------------------------------------------------------- Player
        l.add(hud("health_info", "Health Info", "Health, absorption, hunger and saturation as numbers.", BOTTOM_CENTER, 0, 78)
                .category(PLAYER).bool("saturation", "Saturation", true).bool("armor", "Armour points", false).build());
        l.add(hud("gameplay_stats", "Gameplay Statistics", "Distance travelled, jumps and deaths observed this session.", MIDDLE_LEFT, 4, 90)
                .category(PLAYER).bool("distance", "Distance", true).bool("jumps", "Jumps", true).bool("deaths", "Deaths", true).build());
        l.add(hud("session_stats", "Session Statistics", "Session length and time in the current world.", MIDDLE_LEFT, 4, 130)
                .category(PLAYER).bool("world", "Time in this world", true).build());

        // ---------------------------------------------------------------- World
        l.add(ModuleDef.builder("waypoints", "Waypoint Manager", WORLD).system()
                .desc("Saved locations per world/server and dimension. Manage them from the Waypoints tab.").tags("home", "location", "marker")
                .key("addKey", "Add waypoint key", Keys.NONE, "Creates a waypoint at your position.")
                .key("cycleKey", "Select next key", Keys.NONE, "Selects the next waypoint in this dimension.")
                .color("defaultColor", "Default colour", 0xFF6EA8FE).build());
        l.add(ModuleDef.builder("death_waypoint", "Death Waypoint", WORLD).enabled()
                .desc("Creates a waypoint where you die and selects it.").num("keep", "Keep last", 3, 1, 10, 1).build());
        l.add(hud("waypoint_distance", "Waypoint Distance", "Distance and direction to the selected waypoint.", TOP_CENTER, 0, 22)
                .category(WORLD).bool("coords", "Show coordinates", false).bool("arrow", "Direction arrow", true).build());
        l.add(hud("dimension", "Dimension Display", "The dimension you are in.", TOP_LEFT, 4, 140).category(WORLD).build());
        l.add(hud("biome", "Biome Display", "The biome at your position.", TOP_LEFT, 4, 154).category(WORLD).build());
        l.add(hud("time_info", "Time Information", "Game time, day number and optionally real time.", TOP_LEFT, 4, 168).category(WORLD)
                .bool("day", "Day number", true).bool("real", "Real time", false).bool("h24", "24-hour clock", true).build());
        l.add(hud("environment", "Environment Information", "Weather, light level, moon phase and difficulty.", TOP_LEFT, 4, 182).category(WORLD)
                .bool("weather", "Weather", true).bool("light", "Light level", true).bool("moon", "Moon phase", false).bool("difficulty", "Difficulty", false).build());
        l.add(hud("world_info", "World Information Overlay", "Biome, dimension, position and environment in one overlay.", TOP_LEFT, 4, 200).category(WORLD).group("sensitive")
                .bool("position", "Position", true).bool("biome", "Biome", true).bool("dimension", "Dimension", true).bool("weather", "Weather", true).build());
        l.add(ModuleDef.builder("navigation", "Navigation Preferences", WORLD).system()
                .desc("Shared formatting for coordinates, distances and directions.").tags("units")
                .num("decimals", "Coordinate decimals", 1, 0, 3, 1).choice("directions", "Direction labels", "Short", "", "Short", "Long")
                .bool("perServer", "Separate waypoints per world/server", true).build());

        // ---------------------------------------------------------------- Inventory
        l.add(ModuleDef.builder("item_tooltips", "Tooltip Customisation", INVENTORY).enabled()
                .desc("Adds durability numbers and other details to item tooltips.").tags("tooltip", "item info")
                .bool("durability", "Durability numbers", true).bool("food", "Food values", true).bool("id", "Item id", false).build());
        l.add(ModuleDef.builder("container_visuals", "Container Visuals", INVENTORY).behavior()
                .desc("Controls the vanilla menu background blur.").num("blur", "Menu blur", 5, 0, 10, 1).build());
        l.add(ModuleDef.builder("quick_keys", "Quick Keys", INVENTORY).system()
                .desc("Extra keybinds: open inventory, open the HUD editor and cycle profiles.").tags("keybind", "inventory")
                .key("inventoryKey", "Open inventory", Keys.NONE, "").key("hudEditorKey", "Open HUD editor", Keys.NONE, "")
                .key("profileKey", "Next profile", Keys.NONE, "").build());

        // ---------------------------------------------------------------- Interface
        l.add(ModuleDef.builder("click_gui", "Click GUI", INTERFACE).system()
                .desc("The in-game module manager.").tags("menu", "settings")
                .key("openKey", "Open key", KEY_RIGHT_SHIFT, "").num("scale", "GUI scale", 1.0, 0.6, 1.6, 0.05)
                .bool("tooltips", "Tooltips", true).bool("animations", "Animations", true).bool("pause", "Pause singleplayer", false).build());
        l.add(ModuleDef.builder("hud_editor", "HUD Editor", INTERFACE).system()
                .desc("Drag, scale and align HUD elements.").tags("layout", "position")
                .bool("snap", "Grid snapping", true).num("grid", "Grid size", 4, 2, 16, 1).bool("guides", "Alignment guides", true).build());
        l.add(ModuleDef.builder("hud_settings", "Visual Scale", INTERFACE).system()
                .desc("Global scale and visibility rules for all Lock HUD elements.").tags("scale")
                .num("scale", "Global HUD scale", 1.0, 0.5, 2.0, 0.05).bool("hideInDebug", "Hide with F3 screen", true)
                .bool("hideInScreens", "Hide while menus are open", false).build());
        l.add(ModuleDef.builder("theme", "Theme", INTERFACE).system()
                .desc("Accent and panel colours for everything Lock draws.").tags("color", "colour", "accent")
                .choice("preset", "Preset", "Teal", "", "Teal", "Blue", "Mint", "Rose", "Amber", "Violet", "Mono", "Custom")
                .color("accent", "Accent (custom)", 0xFF26A884).color("panel", "Panel (custom)", 0xF717181B).color("text", "Text (custom)", 0xFFDCDDE1).build());
        l.add(hud("watermark", "Watermark", "The 'lock.' wordmark.", TOP_LEFT, 4, 4).group("branding").enabled().bg(false).build());
        l.add(ModuleDef.builder("vanilla_hud", "Vanilla HUD Controls", INTERFACE).system()
                .desc("Show or hide individual vanilla HUD elements.").tags("hotbar", "health", "hunger", "xp")
                .bool("hotbar", "Hotbar", true).bool("health", "Health", true).bool("food", "Hunger", true).bool("armor", "Armour", true)
                .bool("air", "Air bubbles", true).bool("xp", "Experience bar", true).bool("crosshair", "Crosshair", true)
                .bool("effects", "Status effect icons", true).bool("scoreboard", "Scoreboard", true).bool("bossbar", "Boss bars", true)
                .bool("itemName", "Held item name", true).bool("actionbar", "Action bar", true).bool("titles", "Titles", true)
                .bool("vignette", "Vignette", true).build());
        l.add(ModuleDef.builder("sounds", "Sound Preferences", INTERFACE).system()
                .desc("Interface sounds for the Lock GUI.").bool("clicks", "Click sounds", true).num("volume", "Volume", 0.5, 0, 1, 0.05).build());

        // ---------------------------------------------------------------- Utility
        l.add(ModuleDef.builder("keybinds", "Keybind Manager", UTILITY).system()
                .desc("All Lock keybinds in one place, with conflict detection against Lock and vanilla binds.").tags("keys")
                .bool("inScreens", "Allow binds in menus", false).bool("warnConflicts", "Warn about conflicts", true).build());
        l.add(ModuleDef.builder("profiles", "Profile Manager", UTILITY).system()
                .desc("Create, rename, duplicate, delete and switch profiles from the Profiles tab.").tags("config")
                .bool("notify", "Notify on switch", true).build());
        l.add(ModuleDef.builder("config", "Configuration Manager", UTILITY).system()
                .desc("Autosave, backups, export and import.").tags("save", "backup", "import", "export")
                .num("autosave", "Autosave delay (s)", 2, 1, 30, 1).num("backups", "Backups kept", 5, 0, 20, 1).build());
        l.add(ModuleDef.builder("diagnostics", "Diagnostics", UTILITY).system()
                .desc("Runtime status, compatibility checks and the error log (Diagnostics tab).").tags("errors", "log", "status")
                .bool("errorToasts", "Notify on errors", true).build());

        // ---------------------------------------------------------------- Performance
        l.add(hud("performance", "Performance Statistics", "Average FPS, 1% low and frame time.", TOP_LEFT, 4, 220).category(PERFORMANCE).group("diagnostics")
                .bool("low", "1% low", true).bool("frametime", "Frame time", true).build());
        l.add(hud("frame_graph", "Frame-Time Display", "A graph of recent frame times.", BOTTOM_RIGHT, 4, 200).category(PERFORMANCE).group("diagnostics")
                .num("width", "Width", 120, 60, 240, 10).num("height", "Height", 30, 16, 80, 2).num("targetMs", "Scale (ms)", 33, 8, 100, 1).build());
        l.add(hud("memory", "Memory Information", "JVM heap usage.", TOP_LEFT, 4, 236).category(PERFORMANCE).group("diagnostics")
                .bool("percent", "Show percent", true).build());
        l.add(hud("diag_overlay", "Diagnostic Overlay", "Runtime integration status, hook status, Lock render cost and error count.", TOP_LEFT, 4, 250)
                .category(PERFORMANCE).group("diagnostics").build());
        l.add(ModuleDef.builder("perf_log", "Performance Logging", PERFORMANCE)
                .desc("Writes FPS, frame time and memory to a CSV file in the Lock logs folder.").tags("csv", "benchmark")
                .num("interval", "Interval (s)", 1, 1, 60, 1).build());

        // ---------------------------------------------------------------- Recording & visibility
        ModuleDef.Builder vis = ModuleDef.builder("visibility", "Visibility Manager", RECORDING).system()
                .desc("Presentation modes (Normal, Minimal, Recording, Streamer, Clean) that decide which Lock elements are drawn.")
                .tags("streamer", "recording", "clean", "hide", "panic")
                .choice("mode", "Mode", "Normal", "", MODES.toArray(String[]::new))
                .key("cycleKey", "Cycle mode key", Keys.NONE, "")
                .key("cleanKey", "Clean screen / restore key", KEY_F7, "Hides every Lock overlay; press again to restore the previous mode. The Click GUI key always works.")
                .bool("cleanVanilla", "Clean screen hides vanilla HUD", false);
        for (String mode : MODES) {
            if (mode.equals("Clean")) continue;
            vis.section(mode + " mode");
            for (String g : GROUPS) vis.bool(modeKey(mode, g), GROUP_LABELS.get(g), defaultVisible(mode, g));
        }
        l.add(vis.build());
        l.add(ModuleDef.builder("capture_status", "Capture Compatibility", RECORDING).system()
                .desc("Explains how Lock overlays appear in screenshots, OBS and other recordings.")
                .support(Support.UNSUPPORTED, "Lock draws inside Minecraft's own frame, so whatever you see is exactly what screenshots and recordings capture. Separate capture-only visibility is not offered; use the Recording or Streamer mode to change what is drawn for everyone.")
                .build());

        Map<String, ModuleDef> m = new LinkedHashMap<>();
        for (ModuleDef d : l) if (m.put(d.id, d) != null) throw new IllegalStateException("duplicate module " + d.id);
        MODULES = Collections.unmodifiableMap(m);
    }

    public static String modeKey(String mode, String group) { return "mode." + mode.toLowerCase() + "." + group; }

    static boolean defaultVisible(String mode, String group) {
        return switch (mode) {
            case "Minimal" -> group.equals("crosshair") || group.equals("world") || group.equals("notifications") || group.equals("hud");
            case "Recording" -> !group.equals("diagnostics") && !group.equals("modulelist") && !group.equals("branding");
            case "Streamer" -> !group.equals("sensitive") && !group.equals("diagnostics");
            default -> true;
        };
    }

    private Catalog() {}

    public static Map<String, ModuleDef> all() { return MODULES; }

    public static ModuleDef get(String id) { return MODULES.get(id); }

    public static List<ModuleDef> byCategory(Category c) {
        List<ModuleDef> out = new ArrayList<>();
        for (ModuleDef d : MODULES.values()) if (d.category == c) out.add(d);
        return out;
    }

    private static HudBuilder hud(String id, String name, String desc, Anchor a, int x, int y) {
        return new HudBuilder(id, name, desc, a, x, y);
    }

    /** Builder wrapper for HUD modules that lets the category and defaults be adjusted fluently. */
    private static final class HudBuilder {
        private final String id, name, desc;
        private final Anchor anchor;
        private final int x, y;
        private Category category = HUD;
        private final List<java.util.function.Consumer<ModuleDef.Builder>> ops = new ArrayList<>();
        private boolean bg = true;

        HudBuilder(String id, String name, String desc, Anchor anchor, int x, int y) {
            this.id = id; this.name = name; this.desc = desc; this.anchor = anchor; this.x = x; this.y = y;
        }

        HudBuilder category(Category c) { category = c; return this; }
        HudBuilder bg(boolean v) { bg = v; return this; }
        HudBuilder enabled() { ops.add(ModuleDef.Builder::enabled); return this; }
        HudBuilder group(String g) { ops.add(b -> b.group(g)); return this; }
        HudBuilder support(Support s, String n) { ops.add(b -> b.support(s, n)); return this; }
        HudBuilder section(String s) { ops.add(b -> b.section(s)); return this; }
        HudBuilder bool(String i, String n, boolean d) { ops.add(b -> b.bool(i, n, d)); return this; }
        HudBuilder bool(String i, String n, boolean d, String ds) { ops.add(b -> b.bool(i, n, d, ds)); return this; }
        HudBuilder num(String i, String n, double d, double mn, double mx, double st) { ops.add(b -> b.num(i, n, d, mn, mx, st)); return this; }
        HudBuilder color(String i, String n, int d) { ops.add(b -> b.color(i, n, d)); return this; }
        HudBuilder choice(String i, String n, String d, String ds, String... o) { ops.add(b -> b.choice(i, n, d, ds, o)); return this; }
        HudBuilder text(String i, String n, String d, int max, String ds) { ops.add(b -> b.text(i, n, d, max, ds)); return this; }
        HudBuilder key(String i, String n, int d, String ds) { ops.add(b -> b.key(i, n, d, ds)); return this; }

        ModuleDef build() {
            ModuleDef.Builder real = ModuleDef.builder(id, name, category).desc(desc).section("Module");
            for (var op : ops) op.accept(real);
            return real.hudLayout(anchor, x, y, bg).build();
        }
    }
}
