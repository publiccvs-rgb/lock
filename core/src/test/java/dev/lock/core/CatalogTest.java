package dev.lock.core;

import dev.lock.core.module.Anchor;
import dev.lock.core.module.Catalog;
import dev.lock.core.module.Category;
import dev.lock.core.module.ModuleDef;
import dev.lock.core.module.Setting;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    @Test
    void everyCategoryHasModules() {
        for (Category c : Category.values()) assertFalse(Catalog.byCategory(c).isEmpty(), c.label);
    }

    @Test
    void everyModuleIsWellFormed() {
        for (ModuleDef d : Catalog.all().values()) {
            assertFalse(d.name.isBlank(), d.id);
            assertFalse(d.description.isBlank(), d.id + " needs a description");
            if (d.toggleable()) assertNotNull(d.setting(ModuleDef.KEYBIND), d.id + " needs a toggle key");
            if (d.hud) {
                for (String k : new String[]{ModuleDef.H_ANCHOR, ModuleDef.H_X, ModuleDef.H_Y, ModuleDef.H_SCALE, ModuleDef.H_COLOR,
                        ModuleDef.H_OPACITY, ModuleDef.H_BG, ModuleDef.H_BORDER, ModuleDef.H_PADDING, ModuleDef.H_COMPACT, ModuleDef.H_LOCAL})
                    assertNotNull(d.setting(k), d.id + " missing HUD property " + k);
            }
            for (Setting<?> s : d.settings().values()) assertEquals(s.defaultValue, s.validate(s.toJsonUnchecked(s.defaultValue)), d.id + "." + s.id + " default must round-trip");
        }
    }

    @Test
    void requiredModulesPresent() {
        for (String id : Set.of("crosshair", "fps", "cps", "keystrokes", "coordinates", "compass", "ping", "armor_hud", "potion_effects",
                "toggle_sprint", "toggle_sneak", "zoom", "perspective", "waypoints", "visibility", "click_gui", "hud_editor", "vanilla_hud"))
            assertNotNull(Catalog.get(id), id);
    }

    @Test
    void visibilityModesCoverAllGroups() {
        ModuleDef v = Catalog.get("visibility");
        for (String mode : Catalog.MODES) {
            if (mode.equals("Clean")) continue;
            for (String g : Catalog.GROUPS) assertNotNull(v.setting(Catalog.modeKey(mode, g)), mode + "/" + g);
        }
        for (ModuleDef d : Catalog.all().values()) if (d.hud) assertTrue(Catalog.GROUPS.contains(d.visibilityGroup), d.id);
    }

    @Test
    void anchorsResolveAndInvert() {
        for (Anchor a : Anchor.values()) {
            int x = a.resolveX(640, 50, 7), y = a.resolveY(360, 20, 9);
            assertEquals(7, a.offsetX(640, 50, x));
            assertEquals(9, a.offsetY(360, 20, y));
        }
        assertEquals(Anchor.BOTTOM_RIGHT, Anchor.nearest(640, 360, 600, 340));
    }
}
