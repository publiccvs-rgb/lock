package dev.lock.client.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Event notifications: short-lived toasts plus a persistent feed. */
public final class NotificationManager {
    public enum Kind { MODULE, SYSTEM, WARNING, ERROR, INFO }

    public record Note(Kind kind, String title, String message, long time) {}

    private final Deque<Note> toasts = new ArrayDeque<>();
    private final Deque<Note> feed = new ArrayDeque<>();
    private ConfigManager config;

    public void bind(ConfigManager c) { config = c; }

    public void post(Kind kind, String title, String message) {
        if (config != null) {
            var s = config.state("notifications");
            boolean allowed = switch (kind) {
                case MODULE -> s.bool("moduleToggles");
                case SYSTEM -> s.bool("system");
                case WARNING, ERROR -> s.bool("warnings");
                case INFO -> true;
            };
            if (!allowed) return;
        }
        Note n = new Note(kind, title, message, System.currentTimeMillis());
        toasts.addLast(n);
        while (toasts.size() > 16) toasts.removeFirst();
        feed.addLast(n);
        while (feed.size() > 50) feed.removeFirst();
    }

    public void module(String name, boolean on) { post(Kind.MODULE, name, on ? "Enabled" : "Disabled"); }
    public void system(String title, String msg) { post(Kind.SYSTEM, title, msg); }
    public void warn(String title, String msg) { post(Kind.WARNING, title, msg); }
    public void error(String title, String msg) { post(Kind.ERROR, title, msg); }
    public void info(String title, String msg) { post(Kind.INFO, title, msg); }

    /** Active toasts (oldest first), expiring ones removed. */
    public List<Note> activeToasts(long durationMs) {
        long now = System.currentTimeMillis();
        while (!toasts.isEmpty() && now - toasts.peekFirst().time() > durationMs) toasts.removeFirst();
        return new ArrayList<>(toasts);
    }

    public List<Note> feed() { return new ArrayList<>(feed); }
}
