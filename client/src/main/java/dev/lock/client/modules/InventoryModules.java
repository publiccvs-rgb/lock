package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.gui.HudEditorScreen;
import dev.lock.client.module.ClientModule;
import dev.lock.client.module.OptionModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InventoryModules {
    private InventoryModules() {}

    /** Appends extra lines to item tooltips (display only). */
    public static final class Tooltips extends ClientModule {
        public Tooltips(LockClient l) { super(l, "item_tooltips"); }

        @SuppressWarnings("unchecked")
        public void append(Object stackObj, Object linesObj) {
            if (!active() || !(stackObj instanceof ItemStack s) || !(linesObj instanceof List<?> raw) || s.isEmpty()) return;
            List<Component> extra = new ArrayList<>();
            if (b("durability") && s.isDamageableItem()) {
                int left = s.getMaxDamage() - s.getDamageValue();
                extra.add(Component.literal("Durability: " + left + " / " + s.getMaxDamage()).withStyle(ChatFormatting.GRAY));
            }
            if (b("food")) {
                FoodProperties food = s.get(DataComponents.FOOD);
                if (food != null) extra.add(Component.literal(String.format(Locale.ROOT, "Food: %d (saturation %.1f)", food.nutrition(), food.saturation())).withStyle(ChatFormatting.GRAY));
            }
            if (b("id")) extra.add(Component.literal(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            if (extra.isEmpty()) return;
            try {
                ((List<Component>) raw).addAll(extra);
            } catch (UnsupportedOperationException ignored) {
                // the game returned an immutable list (tooltip hidden): nothing to add to
            }
        }
    }

    public static final class ContainerVisuals extends OptionModule {
        private Integer prev;

        public ContainerVisuals(LockClient l) { super(l, "container_visuals"); }

        @Override protected void remember() { prev = mc.options.menuBackgroundBlurriness().get(); }
        @Override protected void apply() { if (mc.options.menuBackgroundBlurriness().get() != i("blur")) mc.options.menuBackgroundBlurriness().set(i("blur")); }
        @Override protected void restore() { if (prev != null) mc.options.menuBackgroundBlurriness().set(prev); }
    }

    public static final class QuickKeys extends ClientModule {
        public QuickKeys(LockClient l) { super(l, "quick_keys"); }

        @Override public void onBind(String id, boolean pressed) {
            if (!pressed || mc.player == null) return;
            switch (id) {
                case "inventoryKey" -> mc.setScreen(new InventoryScreen(mc.player));
                case "hudEditorKey" -> mc.setScreen(new HudEditorScreen(null));
                case "profileKey" -> {
                    List<String> names = new ArrayList<>(lock.config.config().profiles().keySet());
                    int i = names.indexOf(lock.config.profile().name());
                    lock.switchProfile(names.get((i + 1) % names.size()));
                }
                default -> { }
            }
        }
    }
}
