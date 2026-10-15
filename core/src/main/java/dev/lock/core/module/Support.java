package dev.lock.core.module;

/** Honest compatibility status of a module against the current runtime integration. */
public enum Support {
    /** Implemented and exercised against Minecraft 1.21.11. */
    SUPPORTED("Supported"),
    /** Implemented, but some advertised behaviour depends on what the game/server provides. */
    PARTIAL("Partial"),
    /** Present in the catalog for completeness; it does nothing and says so. */
    UNSUPPORTED("Unsupported");

    public final String label;

    Support(String label) { this.label = label; }
}
