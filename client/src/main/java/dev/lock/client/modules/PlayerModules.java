package dev.lock.client.modules;

import dev.lock.client.LockClient;
import dev.lock.client.module.TextHud;
import dev.lock.client.render.Fmt;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Locale;

public final class PlayerModules {
    private PlayerModules() {}

    public static final class HealthInfo extends TextHud {
        public HealthInfo(LockClient l) { super(l, "health_info"); refreshMs = 100; }

        @Override protected void lines(List<Line> out, boolean compact) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            String hp = String.format(Locale.ROOT, "%.1f/%.0f", p.getHealth(), p.getMaxHealth());
            if (p.getAbsorptionAmount() > 0) hp += String.format(Locale.ROOT, " +%.0f", p.getAbsorptionAmount());
            out.add(line(compact ? "❤" : "Health", hp, 0xFFF2575F));
            String food = Integer.toString(p.getFoodData().getFoodLevel());
            if (b("saturation")) food += String.format(Locale.ROOT, " (sat %.1f)", p.getFoodData().getSaturationLevel());
            out.add(line(compact ? "☕" : "Food", food, 0xFFF5B748));
            if (b("armor")) out.add(line("Armor", Integer.toString(p.getArmorValue())));
        }
    }

    public static final class GameplayStats extends TextHud {
        public GameplayStats(LockClient l) { super(l, "gameplay_stats"); refreshMs = 250; }

        @Override protected void lines(List<Line> out, boolean compact) {
            if (b("distance")) out.add(line("Distance", Fmt.distance(lock.stats.distance) + " m"));
            if (b("jumps")) out.add(line("Jumps", Integer.toString(lock.stats.jumps)));
            if (b("deaths")) out.add(line("Deaths", Integer.toString(lock.stats.deaths)));
        }
    }

    public static final class SessionStats extends TextHud {
        public SessionStats(LockClient l) { super(l, "session_stats"); refreshMs = 500; }

        @Override protected void lines(List<Line> out, boolean compact) {
            long now = System.currentTimeMillis();
            out.add(line("Session", Fmt.duration(now - lock.stats.sessionStart)));
            if (b("world")) out.add(line("This world", Fmt.duration(now - lock.stats.worldJoin)));
        }
    }
}
