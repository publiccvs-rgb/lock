package lock.module;

import lock.setting.*;

public class NameTags extends Module {
    public final BoolSetting health = add(new BoolSetting("Health Bar", true)).d("Slim health bar under the name; shrinks with distance.");
    public final BoolSetting number = add(new BoolSetting("Health Number", false)).d("Shows the exact health next to the bar.");
    public final BoolSetting trail = add(new BoolSetting("Damage Trail", true)).d("A pale trail drains behind the bar after a hit.");
    public final BoolSetting armor = add(new BoolSetting("Armor Pips", true)).d("Tiny durability pips for each armor piece.");
    public final BoolSetting held = add(new BoolSetting("Held Item", true)).d("Name of the item in the main hand.");
    public final BoolSetting hideVanilla = add(new BoolSetting("Hide Vanilla Tag", true)).d("Replaces the default floating name.");
    public final ModeSetting color = add(new ModeSetting("Color", "Health", "Health", "Accent", "Team")).d("What the bar is tinted by.");
    public final NumSetting scale = add(new NumSetting("Scale", 1.0, 0.6, 1.5, 0.1)).d("Overall size of the tag.");

    public NameTags() { super("NameTags", "Clean tags with health above heads", Category.PLAYER, true); }
}
