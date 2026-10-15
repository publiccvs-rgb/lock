package dev.lock.core.bridge;

/** Vanilla HUD elements the runtime can suppress. Ordinals are baked into injected bytecode. */
public enum VanillaElement {
    HOTBAR("hotbar"), HEALTH("health"), FOOD("food"), ARMOR("armor"), AIR("air"), XP("xp"),
    CROSSHAIR("crosshair"), EFFECTS("effects"), SCOREBOARD("scoreboard"), BOSSBAR("bossbar"),
    ITEM_NAME("itemName"), ACTIONBAR("actionbar"), TITLES("titles"), VIGNETTE("vignette");

    /** Setting key in the "vanilla_hud" module. */
    public final String setting;

    VanillaElement(String setting) { this.setting = setting; }
}
