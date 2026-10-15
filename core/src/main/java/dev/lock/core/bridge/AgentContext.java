package dev.lock.core.bridge;

import java.util.List;
import java.util.Map;

/** What the agent tells the in-game client about the integration it set up. */
public interface AgentContext {
    String lockVersion();

    String minecraftVersion();

    /** One entry per hook: name, whether it was applied, and detail. */
    List<HookStatus> hooks();

    /** Startup diagnostics collected before the client was created (mapping source, timings...). */
    Map<String, Object> startupInfo();

    record HookStatus(String name, boolean applied, String detail) {}
}
