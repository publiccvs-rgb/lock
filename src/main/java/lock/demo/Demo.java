package lock.demo;

import lock.LockClient;
import lock.gui.LockScreen;
import lock.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Dev-only scripted walkthrough used to capture preview screenshots (-Dlock.demo=true). */
public final class Demo {
    public static final boolean ON = Boolean.getBoolean("lock.demo");
    private static boolean created;
    private static int idle, t = -1;

    private Demo() {}

    private static void cmd(Minecraft mc, String c) {
        var s = mc.getSingleplayerServer();
        if (s != null) s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), c));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            if (!created && mc.screen instanceof TitleScreen && ++idle > 60) {
                created = true;
                mc.createWorldOpenFlows().createFreshLevel("lockdemo",
                    new LevelSettings("lockdemo", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT),
                    WorldOptions.defaultWithRandomSeed(), WorldPresets::createFlatWorldDimensions, mc.screen);
            }
            return;
        }
        if (mc.player == null) return;
        t++;
        ModuleManager m = LockClient.modules;

        switch (t) {
            case 40 -> {
                m.general.hostiles.set(true);
                m.general.passives.set(true);
                m.tracers.playersOnly.set(false);
                m.charms.setEnabled(false);
                cmd(mc, "gamerule doMobSpawning false");
                cmd(mc, "time set noon");
                cmd(mc, "weather clear");
                cmd(mc, "tp @p 0 -60 0 0 6");
                // mobs in the open
                cmd(mc, "summon zombie 3 -60 7 {NoAI:1b,Silent:1b,Health:20f}");
                cmd(mc, "summon skeleton -4 -60 10 {NoAI:1b,Silent:1b,Health:7f}");
                cmd(mc, "summon creeper 8 -60 14 {NoAI:1b,Silent:1b}");
                cmd(mc, "summon villager -9 -60 18 {NoAI:1b,Silent:1b}");
                cmd(mc, "summon zombie 14 -60 24 {NoAI:1b,Silent:1b,Health:4f}");
                // stone wall with things hidden behind it
                cmd(mc, "fill -12 -60 5 -2 -58 5 stone_bricks");
                cmd(mc, "setblock -11 -60 7 chest");
                cmd(mc, "setblock -9 -60 7 trapped_chest");
                cmd(mc, "setblock -7 -60 7 ender_chest");
                cmd(mc, "setblock -5 -60 7 barrel");
                cmd(mc, "setblock -3 -60 7 magenta_shulker_box");
                cmd(mc, "summon pig -7 -60 10 {NoAI:1b,Silent:1b}");
            }
            case 160 -> shot(mc, "01_default");
            case 170 -> { m.charms.setEnabled(true); m.charms.playersOnly.set(false); }
            case 240 -> shot(mc, "02_charms");
            case 245 -> { m.charms.setEnabled(false); m.outlines.setEnabled(true); m.outlines.playersOnly.set(false); m.boxes.setEnabled(false); }
            case 320 -> shot(mc, "03_outlines");
            case 325 -> {
                m.outlines.setEnabled(false);
                m.boxes.setEnabled(true);
                m.tracers.setEnabled(false);
                m.health.number.set(true);
                m.health.name.set(true);
                cmd(mc, "tp @p 3 -60 2.5 0 4");
            }
            case 400 -> shot(mc, "04_health_closeup");
            case 405 -> {
                m.tracers.setEnabled(true);
                m.health.number.set(false);
                m.health.name.set(false);
                cmd(mc, "tp @p -7 -60 0 0 14");
            }
            case 480 -> shot(mc, "05_storage");
            case 485 -> {
                cmd(mc, "tp @p 0 -60 0 0 6");
                var scr = new LockScreen();
                mc.setScreen(scr);
                scr.mouseClicked(20, 48, 1);
            }
            case 560 -> shot(mc, "06_menu");
            case 580 -> mc.stop();
            default -> {}
        }
    }
}
