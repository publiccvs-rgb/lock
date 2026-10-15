package lock.inject;

/**
 * Static trampoline the injected bytecode calls into. Lives in the agent's own classloader (added to the
 * bootstrap/system classpath by the agent), so it is reachable from any class the transformer patches,
 * obfuscated or not.
 */
public final class Hooks {
    private Hooks() {}

    public static volatile long ticks;
    public static volatile long lastTickNanos;

    /** Called at the head of the hooked method on every invocation. Must never throw or block. */
    public static void onTick() {
        ticks++;
        lastTickNanos = System.nanoTime();
    }
}
