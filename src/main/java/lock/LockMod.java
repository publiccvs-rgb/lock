package lock;

import net.fabricmc.api.ClientModInitializer;

public class LockMod implements ClientModInitializer {
    @Override public void onInitializeClient() { Core.init(); }
}
