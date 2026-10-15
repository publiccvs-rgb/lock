package lock;

import com.mojang.blaze3d.platform.InputConstants;
import lock.gui.LockScreen;
import lock.module.ModuleManager;
import lock.render.HealthHud;
import lock.render.WorldEsp;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class LockClient implements ClientModInitializer {
    public static ModuleManager modules;
    private static KeyMapping openGui;

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        modules.load();

        openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.lock.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "key.categories.lock"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            modules.tick();
            if (lock.demo.Demo.ON) lock.demo.Demo.tick(mc);
            while (openGui.consumeClick()) mc.setScreen(new LockScreen());
        });
        WorldRenderEvents.LAST.register(WorldEsp::render);
        HudRenderCallback.EVENT.register(HealthHud::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> modules.save());
    }
}
