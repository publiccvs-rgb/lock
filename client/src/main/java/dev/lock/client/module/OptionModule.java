package dev.lock.client.module;

import dev.lock.client.LockClient;

/**
 * Module that controls vanilla options. While enabled it keeps the options at the configured values
 * (re-applied each tick, so profile switches and launcher edits take effect); when disabled it
 * restores the values that were in place before it was enabled.
 */
public abstract class OptionModule extends ClientModule {
    protected OptionModule(LockClient lock, String id) { super(lock, id); }

    /** Captures the current vanilla values. */
    protected abstract void remember();

    /** Applies configured values if they differ from the current ones. */
    protected abstract void apply();

    /** Puts back what {@link #remember} captured. */
    protected abstract void restore();

    @Override protected void onEnable() { remember(); apply(); }

    @Override protected void onDisable() { restore(); }

    @Override public void tick() { apply(); }
}
