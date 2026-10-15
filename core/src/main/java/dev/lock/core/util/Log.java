package dev.lock.core.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Small file logger with an in-memory ring buffer of recent errors, which the diagnostics
 * views (in-game and in the launcher) display. Never throws.
 */
public final class Log {
    public enum Level { DEBUG, INFO, WARN, ERROR }

    public record Entry(long time, Level level, String message) {}

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Deque<Entry> RECENT = new ArrayDeque<>();
    private static final int MAX_RECENT = 200;
    private static Path file;
    private static String prefix = "lock";
    private static int errorCount;

    private Log() {}

    public static synchronized void init(Path logFile, String name) {
        file = logFile;
        prefix = name;
        try {
            Files.createDirectories(logFile.getParent());
            if (Files.exists(logFile) && Files.size(logFile) > 2_000_000) {
                Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".old"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    public static void debug(String msg) { log(Level.DEBUG, msg, null); }
    public static void info(String msg) { log(Level.INFO, msg, null); }
    public static void warn(String msg) { log(Level.WARN, msg, null); }
    public static void error(String msg) { log(Level.ERROR, msg, null); }
    public static void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }
    public static void warn(String msg, Throwable t) { log(Level.WARN, msg, t); }

    public static synchronized void log(Level level, String msg, Throwable t) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [" + prefix + "/" + level + "] " + msg;
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            line += System.lineSeparator() + sw;
        }
        if (level != Level.DEBUG) {
            RECENT.addLast(new Entry(System.currentTimeMillis(), level, t == null ? msg : msg + ": " + t));
            while (RECENT.size() > MAX_RECENT) RECENT.removeFirst();
        }
        if (level == Level.ERROR) errorCount++;
        if (level.ordinal() >= Level.WARN.ordinal() || Boolean.getBoolean("lock.debug")) System.out.println(line);
        if (file != null) {
            try {
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
    }

    public static synchronized List<Entry> recent() { return new ArrayList<>(RECENT); }

    public static synchronized int errorCount() { return errorCount; }

    public static synchronized void clearRecent() { RECENT.clear(); errorCount = 0; }
}
