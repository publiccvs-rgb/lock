package lock.module;

import lock.setting.*;

/** Replaces the look of the sky: aurora ribbons, nebula haze, stars; plus time and weather overrides. */
public class Ambience extends Module {
    public final BoolSetting sky = add(new BoolSetting("Sky", true)).d("Draws the animated atmosphere over the Overworld sky at night.");
    public final ModeSetting mode = add(new ModeSetting("Sky Mode", "Aurora", "Aurora", "Nebula", "Sunset Haze", "Clear")).d("Aurora curtains, wide nebula ribbons, a sunset horizon glow, or stars only.");
    public final ModeSetting colors = add(new ModeSetting("Aurora Colors", "Theme", "Theme", "Natural", "Ice", "Ember")).d("Menu accent colors or natural green-blue northern lights.");
    public final NumSetting intensity = add(new NumSetting("Sky Intensity", 1.0, 0.0, 2.0, 0.05)).d("Brightness of the sky glow and atmospheric detail.");
    public final NumSetting speed = add(new NumSetting("Sky Speed", 5.0, 0.0, 10.0, 0.5)).d("Controls ribbon and cloud movement. Zero freezes the sky.");
    public final NumSetting softness = add(new NumSetting("Sky Softness", 0.7, 0.0, 1.0, 0.05)).d("Softens curtains and blends the sky into the horizon.");
    public final NumSetting stars = add(new NumSetting("Stars", 0.7, 0.0, 1.0, 0.05)).d("Sparse, irregular stars. Zero hides them.");
    public final ModeSetting quality = add(new ModeSetting("Sky Quality", "High", "Low", "Medium", "High")).d("More ribbon segments and star density at higher settings.");
    public final ModeSetting time = add(new ModeSetting("Time", "Auto", "Auto", "Day", "Sunset", "Night", "Midnight")).d("Locks the time of day on your screen only.");
    public final ModeSetting weather = add(new ModeSetting("Weather", "Auto", "Auto", "Clear")).d("Hides rain and thunder on your screen only.");

    public Ambience() { super("Ambience", "Changes the ambience of the game", Category.WORLD, false); }
}
