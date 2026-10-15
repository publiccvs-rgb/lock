package dev.lock.client.core;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lock.client.LockClient;
import dev.lock.client.module.ClientModule;
import dev.lock.core.module.Keys;
import dev.lock.core.module.ModuleDef;
import dev.lock.core.module.Setting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dispatches Lock keybinds from the game's own key and mouse events and detects conflicts. */
public final class KeybindManager {
    public record Bind(ClientModule module, Setting.Key setting) {
        public String label() {
            return setting.id.equals(ModuleDef.KEYBIND) ? module.def.name : module.def.name + ": " + setting.name;
        }
    }

    private final LockClient lock;
    private final Minecraft mc = Minecraft.getInstance();
    private Map<Integer, List<Bind>> byKey = new HashMap<>();
    private long builtFor = -1;

    public KeybindManager(LockClient lock) { this.lock = lock; }

    private void rebuildIfNeeded() {
        if (builtFor == lock.config.version()) return;
        Map<Integer, List<Bind>> m = new HashMap<>();
        for (Bind b : all()) {
            int k = b.module().state().get(b.setting());
            if (k != Keys.NONE) m.computeIfAbsent(k, x -> new ArrayList<>()).add(b);
        }
        byKey = m;
        builtFor = lock.config.version();
    }

    /** Every keybind setting of every module, bound or not. */
    public List<Bind> all() {
        List<Bind> out = new ArrayList<>();
        for (ClientModule mod : lock.modules.all())
            for (Setting<?> s : mod.def.settings().values())
                if (s instanceof Setting.Key k) out.add(new Bind(mod, k));
        return out;
    }

    /** GLFW key event. Presses are ignored while a screen is open (typing in chat must not toggle modules); releases always pass so held binds never stick. */
    public void onInput(int key, boolean pressed) {
        rebuildIfNeeded();
        if (pressed && !allowedNow()) return;
        List<Bind> binds = byKey.get(key);
        if (binds == null) return;
        for (Bind b : List.copyOf(binds)) {
            if (b.setting().id.equals(ModuleDef.KEYBIND)) {
                if (pressed) lock.modules.toggle(b.module().def.id);
            } else {
                // Module-specific binds work even while the module's own toggle is off only for system modules.
                if (!b.module().def.toggleable() || b.module().active())
                    lock.modules.guard(b.module(), "keybind", () -> b.module().onBind(b.setting().id, pressed));
            }
        }
    }

    private boolean allowedNow() {
        if (mc.screen == null) return true;
        return lock.config.state("keybinds").bool("inScreens") && mc.screen instanceof AbstractContainerScreen<?>;
    }

    /** Key code -> descriptions of everything bound to it, for keys with more than one binding. */
    public Map<Integer, List<String>> conflicts() {
        Map<Integer, List<String>> users = new LinkedHashMap<>();
        for (Bind b : all()) {
            int k = b.module().state().get(b.setting());
            if (k == Keys.NONE) continue;
            users.computeIfAbsent(k, x -> new ArrayList<>()).add("Lock: " + b.label());
        }
        for (KeyMapping km : mc.options.keyMappings) {
            if (km.isUnbound()) continue;
            InputConstants.Key key = InputConstants.getKey(km.saveString());
            int code = key.getType() == InputConstants.Type.MOUSE ? Keys.MOUSE_BASE + key.getValue() : key.getValue();
            if (users.containsKey(code)) users.get(code).add("Minecraft: " + net.minecraft.network.chat.Component.translatable(km.getName()).getString());
        }
        users.values().removeIf(l -> l.size() < 2);
        return users;
    }
}
