package lock.basefinder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Block classification for the finder. Every block state is reduced to one of a few dozen groups; the engine
 * only ever sees group ids. Groups encode what a block says about player presence, not what it looks like:
 * a torch is weak evidence (mineshafts and villages have them), a piston is strong (nothing generates one).
 */
public final class Blocks {
    private Blocks() {}

    // ---- group ids ----
    public static final int AIR = 0, WATER = 1, LAVA = 2, STONE = 3, SOIL = 4, ORE = 5, BEDROCK = 6, PLANT = 7,
        SNOW_ICE = 8, NATURAL_MISC = 9, NETHER_END = 10, OBSIDIAN = 11,
        MINESHAFT = 12, STRONGHOLD = 13, VILLAGE = 14, TORCH = 15, CONTAINER = 16, FURNACE = 17,
        MACHINE = 18, PLAYER_ONLY = 19, BUILDING = 20, LIGHT_BLOCK = 21, BED = 22, PORTAL = 23,
        AMETHYST = 24, CAVE_AIR = 25, OTHER = 26, GEODE_SHELL = 27, NATURAL_STRUCT = 28;
    public static final int COUNT = 29;

    public static final String[] NAME = {
        "air", "water", "lava", "stone", "soil", "ore", "bedrock", "plant", "snow/ice", "natural", "nether/end",
        "obsidian", "mineshaft", "stronghold", "village", "torch", "container", "furnace", "machine",
        "player-only", "building", "light", "bed", "portal", "amethyst", "cave air", "other", "geode shell", "natural structure"
    };

    // ---- per-group flags ----
    public static final int F_SOLID = 1;        // full opaque cube: blocks light completely
    public static final int F_PASSABLE = 2;     // counts as open space for void analysis
    public static final int F_NATURAL = 4;      // generates in ordinary terrain
    public static final int F_EMITS = 8;        // gives off light
    public static final int[] FLAGS = new int[COUNT];

    static {
        for (int g : new int[] {STONE, SOIL, ORE, BEDROCK, SNOW_ICE, OBSIDIAN, NETHER_END, GEODE_SHELL}) FLAGS[g] |= F_SOLID | F_NATURAL;
        FLAGS[BUILDING] |= F_SOLID;
        FLAGS[MINESHAFT] |= 0;
        FLAGS[STRONGHOLD] |= F_SOLID;
        FLAGS[NATURAL_STRUCT] |= F_SOLID | F_NATURAL;
        FLAGS[PLANT] |= F_NATURAL;
        FLAGS[NATURAL_MISC] |= F_NATURAL;
        FLAGS[AMETHYST] |= F_NATURAL;
        FLAGS[AIR] |= F_PASSABLE | F_NATURAL;
        FLAGS[CAVE_AIR] |= F_PASSABLE | F_NATURAL;
        FLAGS[TORCH] |= F_EMITS;
        FLAGS[LIGHT_BLOCK] |= F_EMITS;
        FLAGS[LAVA] |= F_EMITS | F_NATURAL;
        FLAGS[PORTAL] |= F_EMITS;
    }

    public static boolean solid(int g) { return (FLAGS[g] & F_SOLID) != 0; }
    public static boolean passable(int g) { return (FLAGS[g] & F_PASSABLE) != 0; }
    public static boolean natural(int g) { return (FLAGS[g] & F_NATURAL) != 0; }

    /** Natural stone-like terrain a player would have to carve through. */
    public static boolean carvable(int g) { return g == STONE || g == SOIL || g == ORE || g == NETHER_END || g == SNOW_ICE; }

    private static final Map<String, Integer> EXACT = new HashMap<>();

    private static void put(int g, String... names) { for (String n : names) EXACT.put(n, g); }

