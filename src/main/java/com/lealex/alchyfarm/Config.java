package com.lealex.alchyfarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.Nullable;

/**
 * Game settings, saved in config/alchyfarm-common.toml. Read through the getters: they fall back to the defaults
 * while the file isn't loaded yet.
 */
public final class Config {
    private Config() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Lost souls.").push("souls");
    }

    public static final ModConfigSpec.IntValue SOUL_ARRIVAL_SECONDS = BUILDER
            .comment("How long a finished workstation waits before a lost soul comes to work it.",
                    "0: at once, so its arrival shows that the structure is right.")
            .defineInRange("arrivalSeconds", 0, 0, 3600);

    static {
        BUILDER.pop();
        BUILDER.comment("The crop plot.").push("cropPlot");
    }

    public static final ModConfigSpec.IntValue MAX_FIELD_SIZE = BUILDER
            .comment("The largest field a fence may close in, in blocks along each side (9 = up to 9 x 9).")
            .defineInRange("maxFieldSize", 9, 1, 9);

    public static final ModConfigSpec.IntValue CROP_GLIDE_TICKS = BUILDER
            .comment("How long a soul takes to cross one block, in ticks (20 = 1 second). For every workstation.")
            .defineInRange("glideTicksPerBlock", 10, 1, 200);

    public static final ModConfigSpec.IntValue CROP_WORK_TICKS = BUILDER
            .comment("How long a soul's shortest work lasts (sowing a square, filling a trough), in ticks.",
                    "Harvesting, taking an animal's drops and milking take about twice as long. For every workstation.")
            .defineInRange("workTicks", 44, 10, 1200);

    public static final ModConfigSpec.IntValue CROPS_PER_BONE_MEAL = BUILDER
            .comment("How many growing crops one bone meal covers when the soul casts it over the field",
                    "(each covered crop grows as if bone meal had been used on it by hand). 1: one piece for each crop.")
            .defineInRange("cropsPerBoneMeal", 1, 1, 81);

    static {
        BUILDER.pop();
        BUILDER.comment("What grows on what, for crop plots, orchards and ponds.").push("ground");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GROUNDS = BUILDER
            .comment("The ground each plant takes. One entry: \"what is planted=ground, ground, ...\".",
                    "What is planted is an item id, or # and an item tag. A ground is a block id, or # and a block tag.",
                    "The first entry that names a plant decides for it, so put single items before the tags they are in.",
                    "A plant no entry names goes wherever the game lets it grow.",
                    "The game's own rules still apply on top (a cactus needs free sides, sugar cane needs water beside it):",
                    "an entry can forbid a ground, it cannot make a plant grow where the game refuses it.",
                    "For a mod's farmland, add it to the seeds' entry: \"#alchyfarm:plot_seeds=minecraft:farmland, somemod:rich_soil\".")
            .defineListAllowEmpty("grounds", List.of(
                    "minecraft:cactus=#minecraft:sand",
                    "minecraft:sugar_cane=#minecraft:dirt, #minecraft:sand, minecraft:farmland",
                    "minecraft:bamboo=#minecraft:dirt, #minecraft:sand, minecraft:gravel",
                    "#alchyfarm:orchard_saplings=#minecraft:dirt",
                    "#alchyfarm:plot_seeds=minecraft:farmland"), () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The ranch.").push("ranch");
    }

    public static final ModConfigSpec.IntValue MAX_PEN_SIZE = BUILDER
            .comment("The largest pen a fence may close in, in blocks along each side (9 = up to 9 x 9).")
            .defineInRange("maxPenSize", 9, 1, 9);

    public static final ModConfigSpec.IntValue MAX_RANCH_ANIMALS = BUILDER
            .comment("How many animals one ranch takes in and keeps as a ghost herd (they cost the server nothing).",
                    "Animals beyond that stay ordinary animals in the pen.")
            .defineInRange("maxAnimals", 32, 1, 64);

    public static final ModConfigSpec.IntValue SECONDS_PER_DROP = BUILDER
            .comment("How long an animal that has given its drops waits before it gives again.")
            .defineInRange("secondsPerDrop", 120, 5, 7200);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANCH_DROPS = BUILDER
            .comment("What a ranch animal gives each time, for the kinds listed here. A kind that is not listed gives what it",
                    "drops when killed (its loot table), whatever mod it comes from. An entry here always wins.",
                    "One entry for each kind: \"entity id=item, item*count, item*least-most, item%chance, ...\".",
                    "The count is 1 if left out. %chance is how many times in a hundred the item is given at all.",
                    "Nothing after the = sign: that kind gives nothing.",
                    "The default entries: sheep give plain white wool whatever their colour, rabbits can give their foot,",
                    "pandas also give slime balls, and the animals that drop nothing when killed give a little something",
                    "(all but the bee). No animal gives back the very thing it is fed. Cows, pigs, chickens and the rest are",
                    "not listed: they give their usual drops.")
            .defineListAllowEmpty("drops", List.of(
                    "minecraft:sheep=minecraft:white_wool, minecraft:mutton*1-2",
                    "minecraft:rabbit=minecraft:rabbit_hide*0-1, minecraft:rabbit, minecraft:rabbit_foot%10",
                    "minecraft:panda=minecraft:bamboo, minecraft:slime_ball*0-1",
                    "minecraft:goat=minecraft:leather*0-1",
                    "minecraft:camel=minecraft:leather*0-2",
                    "minecraft:sniffer=minecraft:torchflower_seeds*0-2, minecraft:pitcher_pod*0-2",
                    "minecraft:armadillo=minecraft:armadillo_scute*0-1",
                    "minecraft:fox=minecraft:rabbit_hide*0-1, minecraft:feather*0-1",
                    "minecraft:frog=minecraft:ochre_froglight%25",
                    "minecraft:axolotl=minecraft:bucket, minecraft:prismarine_shard*0-2",
                    "minecraft:wolf=minecraft:bone*0-1",
                    "minecraft:ocelot=minecraft:feather*0-2",
                    "minecraft:happy_ghast=minecraft:ghast_tear%10",
                    "minecraft:turtle=minecraft:turtle_scute*0-1"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.IntValue FEED_PER_DROP = BUILDER
            .comment("How many pieces of its feed an animal eats from the trough each time it gives its drops.")
            .defineInRange("feedPerDrop", 1, 0, 64);

    public static final ModConfigSpec.IntValue SECONDS_PER_MILK = BUILDER
            .comment("How long an animal that has been milked waits before it can be milked again.")
            .defineInRange("secondsPerMilk", 120, 5, 7200);

    public static final ModConfigSpec.IntValue FEED_PER_BREEDING = BUILDER
            .comment("How many pieces of their feed a pair costs to breed from the ranch's screen.",
                    "4 is twice what breeding two animals by hand takes.")
            .defineInRange("feedPerBreeding", 4, 0, 64);

    public static final ModConfigSpec.IntValue BREED_REST_SECONDS = BUILDER
            .comment("How long two animals that have bred wait before they breed again (vanilla: 300).")
            .defineInRange("breedRestSeconds", 300, 0, 7200);

    public static final ModConfigSpec.IntValue YOUNG_GROW_SECONDS = BUILDER
            .comment("How long a young one born in a ranch takes to grow, and so to give drops and milk (vanilla: 1200).")
            .defineInRange("youngGrowSeconds", 1200, 0, 7200);

    public static final ModConfigSpec.IntValue FREED_SECONDS = BUILDER
            .comment("How long an animal freed from a ranch is left alone by every ranch, in case it was freed inside a pen",
                    "(time to lead it out). After that, any ranch whose pen it stands in takes it in again.")
            .defineInRange("freedSeconds", 30, 0, 7200);

    static {
        BUILDER.pop();
        BUILDER.comment("The pond.").push("pond");
    }

    public static final ModConfigSpec.IntValue POND_CATCH_SECONDS = BUILDER
            .comment("How long the fisherman waits for one catch, from the cast to the fish.")
            .defineInRange("catchSeconds", 20, 2, 3600);

    public static final ModConfigSpec.IntValue POND_MIN_WATER = BUILDER
            .comment("How many squares of water the fence must close in before the fisherman fishes.")
            .defineInRange("minWater", 4, 1, 81);

    public static final ModConfigSpec.ConfigValue<String> POND_LOOT_TABLE = BUILDER
            .comment("The loot table one catch is rolled from. The default is the game's own fishing table, without its",
                    "treasure part (that part asks for a float in open water, and there is none here).")
            .define("lootTable", "minecraft:gameplay/fishing");

    static {
        BUILDER.pop();
        BUILDER.comment("The mushroom hollow.").push("mushroomHollow");
    }

    public static final ModConfigSpec.IntValue MUSHROOM_GROW_SECONDS = BUILDER
            .comment("How long the hollow takes to grow one mushroom, by itself.",
                    "(Bone meal in its stock grows one on every free square of its bed at once, one piece each.)")
            .defineInRange("secondsPerMushroom", 20, 1, 7200);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HOLLOW_HARVEST = BUILDER
            .comment("What the hollow's bogged gets from one mushroom it picks, for the kinds listed here.",
                    "A kind that is not listed gives itself, one piece. Same writing as ranch.drops, with the mushroom's",
                    "block id first: \"minecraft:red_mushroom=minecraft:red_mushroom*1-2\".")
            .defineListAllowEmpty("harvest", List.of(), () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.push("cropPlot");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CROP_HARVEST = BUILDER
            .comment("What a crop plot's farmhand gets from one grown crop, for the crops listed here. A crop that is not",
                    "listed gives what it drops when broken (its loot table), whatever mod it comes from.",
                    "Same writing as ranch.drops, with the crop's BLOCK id first (for a pumpkin or a melon, the fruit's):",
                    "\"minecraft:wheat=minecraft:wheat*1-2, minecraft:wheat_seeds*0-3\".",
                    "A trellis is \"alchyfarm:trellis\" (not listed: 1 or 2 glow berries for each frame in berries).")
            .defineListAllowEmpty("harvest", List.of(), () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The orchard.").push("orchard");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORCHARD_HARVEST = BUILDER
            .comment("What an orchard's woodsman gets from one block of what it fells, cuts or picks, for the blocks listed",
                    "here: a log, a leaves block, a block of cactus or bamboo, a ripe berry bush. A block that is not listed",
                    "gives what it drops when broken. Same writing as ranch.drops, with the BLOCK id first:",
                    "\"minecraft:oak_leaves=minecraft:oak_sapling%8, minecraft:apple%2\". The pond's sugar cane reads this list too.")
            .defineListAllowEmpty("harvest", List.of(), () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.push("pond");
    }

    public static final ModConfigSpec.IntValue POND_POTION_PERCENT = BUILDER
            .comment("By magic (a violet stamp on the angler's post), the chance in a hundred at each cast, for EACH violet tier",
                    "(1 to 6), that the water also gives one of pond.riftPotions. 0: never.")
            .defineInRange("riftPotionPercent", 2, 0, 16);

    public static final ModConfigSpec.IntValue POND_SPOTS_AT_TOP = BUILDER
            .comment("At the top (stamps.sureLootRiftLevel), one cast fishes every square of the pond's water at once: up to",
                    "this many catches a cast.")
            .defineInRange("spotsAtTop", 16, 1, 81);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> POND_RIFT_POTIONS = BUILDER
            .comment("Potions no one can brew, fished by magic: \"Name=effect:level:seconds, effect:level:seconds, ...\".",
                    "Any effect of any mod, at any level (level 7 = VII).")
            .defineListAllowEmpty("riftPotions", List.of(
                    "Potion of the Gale=minecraft:speed:7:180, minecraft:jump_boost:4:180",
                    "Berserker Stance=minecraft:strength:10:90, minecraft:absorption:4:90, minecraft:resistance:4:90, minecraft:slowness:2:90",
                    "Deep Lungs=minecraft:water_breathing:1:1800, minecraft:dolphins_grace:3:600, minecraft:night_vision:1:1800",
                    "Miner's Fever=minecraft:haste:5:300, minecraft:luck:3:300, minecraft:night_vision:1:300",
                    "Stone Skin=minecraft:resistance:5:60, minecraft:slowness:4:60",
                    "Feather Step=minecraft:jump_boost:8:180, minecraft:slow_falling:1:240",
                    "Second Wind=minecraft:regeneration:4:30, minecraft:saturation:1:10"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> POND_CATCHES = BUILDER
            .comment("What the pond's fisherman can catch. Empty: one roll of pond.lootTable. Otherwise ONE of these is caught",
                    "each time, picked by its weight: \"item%weight\" or \"item*count%weight\" or \"item*least-most%weight\".",
                    "For example: [\"minecraft:cod%60\", \"minecraft:salmon%25\", \"minecraft:pufferfish%10\", \"minecraft:nautilus_shell%5\"].")
            .defineListAllowEmpty("catches", List.of(), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> POND_BAITS = BUILDER
            .comment("What counts as bait in a pond's stock, and what each does: \"item=wait,treasure\".",
                    "wait: how long a catch takes with it, in hundredths of pond.catchSeconds (80 = a fifth sooner).",
                    "treasure: how many casts in a hundred bring up something from the game's fishing treasure.",
                    "One bait is used at each cast, the quickest in the stock first. With any bait there is no junk:",
                    "the catch is fish, or treasure. (With pond.catches set, a bait only shortens the wait.)")
            .defineListAllowEmpty("baits", List.of("alchyfarm:bait=80,0", "alchyfarm:fine_bait=60,6", "alchyfarm:rift_bait=40,18"),
                    () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The mine.").push("mine");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MINE_VEINS = BUILDER
            .comment("What a mine's miner can bring up: \"block=weight,lowest,highest\". Each swing picks one entry by weight",
                    "among those whose heights (lowest to highest) hold the height of the mine's post. The block is then",
                    "\"broken\" with the pickaxe from the stock and gives what it drops to that pickaxe (Fortune and Silk",
                    "Touch count). A block the pickaxe could not harvest by hand is passed over for the first entry it can.",
                    "Put plain rock first. Any mod's ore can be listed.")
            .defineListAllowEmpty("veins", List.of(
                    "minecraft:stone=560,0,320", "minecraft:deepslate=560,-64,-1",
                    "minecraft:andesite=40,0,128", "minecraft:diorite=40,0,128", "minecraft:granite=40,0,128", "minecraft:tuff=70,-64,-1",
                    "minecraft:coal_ore=70,0,256",
                    "minecraft:copper_ore=45,0,112", "minecraft:deepslate_copper_ore=20,-16,-1",
                    "minecraft:iron_ore=45,0,72", "minecraft:deepslate_iron_ore=45,-64,-1",
                    "minecraft:gold_ore=10,0,32", "minecraft:deepslate_gold_ore=16,-64,-1",
                    "minecraft:lapis_ore=8,0,64", "minecraft:deepslate_lapis_ore=9,-64,-1",
                    "minecraft:redstone_ore=10,0,15", "minecraft:deepslate_redstone_ore=28,-64,-1",
                    "minecraft:diamond_ore=3,0,15", "minecraft:deepslate_diamond_ore=7,-64,-1",
                    "minecraft:emerald_ore=2,32,320"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> NETHER_MINE_VEINS = BUILDER
            .comment("The same for a nether mine, whose heights are the Nether's (0 to 127) wherever it is built.",
                    "An entry whose block does not exist (a mod that is not there) is left out with a line in the log,",
                    "so one list can serve a whole pack.")
            .defineListAllowEmpty("netherVeins", List.of(
                    "minecraft:netherrack=560,0,127", "minecraft:basalt=60,0,127", "minecraft:blackstone=40,0,127",
                    "minecraft:magma_block=30,24,40",
                    "minecraft:nether_quartz_ore=90,10,117", "minecraft:nether_gold_ore=45,10,117",
                    "minecraft:glowstone=20,60,127", "minecraft:gilded_blackstone=6,0,127",
                    "minecraft:ancient_debris=3,8,22"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MINE_SPRINGS = BUILDER
            .comment("What a mine gives as a fluid, with a pump in the middle of its floor and a cask on its stock or its",
                    "basket: \"fluid=mB each second,lowest,highest\" (the heights are those the mine digs at). It never runs",
                    "dry. Any mod's fluid can be listed; one that does not exist is left out.")
            .defineListAllowEmpty("springs", List.of("minecraft:lava=10,-64,-50"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> NETHER_MINE_SPRINGS = BUILDER
            .comment("The same for a nether mine (heights 0 to 127).")
            .defineListAllowEmpty("netherSprings", List.of("minecraft:lava=50,0,127"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.IntValue MINE_TORCH_BLOCKS = BUILDER
            .comment("How many blocks one torch from the stock lights the miner's work for (0: torches are not used).")
            .defineInRange("torchBlocks", 8, 0, 1024);

    public static final ModConfigSpec.IntValue MINE_TORCH_PERCENT = BUILDER
            .comment("How long a block takes to dig by torchlight, in hundredths of the usual (60 = four tenths sooner).")
            .defineInRange("torchPercent", 60, 10, 100);

    public static final ModConfigSpec.IntValue MINE_VEIN_SECONDS = BUILDER
            .comment("How often the rock gives something up: every so many seconds one of mine.veins is picked by weight and",
                    "comes to show on the mine's walls for the miner to dig (unless it is turned off on the mine's screen:",
                    "that turn is then lost). By torchlight it is mine.torchPercent hundredths of this.")
            .defineInRange("secondsPerVein", 4, 1, 3600);

    static {
        BUILDER.pop();
        BUILDER.comment("The stamps (upgrades). A post holds one stamp of each kind, of tier 1 to 4.").push("stamps");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> HASTE_TIERS = BUILDER
            .comment("Haste: how much quicker the soul works and walks at tiers 1, 2, 3 and 4, in hundredths (95 at most).",
                    "Veins, mushrooms, honey and blooms come that much sooner too. The violet tiers (3 and 4) are worth more.")
            .defineList("hasteTiers", List.of(15, 30, 55, 80), () -> 0, value -> value instanceof Integer number && number >= 0 && number <= 95);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> BOUNTY_TIERS = BUILDER
            .comment("Bounty: at tiers 1, 2, 3 and 4, the chance in a hundred that each thing brought to the basket comes with",
                    "one more. Above 100: one more for sure for each hundred, and the rest as a chance (250 = two more, and a",
                    "third half the time). Fluids poured into a cask gain the same share. Things that do not stack are never",
                    "doubled.")
            .defineList("bountyTiers", List.of(25, 50, 120, 200), () -> 0, value -> value instanceof Integer number && number >= 0 && number <= 1000);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> THRIFT_TIERS = BUILDER
            .comment("Thrift: at tiers 1, 2, 3 and 4, the chance in a hundred that something it uses is not used up: a seed",
                    "sown, a piece of bone meal, a bait, an animal's feed, a glass bottle, a torch, a pickaxe's wear.")
            .defineList("thriftTiers", List.of(20, 40, 65, 90), () -> 0, value -> value instanceof Integer number && number >= 0 && number <= 100);

    public static final ModConfigSpec.IntValue SURE_LOOT_LEVEL = BUILDER
            .comment("The number of violet tiers on a post (1 to 6; 6 = all three stamps at tier 4) from which a mine brings up",
                    "ONLY the blocks its screen keeps on, and a fisherman's cast never comes back with a catch its screen has",
                    "off. 0: never.")
            .defineInRange("sureLootRiftLevel", 6, 0, 6);

    public static final ModConfigSpec.IntValue RIFT_GROWTH_PERCENT = BUILDER
            .comment("With a violet stamp (tier 3 or 4) on its post a workstation works by magic, and what grows on its ground",
                    "grows quicker: each second, each square has this many chances in a hundred, for EACH violet tier on the",
                    "post (1 to 6), to get one of the game's own growth ticks. 0: no quicker growth.")
            .defineInRange("riftGrowthPercent", 2, 0, 16);

    public static final ModConfigSpec.IntValue RIFTED_PERCENT = BUILDER
            .comment("By magic (a violet stamp), the chance in a hundred, for EACH violet tier on the post (1 to 6), that what",
                    "the soul brings to the basket comes RIFTED: food that feeds twice as much (and gives the effect below),",
                    "three ores for one, an enchanted book with more enchantments, two of anything else. Not in the mushroom",
                    "hollow nor the flower garden. 0: never.")
            .defineInRange("riftedPercentByTier", 6, 0, 16);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RIFTED_EFFECTS = BUILDER
            .comment("What a rifted food gives when eaten, AT THE TOP (rift level 6: three tier 4 stamps):",
                    "\"item=effect,seconds,level; effect,seconds,level; ...\" (level 4 = IV). A workstation with fewer violet",
                    "tiers gives a share of each: its level x rift level / 6, rounded up, for three eighths (level 1) to all",
                    "of the time. A food that is not listed only feeds more. Any mod's food and effect can be named.")
            .defineListAllowEmpty("riftedFoodEffectsAtTop", List.of(
                    "minecraft:potato=minecraft:resistance,240,2", "minecraft:baked_potato=minecraft:resistance,480,3; minecraft:saturation,10,1",
                    "minecraft:carrot=minecraft:night_vision,600,1; minecraft:speed,180,2",
                    "minecraft:beetroot=minecraft:regeneration,30,3; minecraft:health_boost,300,2",
                    "minecraft:beetroot_soup=minecraft:regeneration,45,4; minecraft:health_boost,600,4",
                    "minecraft:wheat=minecraft:saturation,5,1", "minecraft:bread=minecraft:saturation,20,2; minecraft:haste,300,2",
                    "minecraft:melon_slice=minecraft:speed,240,3; minecraft:jump_boost,240,2",
                    "minecraft:pumpkin_pie=minecraft:strength,300,2; minecraft:resistance,300,2",
                    "minecraft:apple=minecraft:absorption,300,4; minecraft:regeneration,20,2",
                    "minecraft:sweet_berries=minecraft:haste,240,3; minecraft:speed,120,1",
                    "minecraft:glow_berries=minecraft:night_vision,900,1; minecraft:glowing,60,1; minecraft:luck,300,2",
                    "minecraft:honey_bottle=minecraft:regeneration,60,4; minecraft:absorption,600,6; minecraft:haste,600,3; minecraft:saturation,15,2",
                    "minecraft:cod=minecraft:water_breathing,600,1; minecraft:dolphins_grace,120,1",
                    "minecraft:cooked_cod=minecraft:water_breathing,1200,1; minecraft:dolphins_grace,300,2",
                    "minecraft:salmon=minecraft:dolphins_grace,240,2; minecraft:speed,120,2",
                    "minecraft:cooked_salmon=minecraft:dolphins_grace,480,3; minecraft:speed,300,2; minecraft:strength,300,2",
                    "minecraft:tropical_fish=minecraft:luck,600,4", "minecraft:pufferfish=minecraft:water_breathing,1800,1; minecraft:conduit_power,600,2",
                    "minecraft:beef=minecraft:strength,240,3", "minecraft:cooked_beef=minecraft:strength,600,4; minecraft:resistance,300,2",
                    "minecraft:porkchop=minecraft:strength,240,2; minecraft:health_boost,300,2",
                    "minecraft:cooked_porkchop=minecraft:strength,600,3; minecraft:health_boost,600,4",
                    "minecraft:mutton=minecraft:resistance,240,2", "minecraft:cooked_mutton=minecraft:resistance,600,3; minecraft:regeneration,30,2",
                    "minecraft:chicken=minecraft:slow_falling,240,1; minecraft:jump_boost,240,2",
                    "minecraft:cooked_chicken=minecraft:slow_falling,600,1; minecraft:jump_boost,600,4; minecraft:speed,300,2",
                    "minecraft:rabbit=minecraft:jump_boost,300,4", "minecraft:cooked_rabbit=minecraft:jump_boost,600,6; minecraft:speed,600,3",
                    "minecraft:mushroom_stew=minecraft:saturation,20,2; minecraft:regeneration,30,3",
                    "minecraft:golden_carrot=minecraft:night_vision,1800,1; minecraft:speed,900,4; minecraft:haste,900,4; minecraft:saturation,30,2",
                    "minecraft:glistering_melon_slice=minecraft:instant_health,1,6; minecraft:regeneration,60,5; minecraft:health_boost,1200,5",
                    "minecraft:golden_apple=minecraft:absorption,1200,10; minecraft:regeneration,120,5; minecraft:resistance,900,4; minecraft:strength,900,5; minecraft:fire_resistance,1800,1",
                    "minecraft:enchanted_golden_apple=minecraft:absorption,2400,20; minecraft:regeneration,300,8; minecraft:resistance,1800,5; minecraft:strength,1800,8; minecraft:fire_resistance,3600,1; minecraft:health_boost,3600,15; minecraft:speed,1800,5; minecraft:haste,1800,5; minecraft:luck,3600,5; minecraft:night_vision,3600,1"),
                    () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("Fluids.").push("fluids");
    }

    public static final ModConfigSpec.IntValue CASK_BUCKETS = BUILDER
            .comment("How many buckets a cask holds.")
            .defineInRange("caskBuckets", 16, 1, 1024);

    static {
        BUILDER.pop();
        BUILDER.comment("The apiary.").push("apiary");
    }

    public static final ModConfigSpec.IntValue APIARY_LEVEL_SECONDS = BUILDER
            .comment("How long ONE hive of an apiary takes to gain one level of honey (a hive is full at 5), with one flower",
                    "in the yard. With no flower there is no honey.")
            .defineInRange("secondsPerLevel", 40, 1, 36000);

    public static final ModConfigSpec.IntValue APIARY_FLOWER_PERCENT = BUILDER
            .comment("How much quicker each flower after the first makes the honey, in hundredths (20: five more flowers,",
                    "twice as quick).")
            .defineInRange("flowerPercent", 20, 0, 1000);

    public static final ModConfigSpec.IntValue APIARY_MAX_FLOWERS = BUILDER
            .comment("How many flowers of the yard count at most.")
            .defineInRange("maxFlowers", 11, 1, 81);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> APIARY_HONEY = BUILDER
            .comment("What a full hive gives the beekeeper when the stock holds a glass bottle (one bottle is used): a list of",
                    "\"item*least-most%chance\", as in ranch.drops. Any item can be named (another mod's honey).")
            .defineListAllowEmpty("honey", List.of("minecraft:honey_bottle"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> APIARY_COMB = BUILDER
            .comment("What a full hive gives when the stock holds no glass bottle.")
            .defineListAllowEmpty("comb", List.of("minecraft:honeycomb*3"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<String> APIARY_FLUID = BUILDER
            .comment("The fluid a full hive gives instead, when a cask is joined to the apiary: a fluid id, or # and a fluid",
                    "tag (the first fluid of the tag is used). AlchyFarm's own honey by default; name another mod's here to",
                    "get that one. Empty: never a fluid, the beekeeper keeps to bottles and comb.")
            .define("fluid", "alchyfarm:honey");

    public static final ModConfigSpec.IntValue APIARY_FLUID_AMOUNT = BUILDER
            .comment("How many mB of it one full hive gives.")
            .defineInRange("fluidAmount", 250, 1, 64000);

    static {
        BUILDER.pop();
        BUILDER.comment("The flower garden.").push("flowerGarden");
    }

    public static final ModConfigSpec.IntValue GARDEN_BLOOM_SECONDS = BUILDER
            .comment("How often ONE flower of a garden gives a bloom, in seconds: a garden of ten flowers gives ten times as",
                    "many. With bone meal in the stock (one piece a bloom) the next one comes in half the time.")
            .defineInRange("secondsPerBloom", 120, 2, 36000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GARDEN_HARVEST = BUILDER
            .comment("What one bloom gives, for the flowers listed here, with the flower's BLOCK id first (same writing as",
                    "ranch.drops): \"minecraft:poppy=minecraft:poppy*1-2\". A flower that is not listed gives itself, one,",
                    "or its dye when the garden's screen says so.")
            .defineListAllowEmpty("harvest", List.of(), () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The gilded garden.").push("gildedGarden");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GILDED_CROPS = BUILDER
            .comment("What a gilded garden's soul sows: \"seed=block\", the item it takes from the stock for a square and the",
                    "crop that then grows there (alchyfarm:gilded_carrots, alchyfarm:gilded_apple_tree, alchyfarm:enchanted_apple_tree, alchyfarm:gilded_melon_stem).",
                    "To make sowing cost more, name other items after the seed: they are taken from the stock with it each",
                    "time: \"minecraft:carrot, minecraft:gold_nugget*8=alchyfarm:gilded_carrots\".")
            .defineListAllowEmpty("seeds", List.of(
                    "minecraft:golden_carrot=alchyfarm:gilded_carrots",
                    "minecraft:golden_apple=alchyfarm:gilded_apple_tree",
                    "minecraft:enchanted_golden_apple=alchyfarm:enchanted_apple_tree",
                    "minecraft:glistering_melon_slice=alchyfarm:gilded_melon_stem"),
                    () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GILDED_HARVEST = BUILDER
            .comment("What the garden's soul gets from one grown crop, with the crop's BLOCK id first (same writing as",
                    "ranch.drops). A crop that is not listed gives what it drops when broken (the melon: \"alchyfarm:glistering_melon\",",
                    "3 to 7 glistering slices unless it is listed here).")
            .defineListAllowEmpty("harvest", List.of(
                    "alchyfarm:gilded_carrots=minecraft:golden_carrot*2",
                    "alchyfarm:gilded_apple_tree=minecraft:golden_apple*2",
                    "alchyfarm:enchanted_apple_tree=minecraft:enchanted_golden_apple*2"),
                    () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.push("souls");
    }

    public static final ModConfigSpec.IntValue SOUL_IDLE_SECONDS = BUILDER
            .comment("About how long a soul with nothing to do waits before it strolls off to look at a crop, a sapling or",
                    "an animal. 0: it never does. The pond's fisherman never does: it fishes.")
            .defineInRange("idleSeconds", 20, 0, 3600);

    public static final ModConfigSpec.IntValue OPEN_GROUND_RADIUS = BUILDER
            .comment("A fenced workstation (crop plot, ranch, orchard, pond, flower garden, apiary, gilded garden) whose post has",
                    "no closed border works the ground this many blocks round its post instead (2 = a 5 x 5 with the post in",
                    "its middle). A closed border always wins, and gives any shape. 0: a closed border is needed.")
            .defineInRange("openGroundRadius", 2, 0, 4);

    public static final ModConfigSpec.BooleanValue SOUL_IDLE_CARE = BUILDER
            .comment("True if the plant an idle soul goes to look at gets what one bone meal would do, for nothing.")
            .define("idleCare", true);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int soulArrivalTicks() {
        return read(SOUL_ARRIVAL_SECONDS, 0) * 20;
    }

    public static int openGroundRadius() {
        return read(OPEN_GROUND_RADIUS, 2);
    }

    public static int soulIdleSeconds() {
        return read(SOUL_IDLE_SECONDS, 20);
    }

    public static boolean soulIdleCare() {
        try {
            return SOUL_IDLE_CARE.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    public static int maxFieldSize() {
        return read(MAX_FIELD_SIZE, 9);
    }

    /** Every soul moves and works at the crop plot's pace (cropPlot.glideTicksPerBlock, cropPlot.workTicks). */
    public static int soulGlideTicks() {
        return read(CROP_GLIDE_TICKS, 10);
    }

    public static int soulWorkTicks() {
        return read(CROP_WORK_TICKS, 44);
    }

    public static int maxPenSize() {
        return read(MAX_PEN_SIZE, 9);
    }

    public static int maxRanchAnimals() {
        return read(MAX_RANCH_ANIMALS, 32);
    }

    public static int secondsPerDrop() {
        return read(SECONDS_PER_DROP, 120);
    }

    public static int feedPerDrop() {
        return read(FEED_PER_DROP, 1);
    }

    public static int secondsPerMilk() {
        return read(SECONDS_PER_MILK, 120);
    }

    public static int feedPerBreeding() {
        return read(FEED_PER_BREEDING, 4);
    }

    public static int breedRestSeconds() {
        return read(BREED_REST_SECONDS, 300);
    }

    public static int youngGrowSeconds() {
        return read(YOUNG_GROW_SECONDS, 1200);
    }

    public static int freedSeconds() {
        return read(FREED_SECONDS, 30);
    }

    public static int pondCatchSeconds() {
        return read(POND_CATCH_SECONDS, 20);
    }

    public static int pondMinWater() {
        return read(POND_MIN_WATER, 4);
    }

    public static String pondLootTable() {
        try {
            return POND_LOOT_TABLE.get();
        } catch (IllegalStateException notLoadedYet) {
            return "minecraft:gameplay/fishing";
        }
    }

    public static int mushroomGrowSeconds() {
        return read(MUSHROOM_GROW_SECONDS, 20);
    }

    public static int cropsPerBoneMeal() {
        return read(CROPS_PER_BONE_MEAL, 1);
    }

    /** One part of an entry, "item*least-most%chance": null (and a line in the log) if it is not understood. */
    private static @Nullable Drop parseDrop(String part, String name) {
        String[] chanced = part.trim().split("%", 2);
        String[] halves = chanced[0].trim().split("\\*", 2);
        Identifier itemId = Identifier.tryParse(halves[0].trim());
        Item item = itemId == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        int least = 1, most = 1, chance = 100;
        try {
            if (chanced.length > 1) chance = Integer.parseInt(chanced[1].trim());
            if (halves.length > 1) {
                String[] range = halves[1].trim().split("-", 2);
                least = Integer.parseInt(range[0].trim());
                most = range.length > 1 ? Integer.parseInt(range[1].trim()) : least;
            }
        } catch (NumberFormatException e) {
            item = Items.AIR;
        }
        if (item == Items.AIR || least < 0 || most < least || chance < 0 || chance > 100) {
            AlchyFarm.LOGGER.warn("AlchyFarm: {}: \"{}\" not understood, left out", name, part.trim());
            return null;
        }
        return new Drop(item, least, most, chance);
    }

    /** A list of "id=item, item*count, ..." entries as a map from the id to what it gives. */
    private static Map<Identifier, List<Drop>> parseYields(List<? extends String> entries, String name) {
        Map<Identifier, List<Drop>> read = new HashMap<>();
        for (String entry : entries) {
            int cut = entry.indexOf('=');
            Identifier id = cut < 0 ? null : Identifier.tryParse(entry.substring(0, cut).trim());
            if (id == null) {
                if (!entry.isBlank()) AlchyFarm.LOGGER.warn("AlchyFarm: {} entry not understood: {}", name, entry);
                continue;
            }
            List<Drop> drops = new ArrayList<>();
            for (String part : entry.substring(cut + 1).split(",")) {
                if (part.isBlank()) continue;
                Drop drop = parseDrop(part, name + " entry for " + id);
                if (drop != null) drops.add(drop);
            }
            read.put(id, List.copyOf(drops));
        }
        return read;
    }

    /** One of the harvest lists, read once per version of the list (the config hands out the same list until it is reloaded). */
    private static final class Yields {
        private final ModConfigSpec.ConfigValue<List<? extends String>> value;
        private final String name;
        private List<? extends String> read;
        private Map<Identifier, List<Drop>> byId = Map.of();

        Yields(ModConfigSpec.ConfigValue<List<? extends String>> value, String name) {
            this.value = value;
            this.name = name;
        }

        @Nullable List<Drop> of(Identifier id) {
            List<? extends String> entries;
            try {
                entries = value.get();
            } catch (IllegalStateException notLoadedYet) {
                return null;
            }
            if (entries != read) {
                byId = parseYields(entries, name);
                read = entries;
            }
            return byId.get(id);
        }
    }

    private static final Yields CROP_YIELDS = new Yields(CROP_HARVEST, "cropPlot.harvest"),
            ORCHARD_YIELDS = new Yields(ORCHARD_HARVEST, "orchard.harvest"), HOLLOW_YIELDS = new Yields(HOLLOW_HARVEST, "mushroomHollow.harvest");

    /** What the config says this block gives a crop plot's farmhand, or null if it says nothing (its loot table then). */
    public static @Nullable List<Drop> cropHarvest(Identifier block) {
        return CROP_YIELDS.of(block);
    }

    /** The same for an orchard (and the pond's banks). */
    public static @Nullable List<Drop> orchardHarvest(Identifier block) {
        return ORCHARD_YIELDS.of(block);
    }

    /** The same for the mushroom hollow (null: the mushroom itself). */
    public static @Nullable List<Drop> hollowHarvest(Identifier block) {
        return HOLLOW_YIELDS.of(block);
    }

    private static List<? extends String> catchesRead;
    private static List<Drop> catches = List.of();

    /** What the pond's fisherman catches, each with its weight as "chance". Empty: its loot table is rolled. */
    public static List<Drop> pondCatches() {
        List<? extends String> entries;
        try {
            entries = POND_CATCHES.get();
        } catch (IllegalStateException notLoadedYet) {
            return List.of();
        }
        if (entries != catchesRead) {
            List<Drop> read = new ArrayList<>();
            for (String entry : entries) {
                if (entry.isBlank()) continue;
                Drop drop = parseDrop(entry, "pond.catches");
                if (drop != null && drop.chance() > 0) read.add(drop);
            }
            catches = List.copyOf(read);
            catchesRead = entries;
        }
        return catches;
    }

    /** A plain list of drops, read once per version of the list. */
    private static final class Drops {
        private final ModConfigSpec.ConfigValue<List<? extends String>> value;
        private final String name;
        private List<? extends String> read;
        private List<Drop> drops = List.of();

        Drops(ModConfigSpec.ConfigValue<List<? extends String>> value, String name) {
            this.value = value;
            this.name = name;
        }

        List<Drop> get() {
            List<? extends String> entries;
            try {
                entries = value.get();
            } catch (IllegalStateException notLoadedYet) {
                return List.of();
            }
            if (entries != read) {
                List<Drop> found = new ArrayList<>();
                for (String entry : entries) {
                    if (entry.isBlank()) continue;
                    Drop drop = parseDrop(entry, name);
                    if (drop != null) found.add(drop);
                }
                drops = List.copyOf(found);
                read = entries;
            }
            return drops;
        }
    }

    /** One fluid a mine's pump brings up: how much each second, and the heights the mine must dig at. */
    public record Spring(Fluid fluid, int perSecond, int minY, int maxY) {}

    /** One of the spring lists, read once per version of the list. */
    private static final class Springs {
        private final ModConfigSpec.ConfigValue<List<? extends String>> value;
        private final String name;
        private List<? extends String> read;
        private List<Spring> springs = List.of();

        Springs(ModConfigSpec.ConfigValue<List<? extends String>> value, String name) {
            this.value = value;
            this.name = name;
        }

        List<Spring> get() {
            List<? extends String> entries;
            try {
                entries = value.get();
            } catch (IllegalStateException notLoadedYet) {
                return List.of();
            }
            if (entries != read) {
                List<Spring> found = new ArrayList<>();
                for (String entry : entries) {
                    if (entry.isBlank()) continue;
                    try {
                        int cut = entry.indexOf('=');
                        String[] numbers = entry.substring(cut + 1).split(",");
                        Identifier id = Identifier.tryParse(entry.substring(0, cut).trim());
                        Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
                        int amount = Integer.parseInt(numbers[0].trim());
                        int lowest = numbers.length > 1 ? Integer.parseInt(numbers[1].trim()) : -2048, highest = numbers.length > 2 ? Integer.parseInt(numbers[2].trim()) : 2048;
                        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0 || highest < lowest) throw new IllegalArgumentException();
                        found.add(new Spring(fluid, amount, lowest, highest));
                    } catch (RuntimeException e) {
                        AlchyFarm.LOGGER.warn("AlchyFarm: {} entry left out (not understood, or no such fluid): {}", name, entry);
                    }
                }
                springs = List.copyOf(found);
                read = entries;
            }
            return springs;
        }
    }

    private static final Springs MINE_SPRING_LIST = new Springs(MINE_SPRINGS, "mine.springs"), NETHER_SPRING_LIST = new Springs(NETHER_MINE_SPRINGS, "mine.netherSprings");

    public static List<Spring> mineSprings() {
        return MINE_SPRING_LIST.get();
    }

    public static List<Spring> netherMineSprings() {
        return NETHER_SPRING_LIST.get();
    }

    private static int tierOf(ModConfigSpec.ConfigValue<List<? extends Integer>> list, int tier, int[] otherwise) {
        if (tier <= 0) return 0;
        try {
            List<? extends Integer> values = list.get();
            if (!values.isEmpty()) return values.get(Math.min(tier, values.size()) - 1);
        } catch (IllegalStateException notLoadedYet) {
            // the defaults below
        }
        return otherwise[Math.min(tier, otherwise.length) - 1];
    }

    /** How much quicker a haste stamp of this tier makes the work, in hundredths. */
    public static int hasteOf(int tier) {
        return Math.min(95, tierOf(HASTE_TIERS, tier, new int[] {15, 30, 55, 80}));
    }

    /** A bounty stamp of this tier: the chance in a hundred of one more (above 100, so many more for sure). */
    public static int bountyOf(int tier) {
        return tierOf(BOUNTY_TIERS, tier, new int[] {25, 50, 120, 200});
    }

    /** A thrift stamp of this tier: the chance in a hundred that what is used is spared. */
    public static int thriftOf(int tier) {
        return Math.min(100, tierOf(THRIFT_TIERS, tier, new int[] {20, 40, 65, 90}));
    }

    public static int sureLootLevel() {
        return read(SURE_LOOT_LEVEL, 6);
    }

    public static int pondSpotsAtTop() {
        return read(POND_SPOTS_AT_TOP, 16);
    }

    public static int pondPotionPercent() {
        return read(POND_POTION_PERCENT, 2);
    }

    /** One potion the pond gives by magic: its name and its effects. */
    public record RiftPotion(String name, List<net.minecraft.world.effect.MobEffectInstance> effects) {}

    private static List<? extends String> riftPotionsRead;
    private static List<RiftPotion> riftPotions = List.of();

    public static List<RiftPotion> riftPotions() {
        List<? extends String> entries;
        try {
            entries = POND_RIFT_POTIONS.get();
        } catch (IllegalStateException notLoadedYet) {
            return List.of();
        }
        if (entries != riftPotionsRead) {
            List<RiftPotion> read = new ArrayList<>();
            for (String entry : entries) {
                if (entry.isBlank()) continue;
                try {
                    int cut = entry.indexOf('=');
                    List<net.minecraft.world.effect.MobEffectInstance> effects = new ArrayList<>();
                    for (String part : entry.substring(cut + 1).split(",")) {
                        String[] fields = part.trim().split(":");   // namespace : path : level : seconds
                        var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath(fields[0].trim(), fields[1].trim())).orElseThrow();
                        effects.add(new net.minecraft.world.effect.MobEffectInstance(effect, Math.max(1, Integer.parseInt(fields[3].trim())) * 20,
                                Math.max(0, Integer.parseInt(fields[2].trim()) - 1)));
                    }
                    if (effects.isEmpty() || cut <= 0) throw new IllegalArgumentException();
                    read.add(new RiftPotion(entry.substring(0, cut).trim(), List.copyOf(effects)));
                } catch (RuntimeException e) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: pond.riftPotions entry left out (not understood, or no such effect): {}", entry);
                }
            }
            riftPotions = List.copyOf(read);
            riftPotionsRead = entries;
        }
        return riftPotions;
    }

    public static int riftedPercent() {
        return read(RIFTED_PERCENT, 6);
    }

    /** An effect a rifted food gives: which, for how many seconds, at what level (1 = the plain effect). */
    public record RiftEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int seconds, int level) {}

    private static List<? extends String> riftEffectsRead;
    private static Map<Item, List<RiftEffect>> riftEffects = Map.of();

    /** The effects a rifted food of this kind gives (stamps.riftedFoodEffects). Empty if it is not listed. */
    public static List<RiftEffect> riftedEffects(Item food) {
        List<? extends String> entries;
        try {
            entries = RIFTED_EFFECTS.get();
        } catch (IllegalStateException notLoadedYet) {
            return List.of();
        }
        if (entries != riftEffectsRead) {
            Map<Item, List<RiftEffect>> read = new HashMap<>();
            for (String entry : entries) {
                if (entry.isBlank()) continue;
                try {
                    int cut = entry.indexOf('=');
                    Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(entry.substring(0, cut).trim())).orElseThrow();
                    List<RiftEffect> effects = new ArrayList<>(2);
                    for (String one : entry.substring(cut + 1).split(";")) {
                        if (one.isBlank()) continue;
                        String[] parts = one.split(",");
                        var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(parts[0].trim())).orElseThrow();
                        effects.add(new RiftEffect(effect, Math.max(1, Integer.parseInt(parts[1].trim())), parts.length > 2 ? Math.max(1, Integer.parseInt(parts[2].trim())) : 1));
                    }
                    read.put(item, List.copyOf(effects));
                } catch (RuntimeException e) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: stamps.riftedFoodEffectsAtTop entry left out (not understood, or no such item or effect): {}", entry);
                }
            }
            riftEffects = Map.copyOf(read);
            riftEffectsRead = entries;
        }
        return riftEffects.getOrDefault(food, List.of());
    }

    public static int riftGrowthPercent() {
        return read(RIFT_GROWTH_PERCENT, 2);
    }

    /** How much a cask holds, in mB. */
    public static int caskCapacity() {
        return read(CASK_BUCKETS, 16) * 1000;
    }

    private static String honeyRead;
    private static @Nullable Fluid honey;

    /** The fluid an apiary gives into a cask (apiary.fluid), or null if there is none in this game. */
    public static @Nullable Fluid apiaryFluid() {
        String named;
        try {
            named = APIARY_FLUID.get().trim();
        } catch (IllegalStateException notLoadedYet) {
            return null;
        }
        if (!named.equals(honeyRead)) {
            honeyRead = named;
            honey = null;
            Identifier id = Identifier.tryParse(named.startsWith("#") ? named.substring(1) : named);
            if (id != null && named.startsWith("#")) {
                for (var held : BuiltInRegistries.FLUID.getTagOrEmpty(TagKey.create(Registries.FLUID, id))) {
                    if (held.value().isSource(held.value().defaultFluidState())) {
                        honey = held.value();
                        break;
                    }
                }
                if (honey == null) honeyRead = null; // tags may not be there yet: asked again next time
            } else if (id != null) {
                honey = BuiltInRegistries.FLUID.getOptional(id).filter(fluid -> fluid != Fluids.EMPTY).orElse(null);
            }
        }
        return honey;
    }

    public static int apiaryFluidAmount() {
        return read(APIARY_FLUID_AMOUNT, 250);
    }

    private static final Drops HONEY_DROPS = new Drops(APIARY_HONEY, "apiary.honey"), COMB_DROPS = new Drops(APIARY_COMB, "apiary.comb");

    public static List<Drop> apiaryHoney() {
        return HONEY_DROPS.get();
    }

    public static List<Drop> apiaryComb() {
        return COMB_DROPS.get();
    }

    public static int apiaryLevelSeconds() {
        return read(APIARY_LEVEL_SECONDS, 40);
    }

    public static int apiaryFlowerPercent() {
        return read(APIARY_FLOWER_PERCENT, 20);
    }

    public static int apiaryMaxFlowers() {
        return read(APIARY_MAX_FLOWERS, 11);
    }

    private static final Yields GARDEN_YIELDS = new Yields(GARDEN_HARVEST, "flowerGarden.harvest");

    public static int gardenBloomSeconds() {
        return read(GARDEN_BLOOM_SECONDS, 120);
    }

    /** What the config says one bloom of this flower gives, or null (the flower, or its dye). */
    public static @Nullable List<Drop> gardenHarvest(Identifier block) {
        return GARDEN_YIELDS.of(block);
    }

    /** One crop of a gilded garden: its seed, what is taken from the stock with it (item and count, in "least"), the block. */
    public record Gilded(Item seed, List<Drop> cost, Block crop) {}

    private static List<? extends String> gildedRead;
    private static List<Gilded> gildedCrops = List.of();
    private static final Yields GILDED_YIELDS = new Yields(GILDED_HARVEST, "gildedGarden.harvest");

    public static List<Gilded> gildedCrops() {
        List<? extends String> entries;
        try {
            entries = GILDED_CROPS.get();
        } catch (IllegalStateException notLoadedYet) {
            return List.of();
        }
        if (entries != gildedRead) {
            List<Gilded> read = new ArrayList<>();
            for (String entry : entries) {
                if (entry.isBlank()) continue;
                int cut = entry.indexOf('=');
                Identifier blockId = cut < 0 ? null : Identifier.tryParse(entry.substring(cut + 1).trim());
                Block crop = blockId == null ? null : BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
                List<Drop> parts = new ArrayList<>();
                boolean understood = crop != null;
                for (String part : cut < 0 ? new String[0] : entry.substring(0, cut).split(",")) {
                    if (part.isBlank()) continue;
                    Drop read1 = parseDrop(part, "gildedGarden.seeds");
                    understood &= read1 != null && read1.least() > 0;
                    if (read1 != null) parts.add(read1);
                }
                if (understood && !parts.isEmpty()) read.add(new Gilded(parts.getFirst().item(), List.copyOf(parts.subList(1, parts.size())), crop));
                else AlchyFarm.LOGGER.warn("AlchyFarm: gildedGarden.seeds entry not understood: {}", entry);
            }
            gildedCrops = List.copyOf(read);
            gildedRead = entries;
        }
        return gildedCrops;
    }

    /** The gilded crop this seed is sown as, or null. */
    public static @Nullable Gilded gilded(Item seed) {
        for (Gilded crop : gildedCrops()) {
            if (crop.seed() == seed) return crop;
        }
        return null;
    }

    /** What the config says this grown block gives a gilded garden's soul, or null (its loot table then). */
    public static @Nullable List<Drop> gildedHarvest(Identifier block) {
        return GILDED_YIELDS.of(block);
    }

    /** One vein of a mine: a block, its weight, and the heights of the mine's post at which it is found. */
    public record Vein(Block block, int weight, int minY, int maxY) {}

    /** One of the vein lists, read once per version of the list. */
    private static final class Veins {
        private final ModConfigSpec.ConfigValue<List<? extends String>> value;
        private final String name;
        private List<? extends String> read;
        private List<Vein> veins = List.of();

        Veins(ModConfigSpec.ConfigValue<List<? extends String>> value, String name) {
            this.value = value;
            this.name = name;
        }

        List<Vein> get() {
            List<? extends String> entries;
            try {
                entries = value.get();
            } catch (IllegalStateException notLoadedYet) {
                return List.of();
            }
            if (entries != read) {
                List<Vein> found = new ArrayList<>();
                for (String entry : entries) {
                    if (entry.isBlank()) continue;
                    try {
                        int cut = entry.indexOf('=');
                        String[] numbers = entry.substring(cut + 1).split(",");
                        Identifier id = Identifier.tryParse(entry.substring(0, cut).trim());
                        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                        int weight = Integer.parseInt(numbers[0].trim());
                        int lowest = numbers.length > 1 ? Integer.parseInt(numbers[1].trim()) : -2048, highest = numbers.length > 2 ? Integer.parseInt(numbers[2].trim()) : 2048;
                        if (block == null || weight <= 0 || highest < lowest) throw new IllegalArgumentException();
                        found.add(new Vein(block, weight, lowest, highest));
                    } catch (RuntimeException e) {
                        AlchyFarm.LOGGER.warn("AlchyFarm: {} entry left out (not understood, or no such block): {}", name, entry);
                    }
                }
                veins = List.copyOf(found);
                read = entries;
            }
            return veins;
        }
    }

    private static final Veins MINE_VEIN_LIST = new Veins(MINE_VEINS, "mine.veins"), NETHER_VEIN_LIST = new Veins(NETHER_MINE_VEINS, "mine.netherVeins");

    public static List<Vein> mineVeins() {
        return MINE_VEIN_LIST.get();
    }

    public static List<Vein> netherMineVeins() {
        return NETHER_VEIN_LIST.get();
    }

    public static int mineVeinSeconds() {
        return read(MINE_VEIN_SECONDS, 4);
    }

    public static int mineTorchBlocks() {
        return read(MINE_TORCH_BLOCKS, 8);
    }

    public static int mineTorchPercent() {
        return read(MINE_TORCH_PERCENT, 60);
    }

    /** One kind of bait: how long a catch takes with it (hundredths of the usual), and its chance of treasure in a hundred. */
    public record Bait(Item item, int time, int treasure) {}

    private static List<? extends String> baitsRead;
    private static List<Bait> baits = List.of();

    public static List<Bait> pondBaits() {
        List<? extends String> entries;
        try {
            entries = POND_BAITS.get();
        } catch (IllegalStateException notLoadedYet) {
            return List.of();
        }
        if (entries != baitsRead) {
            List<Bait> read = new ArrayList<>();
            for (String entry : entries) {
                if (entry.isBlank()) continue;
                try {
                    int cut = entry.indexOf('=');
                    String[] numbers = entry.substring(cut + 1).split(",");
                    Identifier id = Identifier.tryParse(entry.substring(0, cut).trim());
                    Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
                    int wait = Integer.parseInt(numbers[0].trim()), treasure = numbers.length > 1 ? Integer.parseInt(numbers[1].trim()) : 0;
                    if (item == Items.AIR || wait < 5 || wait > 400 || treasure < 0 || treasure > 100) throw new IllegalArgumentException();
                    read.add(new Bait(item, wait, treasure));
                } catch (RuntimeException e) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: pond.baits entry not understood: {}", entry);
                }
            }
            baits = List.copyOf(read);
            baitsRead = entries;
        }
        return baits;
    }

    /** The bait this stack is, or null. */
    public static @Nullable Bait baitOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Bait bait : pondBaits()) {
            if (stack.is(bait.item())) return bait;
        }
        return null;
    }

    /** One roll of a configured harvest: each part by its chance, with a count between its least and most. */
    public static List<ItemStack> roll(List<Drop> drops, net.minecraft.util.RandomSource random) {
        List<ItemStack> given = new ArrayList<>(drops.size());
        for (Drop drop : drops) {
            if (drop.chance() < 100 && random.nextInt(100) >= drop.chance()) continue;
            int count = drop.least() + random.nextInt(drop.most() - drop.least() + 1);
            if (count > 0) given.add(new ItemStack(drop.item(), count));
        }
        return given;
    }

    /** One of these, picked by weight (its "chance"), with a count between its least and most. Empty if there are none. */
    public static ItemStack pick(List<Drop> weighted, net.minecraft.util.RandomSource random) {
        int total = 0;
        for (Drop drop : weighted) total += drop.chance();
        if (total <= 0) return ItemStack.EMPTY;
        int at = random.nextInt(total);
        for (Drop drop : weighted) {
            at -= drop.chance();
            if (at < 0) return new ItemStack(drop.item(), Math.max(1, drop.least() + random.nextInt(drop.most() - drop.least() + 1)));
        }
        return ItemStack.EMPTY;
    }

    /** One part of a ranch drops entry: an item, how many of it (least to most), and how often in a hundred. */
    public record Drop(Item item, int least, int most, int chance) {}

    // The drops entries, read once per version of the list (the config hands out the same list until it is reloaded)
    private static List<? extends String> dropsRead;
    private static Map<Identifier, List<Drop>> dropsByKind = Map.of();

    /** What the config says an animal of this kind gives, or null if it says nothing about it (its loot table then). */
    public static @Nullable List<Drop> ranchDrops(Identifier kind) {
        List<? extends String> entries;
        try {
            entries = RANCH_DROPS.get();
        } catch (IllegalStateException notLoadedYet) {
            return null;
        }
        if (entries != dropsRead) {
            dropsByKind = parseYields(entries, "ranch drops");
            dropsRead = entries;
        }
        return dropsByKind.get(kind);
    }

    /** One grounds entry: what it is for (an item or an item tag) and the grounds it allows (blocks and block tags). */
    private record Ground(@Nullable Item item, @Nullable TagKey<Item> items, List<Block> blocks, List<TagKey<Block>> tags) {
        boolean names(ItemStack plant) {
            return item != null ? plant.is(item) : items != null && plant.is(items);
        }

        boolean allows(BlockState ground) {
            for (Block block : blocks) {
                if (ground.is(block)) return true;
            }
            for (TagKey<Block> tag : tags) {
                if (ground.is(tag)) return true;
            }
            return false;
        }
    }

    // The grounds entries, read once per version of the list
    private static List<? extends String> groundsRead;
    private static List<Ground> grounds = List.of();

    /**
     * True if the config lets this plant go on this ground: the first entry naming the plant decides, and a plant no
     * entry names goes anywhere (the game's own rules are asked separately, by the caller).
     */
    public static boolean groundSuits(ItemStack plant, BlockState ground) {
        List<? extends String> entries;
        try {
            entries = GROUNDS.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
        if (entries != groundsRead) {
            List<Ground> read = new ArrayList<>();
            for (String entry : entries) {
                int cut = entry.indexOf('=');
                if (cut < 0) {
                    if (!entry.isBlank()) AlchyFarm.LOGGER.warn("AlchyFarm: grounds entry not understood: {}", entry);
                    continue;
                }
                String what = entry.substring(0, cut).trim();
                Identifier id = Identifier.tryParse(what.startsWith("#") ? what.substring(1) : what);
                if (id == null) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: grounds entry not understood: {}", entry);
                    continue;
                }
                List<Block> blocks = new ArrayList<>();
                List<TagKey<Block>> tags = new ArrayList<>();
                for (String part : entry.substring(cut + 1).split(",")) {
                    String name = part.trim();
                    if (name.isEmpty()) continue;
                    Identifier groundId = Identifier.tryParse(name.startsWith("#") ? name.substring(1) : name);
                    if (groundId == null) {
                        AlchyFarm.LOGGER.warn("AlchyFarm: grounds entry for {}: \"{}\" not understood, left out", what, name);
                    } else if (name.startsWith("#")) {
                        tags.add(TagKey.create(Registries.BLOCK, groundId));
                    } else {
                        BuiltInRegistries.BLOCK.getOptional(groundId).ifPresentOrElse(blocks::add,
                                () -> AlchyFarm.LOGGER.warn("AlchyFarm: grounds entry for {}: no block {}", what, name));
                    }
                }
                if (what.startsWith("#")) read.add(new Ground(null, TagKey.create(Registries.ITEM, id), blocks, tags));
                else read.add(new Ground(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR), null, blocks, tags));
            }
            grounds = read;
            groundsRead = entries;
        }
        for (Ground rule : grounds) {
            if (rule.names(plant)) return rule.allows(ground);
        }
        return true;
    }

    /**
     * The grounds the config gives this plant, as things to show (JEI): the items of the blocks and block tags of the
     * first entry that names it. Empty if no entry names it, or if none of its grounds has an item.
     */
    public static List<ItemStack> groundsShown(ItemStack plant) {
        groundSuits(plant, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); // reads the entries if they changed
        List<ItemStack> shown = new ArrayList<>();
        for (Ground rule : grounds) {
            if (!rule.names(plant)) continue;
            for (Block block : rule.blocks()) {
                if (block.asItem() != Items.AIR) shown.add(new ItemStack(block));
            }
            for (TagKey<Block> tag : rule.tags()) {
                BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(block -> {
                    if (block.value().asItem() != Items.AIR) shown.add(new ItemStack(block.value()));
                });
            }
            break;
        }
        return shown;
    }

    private static int read(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
