package dev.lock.client.core;

import dev.lock.client.render.Fmt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/** Read-only access to information the client already has. Nothing here asks the server for more. */
public final class GameInfo {
    private GameInfo() {}

    static Minecraft mc() { return Minecraft.getInstance(); }

    public static boolean inWorld() { return mc().player != null && mc().level != null; }

    public static String dimension() {
        if (mc().level == null) return "minecraft:overworld";
        return mc().level.dimension().identifier().toString();
    }

    public static String dimensionName() {
        return switch (dimension()) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> Fmt.prettyId(dimension());
        };
    }

    public static String biome() {
        LocalPlayer p = mc().player;
        if (p == null || mc().level == null) return "?";
        return mc().level.getBiome(p.blockPosition()).unwrapKey().map(k -> Fmt.prettyId(k.identifier().toString())).orElse("Unknown");
    }

    /** Latency published by the server in the player list, or -1 when not available. */
    public static int ping() {
        LocalPlayer p = mc().player;
        if (p == null || p.connection == null || mc().isLocalServer()) return -1;
        PlayerInfo info = p.connection.getPlayerInfo(p.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    public static String server(boolean address) {
        if (mc().isLocalServer()) return "Singleplayer";
        ServerData d = mc().getCurrentServer();
        if (d == null) return mc().player == null ? "Not connected" : "Unknown server";
        return address ? d.ip : d.name;
    }

    public static int onlinePlayers() {
        LocalPlayer p = mc().player;
        return p == null || p.connection == null ? 0 : p.connection.getOnlinePlayers().size();
    }

    /** Scope used to separate waypoints by world/server. */
    public static String scope() {
        if (mc().isLocalServer() && mc().getSingleplayerServer() != null)
            return "sp:" + mc().getSingleplayerServer().getWorldData().getLevelName();
        ServerData d = mc().getCurrentServer();
        return d != null ? "mp:" + d.ip.toLowerCase() : "unknown";
    }

    public static long dayTime() { return mc().level == null ? 0 : mc().level.getDayTime(); }

    public static String clock(boolean h24) {
        long t = (dayTime() + 6000) % 24000;
        int hours = (int) (t / 1000), minutes = (int) ((t % 1000) * 60 / 1000);
        if (h24) return String.format("%02d:%02d", hours, minutes);
        return String.format("%d:%02d %s", hours % 12 == 0 ? 12 : hours % 12, minutes, hours < 12 ? "AM" : "PM");
    }

    public static long day() { return dayTime() / 24000 + 1; }

    public static String weather() {
        if (mc().level == null) return "?";
        if (mc().level.isThundering()) return "Thunder";
        if (mc().level.isRaining()) return "Rain";
        return "Clear";
    }

    public static int blockLight() {
        LocalPlayer p = mc().player;
        if (p == null || mc().level == null) return 0;
        BlockPos pos = p.blockPosition();
        return mc().level.getBrightness(LightLayer.BLOCK, pos);
    }

    public static int skyLight() {
        LocalPlayer p = mc().player;
        if (p == null || mc().level == null) return 0;
        return mc().level.getBrightness(LightLayer.SKY, p.blockPosition());
    }

    public static String difficulty() {
        return mc().level == null ? "?" : Fmt.prettyId(mc().level.getDifficulty().getSerializedName());
    }

    public static String moonPhase() {
        String[] phases = {"Full", "Waning gibbous", "Last quarter", "Waning crescent", "New", "Waxing crescent", "First quarter", "Waxing gibbous"};
        return phases[(int) ((dayTime() / 24000L) % 8)];
    }
}
