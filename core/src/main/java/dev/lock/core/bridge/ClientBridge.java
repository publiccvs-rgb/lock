package dev.lock.core.bridge;

import java.util.Map;

/**
 * Calls from the agent's injected hooks into the in-game client. Parameters typed as Object are
 * Minecraft objects; the client (compiled against Minecraft) casts them. Implementations must never
 * throw: the agent additionally guards every call.
 */
public interface ClientBridge {
    /** Start of every client tick (20 Hz). */
    void tick();

    /** End of the vanilla HUD pass. {@code graphics} is a GuiGraphics, {@code delta} a DeltaTracker. */
    void renderHud(Object graphics, Object delta);

    /** Whether a vanilla HUD element ({@link VanillaElement} ordinal) should be skipped this frame. */
    boolean cancelVanilla(int element);

    /** Final field of view (after vanilla effects). {@code world} is false for the held-item pass. */
    float modifyFov(float fov, boolean world);

    /** Accumulated mouse movement about to be turned into camera rotation. */
    double modifyTurn(double delta);

    /** Mouse button event: a MouseButtonInfo and the GLFW action. */
    void mouseButton(Object buttonInfo, int action);

    /** Keyboard event: a KeyEvent and the GLFW action (press/release/repeat). */
    void key(Object keyEvent, int action);

    /** Scroll event while no screen is open; return true to consume it. */
    boolean mouseScroll(double amount);

    /** Item tooltip lines just built for {@code stack}; the client may append lines. */
    void tooltip(Object stack, Object lines);

    /** Command from the launcher over IPC. Called on an IPC thread. */
    Map<String, Object> handleCommand(Map<String, Object> command);
}