    static {
        put(AIR, "air", "void_air");
        put(CAVE_AIR, "cave_air");
        put(WATER, "water", "bubble_column");
        put(LAVA, "lava");
        put(BEDROCK, "bedrock");
        put(STONE, "stone", "deepslate", "granite", "diorite", "andesite", "tuff", "infested_stone",
            "infested_deepslate", "cobbled_deepslate");
        put(SOIL, "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium", "sand", "red_sand",
            "gravel", "clay", "mud", "dirt_path", "farmland", "suspicious_sand", "suspicious_gravel", "sandstone", "red_sandstone",
            "terracotta", "white_terracotta", "orange_terracotta", "yellow_terracotta", "brown_terracotta", "red_terracotta",
            "light_gray_terracotta", "mud_bricks_dummy");
        put(SNOW_ICE, "snow_block", "powder_snow", "ice", "packed_ice", "blue_ice", "snow", "frosted_ice");
        put(NETHER_END, "netherrack", "basalt", "blackstone", "soul_sand", "soul_soil", "magma_block", "end_stone",
            "crimson_nylium", "warped_nylium", "nether_wart_block", "warped_wart_block", "gilded_blackstone", "glowstone",
            "shroomlight", "crying_obsidian_dummy");
        put(OBSIDIAN, "obsidian");
        put(NATURAL_MISC, "dripstone_block", "pointed_dripstone", "moss_block", "moss_carpet", "azalea", "flowering_azalea",
            "big_dripleaf", "small_dripleaf", "spore_blossom", "glow_lichen", "hanging_roots", "cave_vines", "cave_vines_plant",
            "sculk", "sculk_vein", "sculk_catalyst", "sculk_shrieker", "sculk_sensor", "vine", "lily_pad", "kelp", "kelp_plant",
            "seagrass", "tall_seagrass", "sea_pickle", "coral_block", "cobweb_dummy");
        put(AMETHYST, "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud",
            "budding_amethyst");
        put(GEODE_SHELL, "calcite", "smooth_basalt", "amethyst_block");
        put(MINESHAFT, "oak_planks", "oak_fence", "rail", "cobweb", "oak_log_dummy");
        put(STRONGHOLD, "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
            "stone_brick_stairs", "stone_brick_slab", "infested_stone_bricks", "iron_bars", "end_portal_frame",
            "end_portal", "iron_door", "stone_button", "oak_door_dummy");
        put(VILLAGE, "hay_block", "composter", "lectern", "loom", "grindstone", "cartography_table", "fletching_table",
            "smithing_table", "stonecutter", "bell", "cauldron", "oak_door", "spruce_door", "birch_door", "jungle_door",
            "acacia_door", "dark_oak_door", "oak_trapdoor", "spruce_trapdoor", "oak_pressure_plate", "oak_sign", "oak_wall_sign",
            "spruce_sign", "spruce_wall_sign", "ladder", "white_carpet", "red_carpet", "black_carpet", "oak_stairs",
            "oak_slab", "spruce_stairs", "spruce_slab", "spruce_planks", "spruce_fence", "spruce_fence_gate", "oak_fence_gate",
            "glass_pane", "flower_pot", "melon", "pumpkin", "carved_pumpkin", "jack_o_lantern_dummy");
        put(TORCH, "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch");
        put(CONTAINER, "chest", "trapped_chest", "barrel", "decorated_pot", "chiseled_bookshelf");
        put(PLAYER_ONLY, "ender_chest", "shulker_box", "white_shulker_box",
            "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box",
            "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
            "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box", "black_shulker_box");
        put(NATURAL_STRUCT, "mossy_cobblestone", "mossy_cobblestone_slab", "mossy_cobblestone_stairs", "mossy_cobblestone_wall",
            "spawner", "trial_spawner", "vault", "tuff_bricks", "chiseled_tuff", "chiseled_tuff_bricks", "polished_tuff",
            "copper_grate", "deepslate_bricks", "deepslate_tiles", "polished_deepslate", "chiseled_deepslate",
            "cracked_deepslate_bricks", "cracked_deepslate_tiles", "reinforced_deepslate", "prismarine", "prismarine_bricks",
            "dark_prismarine", "sea_lantern", "purpur_block", "purpur_pillar", "end_stone_bricks", "nether_bricks",
            "red_nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks", "polished_blackstone_bricks",
            "cracked_polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone", "gilded_blackstone",
            "candle", "gray_carpet", "light_gray_carpet", "suspicious_dummy", "cut_sandstone", "chiseled_sandstone",
            "cut_red_sandstone", "chiseled_red_sandstone", "smooth_sandstone", "sandstone_stairs", "sandstone_slab", "sandstone_wall");
        put(FURNACE, "furnace", "blast_furnace", "smoker", "crafting_table");
        put(MACHINE, "piston", "sticky_piston", "piston_head", "moving_piston", "observer", "comparator", "repeater",
            "dropper", "dispenser", "hopper", "redstone_lamp", "tnt", "note_block", "target", "daylight_detector",
            "lightning_rod", "redstone_block", "lever", "tripwire_hook", "slime_block", "honey_block", "beacon", "anvil",
            "chipped_anvil", "damaged_anvil", "enchanting_table", "jukebox", "brewing_stand", "respawn_anchor",
            "crafter", "sculk_dummy", "redstone_wire", "detector_rail", "powered_rail", "activator_rail");
        put(PLAYER_ONLY, "iron_block", "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block",
            "coal_block", "copper_block", "waxed_copper_block", "iron_trapdoor", "end_rod", "conduit", "bookshelf_dummy");
        put(LIGHT_BLOCK, "lantern", "soul_lantern", "sea_lantern", "campfire", "soul_campfire", "end_rod_dummy",
            "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "jack_o_lantern", "copper_bulb");
        put(PORTAL, "nether_portal");
    }

    private static final Set<String> BUILDING_SUFFIX = new HashSet<>(java.util.List.of(
        "_planks", "_slab", "_stairs", "_wall", "_fence", "_fence_gate", "_bricks", "_brick", "_concrete", "_concrete_powder",
        "_glazed_terracotta", "_wool", "_carpet", "_glass", "_glass_pane", "_door", "_trapdoor", "_sign", "_hanging_sign",
        "_banner", "_bed", "_button", "_pressure_plate", "_log", "_wood", "_stained_glass_dummy", "_shulker_box_dummy"));

    /** Maps a registry path ("oak_planks", no namespace) to a group id. */
    public static int classify(String p) {
        Integer g = EXACT.get(p);
        if (g != null) return g;
        if (p.endsWith("_ore") || p.equals("ancient_debris") || p.startsWith("raw_") && p.endsWith("_block") && false) return ORE;
        if (p.equals("raw_iron_block") || p.equals("raw_copper_block") || p.equals("raw_gold_block")) return ORE;
        if (p.endsWith("_leaves") || p.endsWith("_sapling") || p.endsWith("_mushroom") || p.endsWith("_mushroom_block")
            || p.endsWith("_flower") || p.endsWith("_roots") || p.endsWith("_propagule") || p.equals("grass") || p.equals("short_grass")
            || p.equals("tall_grass") || p.equals("fern") || p.equals("large_fern") || p.equals("dead_bush") || p.equals("sugar_cane")
            || p.equals("cactus") || p.equals("bamboo") || p.equals("sweet_berry_bush") || p.endsWith("_tulip") || p.equals("dandelion")
            || p.equals("poppy") || p.equals("azure_bluet") || p.equals("oxeye_daisy") || p.equals("cornflower") || p.equals("lilac")
            || p.equals("rose_bush") || p.equals("peony") || p.equals("sunflower") || p.equals("brown_mushroom") || p.equals("red_mushroom")
            || p.equals("chorus_plant") || p.equals("chorus_flower") || p.equals("hay_dummy")) return PLANT;
        if (p.equals("oak_log") || p.equals("birch_log") || p.equals("spruce_log") || p.equals("jungle_log")
            || p.equals("acacia_log") || p.equals("dark_oak_log") || p.equals("cherry_log") || p.equals("mangrove_log")
            || p.equals("mangrove_roots") || p.equals("muddy_mangrove_roots")) return PLANT;
        if (p.endsWith("_concrete") || p.endsWith("_concrete_powder") || p.endsWith("_glazed_terracotta") || p.endsWith("_wool")
            || p.endsWith("_stained_glass") || p.endsWith("_stained_glass_pane") || p.endsWith("quartz_block") || p.equals("quartz_pillar")
            || p.startsWith("smooth_") || p.startsWith("polished_") || p.startsWith("cut_") || p.startsWith("chiseled_")
            || p.equals("bricks") || p.equals("glass") || p.equals("cobblestone")
            || p.endsWith("_planks") || p.endsWith("_stairs") || p.endsWith("_slab") || p.endsWith("_wall") || p.endsWith("_fence")
            || p.endsWith("_fence_gate") || p.endsWith("_door") || p.endsWith("_trapdoor") || p.endsWith("_bed") || p.endsWith("_banner")
            || p.endsWith("_button") || p.endsWith("_pressure_plate") || p.endsWith("_sign") || p.endsWith("_hanging_sign")
            || p.startsWith("stripped_") || p.endsWith("_bricks")
            || p.equals("bookshelf") || p.equals("iron_chain") || p.equals("chain")) {
            if (p.endsWith("_bed")) return BED;
            return BUILDING;
        }
        if (p.equals("water_cauldron") || p.equals("lava_cauldron")) return VILLAGE;
        return OTHER;
    }

    /** Strength of "a player was here" per block of this group, in nats of likelihood ratio. */
    public static double playerWeight(int g) {
        return switch (g) {
            case MACHINE -> 3.2;
            case PLAYER_ONLY -> 2.6;
            case CONTAINER -> 1.6;
            case FURNACE -> 1.1;
            case BED -> 1.2;
            case LIGHT_BLOCK -> 1.0;
            case BUILDING -> 0.7;
            case TORCH -> 0.5;
            case PORTAL -> 3.0;
            default -> 0.0;
        };
    }
}
