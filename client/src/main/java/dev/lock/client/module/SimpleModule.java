package dev.lock.client.module;

import dev.lock.client.LockClient;

/**
 * Module whose behaviour lives in a manager or UI (its settings are read there), e.g. Navigation
 * Preferences or Theme. Registering it binds the catalog entry so the compatibility check passes.
 */
public class SimpleModule extends ClientModule {
    public SimpleModule(LockClient lock, String id) { super(lock, id); }
}
