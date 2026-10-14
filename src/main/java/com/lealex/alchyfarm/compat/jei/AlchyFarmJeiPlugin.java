package com.lealex.alchyfarm.compat.jei;

import com.lealex.alchyfarm.upgrade.FarmStamps;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.block.TrellisBlock;
import com.lealex.alchyfarm.block.entity.CropPlotBlockEntity;
import com.lealex.alchyx.client.Lines;
import com.lealex.alchyfarm.block.entity.FlowerGardenBlockEntity;
import com.lealex.alchyfarm.block.entity.MushroomFarmBlockEntity;
import com.lealex.alchyfarm.block.entity.OrchardBlockEntity;
import com.lealex.alchyfarm.block.entity.PondBlockEntity;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * JEI integration (only loaded when JEI is installed: JEI finds this class by its @JeiPlugin annotation, nothing else
 * refers to it). Its pages:
 * <ul>
 *   <li>Orchard: each sapling an orchard plants and what its tree gives; each column plant and itself;</li>
 *   <li>Pond: what the fisherman catches, and what grows on the banks;</li>
 *   <li>Mushroom Farm: each mushroom and how many a ripe one gives;</li>
 *   <li>Workstation Layouts: one possible way to build each workstation, seen from above;</li>
 *   <li>Crop Plot: each seed a plot sows (item tag alchyfarm:plot_seeds) and what its grown crop gives;</li>
 *   <li>Ranch: each kind of animal, what it eats (its feed, also what breeding costs), what it gives each time, and
 *       what it gives for a bucket or a bowl.</li>
 * </ul>
 * What a crop or an animal gives comes from the config file's ranch.drops where it has an entry, else from loot
 * tables. Loot tables are server data: in single player they are rolled two hundred times here to see what can come
 * out, how many and how often (so the figures shown for them are measured, close but not exact). On a dedicated server the client can't do that, so a crop shows its own item and an animal without a config
 * entry shows no drops (the page says "its usual drops").
 */
@JeiPlugin
public class AlchyFarmJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "jei");
    private static final int ROLLS = 200, TEXT = 0xFF404040, FAINT = 0xFF6A6A6A;

    /**
     * One thing a crop or an animal can give: the item (shown with its largest count), how many (least to most), and
     * how often in a hundred it is given at all.
     */
    public record Given(ItemStack stack, int least, int most, int chance) {
        /** The short words under its slot: its chance when it is not sure, else its range when it varies, else nothing. */
        String label() {
            return chance < 100 ? chance + "%" : least != most ? least + "-" + most : "";
        }

        void describe(ITooltipBuilder tooltip) {
            tooltip.add(Component.literal(least == most ? "Amount: " + most : "Amount: " + least + " to " + most).withStyle(ChatFormatting.GRAY));
            if (chance < 100) tooltip.add(Component.literal("Chance: " + chance + "%").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Crop plot: a seed, and what the grown crop can give. */
    public record Crop(ItemStack seed, List<Given> harvest) {}

    /**
     * Ranch: one kind of animal.
     *
     * @param egg    its spawn egg, to picture it (empty if it has none)
     * @param feed   what it eats
     * @param drops  what it can give each time (empty with usual = true: not known here)
     * @param vessel bucket and / or bowl, with what each brings back in the same place of {@code milk}
     */
    public record Ranch(ItemStack egg, String name, List<ItemStack> feed, List<Given> drops, boolean usual,
                        List<ItemStack> vessel, List<ItemStack> milk) {}

    /**
     * Orchard, pond, mushroom hollow: what is planted (empty for fishing), what comes of it, and a line of words.
     * "ground" is a second thing it needs, shown beside it with groundNote in its tooltip: the grounds the config
     * lets a plant grow on (empty if it has no rule), or the hollow's bone meal.
     */
    public record Grown(ItemStack seed, List<ItemStack> ground, String groundNote, List<Given> results, String words) {
        Grown(ItemStack seed, List<Given> results, String words) {
            this(seed, seed.isEmpty() ? List.of() : Config.groundsShown(seed), "Grows on this ground", results, words);
        }
    }

    /** One square of a layout: what stands there, and a note for its tooltip. */
    public record Cell(ItemStack stack, String note) {}

    /** One possible way to build a workstation, seen from above: rows of squares (north first), and a line of words. */
    public record Layout(String name, Cell[][] rows, String words) {}

    public static final IRecipeType<Grown> GILDED_GARDEN = IRecipeType.create(AlchyFarm.MODID, "gilded_garden", Grown.class);
    public static final IRecipeType<Grown> NETHER_MINE = IRecipeType.create(AlchyFarm.MODID, "nether_mine", Grown.class);
    public static final IRecipeType<Grown> FLOWER_GARDEN = IRecipeType.create(AlchyFarm.MODID, "flower_garden", Grown.class);
    public static final IRecipeType<Grown> APIARY = IRecipeType.create(AlchyFarm.MODID, "apiary", Grown.class);
    public static final IRecipeType<Grown> MINE = IRecipeType.create(AlchyFarm.MODID, "mine", Grown.class);
    public static final IRecipeType<Layout> LAYOUT = IRecipeType.create(AlchyFarm.MODID, "layout", Layout.class);

    public static final IRecipeType<Grown> ORCHARD = IRecipeType.create(AlchyFarm.MODID, "orchard", Grown.class);
    public static final IRecipeType<Grown> POND = IRecipeType.create(AlchyFarm.MODID, "pond", Grown.class);
    public static final IRecipeType<Grown> MUSHROOM_FARM = IRecipeType.create(AlchyFarm.MODID, "mushroom_farm", Grown.class);
    public static final IRecipeType<Crop> CROP = IRecipeType.create(AlchyFarm.MODID, "crop_plot", Crop.class);
    public static final IRecipeType<Ranch> RANCH = IRecipeType.create(AlchyFarm.MODID, "ranch", Ranch.class);

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    // The stamp letter hangs out at the right of every post's screen: JEI keeps its list off it
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>>() {
                    @Override
                    public List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
                        return screen instanceof com.lealex.alchyfarm.client.StampTab.Host host ? List.of(host.stampArea()) : List.of();
                    }
                });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CropCategory(gui), new RanchCategory(gui),
                new GrownCategory(gui, ORCHARD, "jei.alchyfarm.orchard", ModRegistries.WOODSMAN_POST_ITEM.get(), 1),
                new GrownCategory(gui, POND, "jei.alchyfarm.pond", ModRegistries.ANGLER_POST_ITEM.get(), 4),
                new GrownCategory(gui, MUSHROOM_FARM, "jei.alchyfarm.mushroom_farm", ModRegistries.FORAGER_POST_ITEM.get(), 1),
                new GrownCategory(gui, MINE, "jei.alchyfarm.mine", ModRegistries.MINER_POST_ITEM.get(), 1),
                new GrownCategory(gui, GILDED_GARDEN, "jei.alchyfarm.gilded_garden", ModRegistries.GILDER_POST_ITEM.get(), 1),
                new GrownCategory(gui, NETHER_MINE, "jei.alchyfarm.nether_mine", ModRegistries.NETHER_MINER_POST_ITEM.get(), 1),
                new GrownCategory(gui, FLOWER_GARDEN, "jei.alchyfarm.flower_garden", ModRegistries.GARDENER_POST_ITEM.get(), 1),
                new GrownCategory(gui, APIARY, "jei.alchyfarm.apiary", ModRegistries.BEEKEEPER_POST_ITEM.get(), 1),
                new LayoutCategory(gui));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CROP, ModRegistries.CARVED_STAKE_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get(),
                ModRegistries.TRELLIS_ITEM.get());
        registration.addCraftingStation(RANCH, ModRegistries.HITCHING_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(),
                ModRegistries.BASKET_ITEM.get(), ModRegistries.TROUGH_ITEM.get());
        registration.addCraftingStation(ORCHARD, ModRegistries.WOODSMAN_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(POND, ModRegistries.ANGLER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(MINE, ModRegistries.MINER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(GILDED_GARDEN, ModRegistries.GILDER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(NETHER_MINE, ModRegistries.NETHER_MINER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(FLOWER_GARDEN, ModRegistries.GARDENER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(APIARY, ModRegistries.BEEKEEPER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
        registration.addCraftingStation(MUSHROOM_FARM, ModRegistries.FORAGER_POST_ITEM.get(), ModRegistries.STOCK_ITEM.get(), ModRegistries.BASKET_ITEM.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer(); // null on a dedicated server
        ServerLevel serverLevel = server == null ? null : server.overworld();
        Level level = minecraft.level;

        List<Crop> crops = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack seed = new ItemStack(item);
            if (!CropPlotBlockEntity.isSeed(seed)) continue;
            crops.add(new Crop(seed, harvestOf(((BlockItem) item).getBlock(), serverLevel)));
        }
        crops.add(new Crop(new ItemStack(Items.APPLE), harvestOf(ModRegistries.APPLE_TREE.get(), serverLevel))); // sown as the little apple tree
        registration.addRecipes(CROP, crops);

        List<Ranch> ranch = new ArrayList<>();
        if (level != null) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (type.getCategory() != MobCategory.CREATURE && type.getCategory() != MobCategory.AXOLOTLS) continue;
                try {
                    if (!(type.create(level, EntitySpawnReason.LOAD) instanceof Animal animal)) continue;
                    Ranch page = ranchOf(type, animal, serverLevel);
                    if (page != null) ranch.add(page);
                } catch (RuntimeException e) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: no JEI ranch page for {}: {}", EntityType.getKey(type), e.toString());
                }
            }
        }
        registration.addRecipes(RANCH, ranch);

        List<Grown> trees = new ArrayList<>();
        for (Holder<Item> planted : BuiltInRegistries.ITEM.getTagOrEmpty(OrchardBlockEntity.SAPLINGS_TAG)) {
            if (planted.value() instanceof BlockItem item) trees.add(treeOf(item, serverLevel));
        }
        registration.addRecipes(ORCHARD, trees);

        List<Grown> pond = new ArrayList<>();
        pond.add(new Grown(ItemStack.EMPTY, catchOf(serverLevel),
                "Every " + Config.pondCatchSeconds() + " s. Needs " + Config.pondMinWater() + " water"));
        if (Config.pondCatches().isEmpty()) { // one page for each bait: what a cast with it can bring up
            for (Config.Bait bait : Config.pondBaits()) {
                pond.add(new Grown(new ItemStack(bait.item()), baitedCatch(serverLevel, bait),
                        "One per cast, about " + Math.max(2, Config.pondCatchSeconds() * bait.time() / 100) + " s. No junk"));
            }
        }
        for (Holder<Item> planted : BuiltInRegistries.ITEM.getTagOrEmpty(PondBlockEntity.PLANTS_TAG)) {
            if (!(planted.value() instanceof BlockItem item)) continue;
            pond.add(new Grown(new ItemStack(item), List.of(new Given(new ItemStack(item), 1, 1, 100)), "On the bank, by the water"));
        }
        registration.addRecipes(POND, pond);

        List<Grown> mushrooms = new ArrayList<>();
        for (Holder<Item> planted : BuiltInRegistries.ITEM.getTagOrEmpty(MushroomFarmBlockEntity.SPAWN_TAG)) {
            if (!(planted.value() instanceof BlockItem item)) continue;
            boolean wild = item == Items.RED_MUSHROOM || item == Items.BROWN_MUSHROOM;
            // The beds it stands on, by the game's own rules for its kind (spore loam takes them all)
            List<ItemStack> beds = new ArrayList<>(4);
            beds.add(new ItemStack(ModRegistries.SPORE_LOAM_ITEM.get()));
            if (item.getBlock() instanceof NetherWartBlock) beds.add(new ItemStack(Items.SOUL_SAND));
            else if (item.getBlock() instanceof NetherFungusBlock) beds.addAll(List.of(new ItemStack(Items.CRIMSON_NYLIUM), new ItemStack(Items.WARPED_NYLIUM)));
            else beds.addAll(List.of(new ItemStack(Items.MYCELIUM), new ItemStack(Items.PODZOL)));
            String every = "One every " + Config.mushroomGrowSeconds() + " s. ";
            mushrooms.add(new Grown(new ItemStack(item), beds, "The bed under it: any one of these",
                    Config.hollowHarvest(BuiltInRegistries.BLOCK.getKey(item.getBlock())) instanceof List<Config.Drop> set
                            ? givenOf(set) : List.of(new Given(new ItemStack(item), 1, 1, 100)),
                    every + (wild ? "No sample needed" : "Keep one in the stock: it is not used up")));
        }
        registration.addRecipes(MUSHROOM_FARM, mushrooms);

        // The mine: one page for each vein, the block it stands for (with the weakest vanilla pickaxe that takes it)
        // and what it gives to an unenchanted pickaxe
        for (boolean nether : new boolean[] {false, true}) {
        List<Grown> veins = new ArrayList<>();
        for (Config.Vein vein : nether ? Config.netherMineVeins() : Config.mineVeins()) {
            ItemStack rock = new ItemStack(vein.block());
            if (rock.isEmpty()) continue;
            BlockState state = vein.block().defaultBlockState();
            List<ItemStack> picks = new ArrayList<>();
            for (Item pick : List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
                if (new ItemStack(pick).isCorrectToolForDrops(state)) picks.add(new ItemStack(pick));
            }
            List<Given> gives = List.of(new Given(rock.copy(), 1, 1, 100));
            if (serverLevel != null) {
                try {
                    Rolls rolls = new Rolls();
                    for (int roll = 0; roll < ROLLS; roll++) {
                        rolls.roll(Block.getDrops(state, serverLevel, BlockPos.ZERO, null, null, new ItemStack(Items.NETHERITE_PICKAXE)));
                    }
                    if (!rolls.isEmpty()) gives = rolls.given();
                } catch (RuntimeException e) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: can't read what {} gives for JEI: {}", BuiltInRegistries.BLOCK.getKey(vein.block()), e.toString());
                }
            }
            veins.add(new Grown(rock, picks, "A pickaxe in the stock that can break it (any mod's). One use a block.", gives,
                    "Dug at height " + vein.minY() + " to " + vein.maxY()));
        }
        // and what its pump brings up: a bucket of each spring, the cask beside it
        for (Config.Spring spring : nether ? Config.netherMineSprings() : Config.mineSprings()) {
            ItemStack bucket = new ItemStack(spring.fluid().getBucket());
            if (bucket.isEmpty()) continue;
            veins.add(new Grown(new ItemStack(ModRegistries.PUMP_ITEM.get()), List.of(new ItemStack(ModRegistries.CASK_ITEM.get())),
                    "A cask on top of the stock or the basket. The pump stands in the middle of the floor.",
                    List.of(new Given(bucket, 1, 1, 100)), spring.perSecond() + " mB/s, height " + spring.minY() + " to " + spring.maxY()));
        }
        registration.addRecipes(nether ? NETHER_MINE : MINE, veins);
        }

        // The flower garden: one page for each flower it plants (the bone meal beside it is not needed)
        List<Grown> blooms = new ArrayList<>();
        for (Holder<Item> planted : BuiltInRegistries.ITEM.getTagOrEmpty(FlowerGardenBlockEntity.FLOWERS_TAG)) {
            if (!(planted.value() instanceof BlockItem item)) continue;
            List<Config.Drop> set = Config.gardenHarvest(BuiltInRegistries.BLOCK.getKey(item.getBlock()));
            List<Given> gives = new ArrayList<>(2);
            if (set != null) {
                gives.addAll(givenOf(set));
            } else {
                gives.add(new Given(new ItemStack(item), 1, 1, 100));
                ItemStack dye = dyeOf(item, serverLevel); // what the garden gives instead when the kind is set to "Dye"
                if (!dye.isEmpty()) gives.add(new Given(dye, dye.getCount(), dye.getCount(), 100));
            }
            blooms.add(new Grown(new ItemStack(item), List.of(new ItemStack(Items.GRASS_BLOCK), new ItemStack(Items.DIRT)),
                    "Planted on grass or dirt, once. It stays.", gives,
                    "A bloom every " + Config.gardenBloomSeconds() + " s per flower" + (gives.size() > 1 ? ", as a flower or its dye" : "")));
        }
        registration.addRecipes(FLOWER_GARDEN, blooms);

        // The gilded garden: one page for each crop of the config (the seed, the gold beside it, what it gives)
        List<Grown> gilded = new ArrayList<>();
        for (Config.Gilded crop : Config.gildedCrops()) {
            List<ItemStack> cost = new ArrayList<>();
            for (Config.Drop part : crop.cost()) cost.add(new ItemStack(part.item(), Math.min(part.least(), part.item().getDefaultMaxStackSize())));
            boolean stem = crop.crop() == ModRegistries.GILDED_MELON_STEM.get();
            Block grown = stem ? ModRegistries.GLISTERING_MELON.get() : crop.crop(); // a stem's harvest is its fruit
            List<Config.Drop> set = Config.gildedHarvest(BuiltInRegistries.BLOCK.getKey(grown));
            List<Given> gives = set != null ? givenOf(set) : harvestOf(grown, serverLevel);
            if (stem && gives.size() == 1 && gives.getFirst().stack().isEmpty()) gives = List.of(new Given(new ItemStack(Items.GLISTERING_MELON_SLICE, 7), 3, 7, 100));
            gilded.add(new Grown(new ItemStack(crop.seed()), cost, "Taken from the stock with it, each time it is sown",
                    gives, stem ? "The stem stays. Keep a bare square beside it" : "Sown by the soul only, on farmland"));
        }
        registration.addRecipes(GILDED_GARDEN, gilded);

        // The apiary: a full hive, emptied into a bottle or cut out as comb
        List<ItemStack> hiveBlocks = List.of(new ItemStack(Items.BEEHIVE), new ItemStack(Items.BEE_NEST));
        registration.addRecipes(APIARY, List.of(
                new Grown(new ItemStack(Items.GLASS_BOTTLE), hiveBlocks, "A full hive in the yard", givenOf(Config.apiaryHoney()), "One glass bottle per hive"),
                new Grown(ItemStack.EMPTY, hiveBlocks, "A full hive in the yard", givenOf(Config.apiaryComb()), "With no bottle in the stock")));
        if (Config.apiaryFluid() != null && !new ItemStack(Config.apiaryFluid().getBucket()).isEmpty()) {
            registration.addRecipes(APIARY, List.of(new Grown(ItemStack.EMPTY, List.of(new ItemStack(ModRegistries.CASK_ITEM.get())),
                    "A cask joined to the yard: no bottle is used", List.of(new Given(new ItemStack(Config.apiaryFluid().getBucket()), 1, 1, 100)),
                    Config.apiaryFluidAmount() + " mB per hive, into the cask")));
        }

        Layout yard = layout("jei.alchyfarm.apiary", ModRegistries.BEEKEEPER_POST_ITEM.get(), false,
                new Cell(new ItemStack(Items.POPPY), "Flowers, any: the more, the quicker the honey (up to " + Config.apiaryMaxFlowers() + " count). No flower, no honey."),
                new Cell(new ItemStack(Items.GRASS_BLOCK), "Room to walk: the beekeeper stands beside a hive to empty it"), "Hives and flowers inside");
        yard.rows()[3][3] = new Cell(new ItemStack(Items.BEEHIVE), "A beehive or a bee nest, anywhere inside the fence. As many as you like. No bee is needed.");

        Cell tilled = new Cell(new ItemStack(Items.DIRT), "The field: till it (farmland)");
        Cell pen = new Cell(new ItemStack(Items.GRASS_BLOCK), "The pen: any ground. Lead the animals in.");
        Cell grove = new Cell(new ItemStack(Items.DIRT), "The grove: dirt or grass (sand for cactus)");
        Cell bank = new Cell(new ItemStack(Items.DIRT), "The bank: dirt or sand, for sugar cane");
        Cell water = new Cell(new ItemStack(Items.WATER_BUCKET), "Water, dug one block deep: its top is level with the bank");
        infoPages(registration);
        registration.addRecipes(LAYOUT, List.of(
                layout("jei.alchyfarm.crop_plot", ModRegistries.CARVED_STAKE_ITEM.get(), false, tilled, null, "Inside: tilled soil"),
                layout("jei.alchyfarm.ranch", ModRegistries.HITCHING_POST_ITEM.get(), true, pen, null, "Inside: the pen, any ground"),
                layout("jei.alchyfarm.orchard", ModRegistries.WOODSMAN_POST_ITEM.get(), false, grove, null, "Inside: dirt (sand for cactus)"),
                layout("jei.alchyfarm.pond", ModRegistries.ANGLER_POST_ITEM.get(), false, bank, water, "Water in the middle, 1 deep"),
                layout("jei.alchyfarm.gilded_garden", ModRegistries.GILDER_POST_ITEM.get(), false, tilled, null, "Inside: tilled soil"),
                layout("jei.alchyfarm.flower_garden", ModRegistries.GARDENER_POST_ITEM.get(), false,
                        new Cell(new ItemStack(Items.GRASS_BLOCK), "The garden: grass or dirt, whatever the flowers take"), null, "Inside: grass or dirt"),
                yard, hollow(), shaft(), netherShaft()));

    }

    // ---- Information pages: short paragraphs, read from the lang file (jei.alchyfarm.info.<name>.1, .2...) ----

    private static void infoPages(IRecipeRegistration registration) {
        info(registration, ModRegistries.CARVED_STAKE_ITEM.get(), "carved_stake", "fenced", "stores");
        info(registration, ModRegistries.HITCHING_POST_ITEM.get(), "hitching_post", "fenced", "stores");
        info(registration, ModRegistries.WOODSMAN_POST_ITEM.get(), "woodsman_post", "fenced", "stores");
        info(registration, ModRegistries.ANGLER_POST_ITEM.get(), "angler_post", "fenced", "stores");
        info(registration, ModRegistries.GARDENER_POST_ITEM.get(), "gardener_post", "fenced", "stores");
        info(registration, ModRegistries.BEEKEEPER_POST_ITEM.get(), "beekeeper_post", "fenced", "stores");
        info(registration, ModRegistries.GILDER_POST_ITEM.get(), "gilder_post", "fenced", "stores");
        info(registration, ModRegistries.FORAGER_POST_ITEM.get(), "forager_post", "fixed");
        info(registration, ModRegistries.MINER_POST_ITEM.get(), "miner_post", "fixed");
        info(registration, ModRegistries.NETHER_MINER_POST_ITEM.get(), "nether_miner_post", "fixed");
        info(registration, ModRegistries.STOCK_ITEM.get(), "stock");
        info(registration, ModRegistries.BASKET_ITEM.get(), "basket");
        info(registration, ModRegistries.TROUGH_ITEM.get(), "trough");
        info(registration, ModRegistries.TRELLIS_ITEM.get(), "trellis");
        info(registration, ModRegistries.SPORE_LOAM_ITEM.get(), "spore_loam");
        info(registration, ModRegistries.CASK_ITEM.get(), "cask");
        info(registration, ModRegistries.PUMP_ITEM.get(), "pump");
        info(registration, ModRegistries.SURVEYOR_CORD.get(), "surveyor_cord");
        for (var bait : List.of(ModRegistries.BAIT, ModRegistries.FINE_BAIT, ModRegistries.RIFT_BAIT)) info(registration, bait.get(), "bait");
        for (var material : List.of(ModRegistries.CARVED_SPLINTER, ModRegistries.ESTATE_TIMBER, ModRegistries.IRON_FITTING, ModRegistries.LEDGER_PAGE,
                ModRegistries.CRIMSON_WAX, ModRegistries.CRIMSON_SEAL, ModRegistries.VIOLET_WAX, ModRegistries.VIOLET_SEAL)) {
            info(registration, material.get(), "material");
        }
        // Each stamp: what its kind does, with the config's own numbers, then what every stamp shares
        for (var stamp : ModRegistries.STAMPS) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(stampWords(stamp.get().type())));
            lines.addAll(paragraphs("stamps"));
            registration.addIngredientInfo(stamp.get(), lines.toArray(Component[]::new));
        }
    }

    private static String stampWords(com.lealex.alchyx.upgrade.UpgradeType type) {
        if (type == FarmStamps.HASTE) return "Faster work: "
                + Config.hasteOf(1) + " / " + Config.hasteOf(2) + " / " + Config.hasteOf(3) + " / " + Config.hasteOf(4) + "%.";
        if (type == FarmStamps.BOUNTY) return "More output: +" + Config.bountyOf(1) + " / " + Config.bountyOf(2)
                + " / " + Config.bountyOf(3) + " / " + Config.bountyOf(4) + "%.";
        if (type == FarmStamps.THRIFT) return "Chance to use nothing: " + Config.thriftOf(1) + " / " + Config.thriftOf(2) + " / " + Config.thriftOf(3)
                + " / " + Config.thriftOf(4) + "%.";
        if (type == FarmStamps.QUALITY) return "Endless bone meal, torches, bait and feed.";
        if (type == FarmStamps.VOID) return "I destroys leftover seeds. II keeps only rifted items.";
        return "";
    }

    private static void info(IRecipeRegistration registration, net.minecraft.world.level.ItemLike item, String... names) {
        List<Component> lines = new ArrayList<>();
        for (String name : names) lines.addAll(paragraphs(name));
        if (!lines.isEmpty()) registration.addIngredientInfo(item, lines.toArray(Component[]::new));
    }

    /** The lang file's lines jei.alchyfarm.info.<name>.1, .2... for as long as they exist. */
    private static List<Component> paragraphs(String name) {
        List<Component> lines = new ArrayList<>();
        net.minecraft.locale.Language language = net.minecraft.locale.Language.getInstance();
        for (int i = 1; language.has("jei.alchyfarm.info." + name + "." + i); i++) lines.add(Component.translatable("jei.alchyfarm.info." + name + "." + i));
        return lines;
    }

    // ---- What things give ----

    /** What a number of rolls gave: for each item, how many each roll had of it. */
    private static final class Rolls {
        private final Map<Item, int[]> counts = new LinkedHashMap<>(); // per item: least, most, rolls it was in
        private int rolls;

        void roll(List<ItemStack> drops) {
            Map<Item, Integer> now = new LinkedHashMap<>();
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) now.merge(drop.getItem(), drop.getCount(), Integer::sum);
            }
            // An item first seen now was absent from every roll before: its least is 0
            for (Map.Entry<Item, Integer> entry : now.entrySet()) {
                int[] seen = counts.computeIfAbsent(entry.getKey(), item -> new int[] {rolls > 0 ? 0 : entry.getValue(), 0, 0});
                seen[0] = Math.min(seen[0], entry.getValue());
                seen[1] = Math.max(seen[1], entry.getValue());
                seen[2]++;
            }
            for (Map.Entry<Item, int[]> entry : counts.entrySet()) {
                if (!now.containsKey(entry.getKey())) entry.getValue()[0] = 0;
            }
            rolls++;
        }

        boolean isEmpty() {
            return counts.isEmpty();
        }

        /**
         * One Given for each item. Something that comes one at a time and not always is told as a chance (a rabbit's
         * foot), the rest as a range (0 to 2 leather).
         */
        List<Given> given() {
            List<Given> given = new ArrayList<>();
            counts.forEach((item, seen) -> {
                ItemStack shown = new ItemStack(item, Math.clamp(seen[1], 1, item.getDefaultMaxStackSize()));
                if (seen[0] == 0 && seen[1] == 1) given.add(new Given(shown, 1, 1, Math.clamp(Math.round(seen[2] * 100.0F / rolls), 1, 99)));
                else given.add(new Given(shown, seen[0], seen[1], 100));
            });
            return given;
        }
    }

    /** A configured harvest, as results to show. */
    private static List<Given> givenOf(List<Config.Drop> drops) {
        List<Given> given = new ArrayList<>();
        for (Config.Drop drop : drops) {
            if (drop.most() <= 0 || drop.chance() <= 0) continue;
            given.add(new Given(new ItemStack(drop.item(), Math.min(drop.most(), drop.item().getDefaultMaxStackSize())), drop.least(), drop.most(), drop.chance()));
        }
        return given;
    }

    /** What the grown crop of this block can drop: its loot table rolled a few times, or its own item without a server. */
    private static List<Given> harvestOf(Block block, @Nullable ServerLevel server) {
        Block harvested = block == Blocks.PUMPKIN_STEM ? Blocks.PUMPKIN : block == Blocks.MELON_STEM ? Blocks.MELON
                : block == Blocks.CAVE_VINES ? ModRegistries.TRELLIS.get() : block;
        List<Config.Drop> set = Config.cropHarvest(BuiltInRegistries.BLOCK.getKey(harvested));
        if (set != null) return givenOf(set);
        if (block == Blocks.CAVE_VINES) return List.of(new Given(new ItemStack(Items.GLOW_BERRIES, 2), 1, 2, 100));
        if (server != null) {
            try {
                // A stem's harvest is the fruit beside it, not the stem
                if (block == Blocks.PUMPKIN_STEM) block = Blocks.PUMPKIN;
                else if (block == Blocks.MELON_STEM) block = Blocks.MELON;
                BlockState grown = block instanceof CropBlock crop ? crop.getStateForAge(crop.getMaxAge()) : block.defaultBlockState();
                Rolls rolls = new Rolls();
                for (int roll = 0; roll < ROLLS; roll++) rolls.roll(Block.getDrops(grown, server, BlockPos.ZERO, null));
                if (!rolls.isEmpty()) return rolls.given();
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: can't read the harvest of {} for JEI: {}", BuiltInRegistries.BLOCK.getKey(block), e.toString());
            }
        }
        Block shown = block == Blocks.PUMPKIN_STEM ? Blocks.PUMPKIN : block == Blocks.MELON_STEM ? Blocks.MELON : block;
        return List.of(new Given(new ItemStack(shown.asItem()), 1, 1, 100));
    }

    /**
     * The orchard page of something it plants. A column plant (cactus, sugar cane, bamboo) gives itself. A sapling
     * gives its tree: the log and the leaves are found by its name (oak_sapling: oak_log, oak_leaves), and what the
     * leaves drop is rolled like a crop's harvest.
     */
    private static Grown treeOf(BlockItem planted, @Nullable ServerLevel server) {
        Block block = planted.getBlock();
        ItemStack seed = new ItemStack(planted);
        if (block.defaultBlockState().is(OrchardBlockEntity.STALKS_TAG)) {
            return new Grown(seed, List.of(new Given(new ItemStack(planted), 1, 1, 100)),
                    planted == Items.CACTUS ? "Cut at 3 tall. Needs free sides" : "Cut above the foot at 3 tall");
        }
        if (block.defaultBlockState().is(OrchardBlockEntity.BUSHES_TAG)) {
            List<Given> berries = List.of(new Given(seed.copy(), 1, 1, 100));
            if (server != null) {
                try {
                    Rolls rolls = new Rolls();
                    for (int roll = 0; roll < ROLLS; roll++) rolls.roll(Block.getDrops(OrchardBlockEntity.ripe(block), server, BlockPos.ZERO, null));
                    if (!rolls.isEmpty()) berries = rolls.given();
                } catch (RuntimeException e) {
                    AlchyFarm.LOGGER.warn("AlchyFarm: can't read what {} gives for JEI: {}", BuiltInRegistries.BLOCK.getKey(block), e.toString());
                }
            }
            return new Grown(seed, berries, "Picked ripe, the bush stays");
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String wood = id.getPath().replace("_sapling", "");
        List<Given> results = new ArrayList<>();
        Block log = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), wood + "_log")).orElse(null);
        List<Config.Drop> logSet = log == null ? null : Config.orchardHarvest(BuiltInRegistries.BLOCK.getKey(log));
        if (logSet != null) results.addAll(givenOf(logSet));
        else if (log != null) results.add(new Given(new ItemStack(log.asItem()), 1, 1, 100));
        Block leaves = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), wood + "_leaves")).orElse(null);
        boolean rolled = false;
        List<Config.Drop> leafSet = leaves == null ? null : Config.orchardHarvest(BuiltInRegistries.BLOCK.getKey(leaves));
        if (leafSet != null) {
            results.addAll(givenOf(leafSet));
            rolled = true;
        } else if (leaves != null && server != null) {
            try {
                Rolls rolls = new Rolls();
                for (int roll = 0; roll < ROLLS * 3; roll++) rolls.roll(Block.getDrops(leaves.defaultBlockState(), server, BlockPos.ZERO, null));
                results.addAll(rolls.given());
                rolled = true;
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: can't read what {} drop for JEI: {}", BuiltInRegistries.BLOCK.getKey(leaves), e.toString());
            }
        }
        if (!rolled) results.add(new Given(seed.copy(), 1, 1, 100));
        return new Grown(seed, results, "Logs, and what the leaves drop");
    }

    /** What the pond's fisherman can catch: its loot table rolled, or the four fish without a server. */
    private static List<Given> catchOf(@Nullable ServerLevel server) {
        List<Config.Drop> catches = Config.pondCatches();
        if (!catches.isEmpty()) { // the config's own list: each with its share of the weights as its chance
            int total = 0;
            for (Config.Drop drop : catches) total += drop.chance();
            List<Given> given = new ArrayList<>();
            for (Config.Drop drop : catches) {
                given.add(new Given(new ItemStack(drop.item(), Math.clamp(drop.most(), 1, drop.item().getDefaultMaxStackSize())),
                        Math.max(1, drop.least()), Math.max(1, drop.most()), Math.clamp(Math.round(drop.chance() * 100.0F / total), 1, 100)));
            }
            return given;
        }
        Identifier id = Identifier.tryParse(Config.pondLootTable());
        if (server != null && id != null) {
            try {
                LootTable table = server.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
                LootParams params = new LootParams.Builder(server)
                        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                        .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                        .create(LootContextParamSets.FISHING);
                Rolls rolls = new Rolls();
                for (int roll = 0; roll < ROLLS * 5; roll++) rolls.roll(table.getRandomItems(params));
                if (!rolls.isEmpty()) return rolls.given();
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: can't read the pond's catch for JEI: {}", e.toString());
            }
        }
        List<Given> fish = new ArrayList<>();
        for (Item item : List.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH)) fish.add(new Given(new ItemStack(item), 1, 1, 100));
        return fish;
    }

    /** What a cast with this bait can bring up: the game's fish, and its fishing treasure as often as the bait says. */
    private static List<Given> baitedCatch(@Nullable ServerLevel server, Config.Bait bait) {
        List<Given> given = new ArrayList<>();
        addShare(given, server, BuiltInLootTables.FISHING_FISH, 100 - bait.treasure(),
                List.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH));
        addShare(given, server, BuiltInLootTables.FISHING_TREASURE, bait.treasure(),
                List.of(Items.BOW, Items.ENCHANTED_BOOK, Items.FISHING_ROD, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE));
        return given;
    }

    /**
     * Adds what a loot table gives, each chance cut down to the share of casts that roll that table. Without a
     * server to roll it on, the items it is known to hold, the share split evenly between them.
     */
    private static void addShare(List<Given> into, @Nullable ServerLevel server, ResourceKey<LootTable> table, int share, List<Item> known) {
        if (share <= 0) return;
        if (server != null) {
            try {
                LootParams params = new LootParams.Builder(server)
                        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                        .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                        .create(LootContextParamSets.FISHING);
                LootTable rolled = server.getServer().reloadableRegistries().getLootTable(table);
                Rolls rolls = new Rolls();
                for (int roll = 0; roll < ROLLS * 4; roll++) rolls.roll(rolled.getRandomItems(params));
                for (Given one : rolls.given()) {
                    into.add(new Given(one.stack(), one.least(), one.most(), Math.clamp(Math.round(one.chance() * share / 100.0F), 1, 100)));
                }
                return;
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: can't read {} for JEI: {}", table.identifier(), e.toString());
            }
        }
        for (Item item : known) into.add(new Given(new ItemStack(item), 1, 1, Math.max(1, share / known.size())));
    }

    /** The ranch page of this kind of animal, or null if a ranch gets nothing from it. */
    private static @Nullable Ranch ranchOf(EntityType<?> type, Animal animal, @Nullable ServerLevel server) {
        List<ItemStack> feed = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (animal.isFood(stack)) feed.add(stack);
        }

        List<Given> drops = new ArrayList<>();
        boolean usual = false;
        List<Config.Drop> configured = Config.ranchDrops(EntityType.getKey(type));
        if (configured != null) {
            for (Config.Drop drop : configured) {
                if (drop.most() <= 0 || drop.chance() <= 0) continue;
                drops.add(new Given(new ItemStack(drop.item(), Math.min(drop.most(), drop.item().getDefaultMaxStackSize())),
                        drop.least(), drop.most(), drop.chance()));
            }
        } else if (server != null) {
            Entity body = type.create(server, EntitySpawnReason.LOAD);
            Optional<ResourceKey<LootTable>> table = body instanceof Animal known ? known.getLootTable() : Optional.empty();
            if (body != null && table.isPresent()) {
                LootParams params = new LootParams.Builder(server)
                        .withParameter(LootContextParams.THIS_ENTITY, body)
                        .withParameter(LootContextParams.ORIGIN, body.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().generic())
                        .create(LootContextParamSets.ENTITY);
                LootTable rolled = server.getServer().reloadableRegistries().getLootTable(table.get());
                Rolls rolls = new Rolls();
                for (int roll = 0; roll < ROLLS; roll++) rolls.roll(rolled.getRandomItems(params));
                drops = rolls.given();
            }
        } else {
            usual = true; // a dedicated server's loot tables can't be read from here
        }

        List<ItemStack> vessel = new ArrayList<>(), milk = new ArrayList<>();
        if (animal instanceof AbstractCow || animal instanceof Goat) {
            vessel.add(new ItemStack(Items.BUCKET));
            milk.add(new ItemStack(Items.MILK_BUCKET));
        }
        if (animal instanceof MushroomCow) {
            vessel.add(new ItemStack(Items.BOWL));
            milk.add(new ItemStack(Items.MUSHROOM_STEW));
        }
        if (drops.isEmpty() && milk.isEmpty() && !usual) return null;
        ItemStack egg = SpawnEggItem.byId(type).map(holder -> new ItemStack((Holder<Item>) holder)).orElse(ItemStack.EMPTY);
        return new Ranch(egg, type.getDescription().getString(), feed, drops, usual, vessel, milk);
    }

    /** What crafting makes of this flower alone (its dye), read on the integrated server. Empty on a dedicated one. */
    private static ItemStack dyeOf(Item flower, @Nullable ServerLevel server) {
        if (server == null) return ItemStack.EMPTY;
        try {
            CraftingInput alone = CraftingInput.of(1, 1, List.of(new ItemStack(flower)));
            return server.recipeAccess().getRecipeFor(RecipeType.CRAFTING, alone, server).map(found -> found.value().assemble(alone)).orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static void text(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, text, x, y, TEXT, false);
    }

    /** Words wrapped to a page this wide, two lines at most. */
    private static void words(GuiGraphicsExtractor graphics, String text, int x, int y, int width) {
        List<String> lines = Lines.wrap(Minecraft.getInstance().font, text, width - x - 2);
        for (int i = 0; i < Math.min(2, lines.size()); i++) text(graphics, lines.get(i), x, y + i * 10);
    }

    /** A result's slot, 18 wide, with its amount and chance in its tooltip. */
    private static void result(IRecipeLayoutBuilder builder, Given given, int x, int y) {
        builder.addOutputSlot(x, y).setStandardSlotBackground().add(given.stack()).addRichTooltipCallback((view, tooltip) -> given.describe(tooltip));
    }

    /** The short words under a result's slot (its chance or its range), centred on it. */
    private static void label(GuiGraphicsExtractor graphics, Given given, int x, int y) {
        String words = given.label();
        if (words.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        graphics.text(font, words, x + 8 - font.width(words) / 2, y + 18, FAINT, false);
    }

    // ---- Crop plot: seed -> harvest ----

    // Several results stand side by side: they take the standard slot frame (JEI's "output" frame is the big 26 pixel
    // one, made for a single result, and overlaps its neighbors). Under each, its chance or its range.
    public static class CropCategory extends AbstractRecipeCategory<Crop> {
        private static final int RESULTS_X = 72, SPACE = 22;

        public CropCategory(IGuiHelper gui) {
            super(CROP, Component.translatable("jei.alchyfarm.crop_plot"), gui.createDrawableItemLike(ModRegistries.CARVED_STAKE_ITEM.get()), 164, 48);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Crop recipe, IFocusGroup focuses) {
            builder.addInputSlot(6, 4).setStandardSlotBackground().add(recipe.seed());
            for (int i = 0; i < Math.min(4, recipe.harvest().size()); i++) result(builder, recipe.harvest().get(i), RESULTS_X + i * SPACE, 4);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Crop recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(34, 4);
        }

        @Override
        public void draw(Crop recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            for (int i = 0; i < Math.min(4, recipe.harvest().size()); i++) label(graphics, recipe.harvest().get(i), RESULTS_X + i * SPACE, 4);
            text(graphics, recipe.seed().is(TrellisBlock.PLANTS_TAG) ? "Only on a trellis" : "One seed per square", 6, 37);
        }
    }

    /**
     * One possible layout, 7 by 7: fence all round with a gate in the far side, the post in the middle of the near
     * side with a stock and a basket beside it in the fence's line (and a trough for a ranch), "inside" on the 25
     * squares closed in, or "middle" on the 9 in their middle when there is one.
     */
    private static Layout layout(String name, Item post, boolean trough, Cell inside, @Nullable Cell middle, String words) {
        Cell fence = new Cell(new ItemStack(Items.OAK_FENCE), "Fence, gate or wall, joined to the post: any shape that closes, up to 9 by 9 inside");
        Cell[][] rows = new Cell[7][7];
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                rows[z][x] = edge ? fence : middle != null && x >= 2 && x <= 4 && z >= 2 && z <= 4 ? middle : inside;
            }
        }
        rows[0][3] = new Cell(new ItemStack(Items.OAK_FENCE_GATE), "A gate counts as fence");
        rows[6][3] = new Cell(new ItemStack(post), "The post: part of the fence's line");
        rows[6][2] = new Cell(new ItemStack(ModRegistries.STOCK_ITEM.get()), "Stock: in the fence's line, or against it");
        rows[6][4] = new Cell(new ItemStack(ModRegistries.BASKET_ITEM.get()), "Basket: in the fence's line, or against it");
        if (trough) rows[6][1] = new Cell(new ItemStack(ModRegistries.TROUGH_ITEM.get()), "Trough: in the fence's line, or against it");
        return new Layout(name, rows, words);
    }

    /**
     * The mushroom hollow, which is a fixed structure: its middle layer seen from above (the post's level), with the
     * bed and the roof told in notes and in the line of words.
     */
    private static Layout hollow() {
        Cell wall = new Cell(new ItemStack(Items.COBBLESTONE), "Rough stone (cobblestone, stone, deepslate, tuff...): the walls, 3 high, on a floor of the same, under a roof of the same. All of it turns to moss when the hollow forms, and is used up.");
        Cell bed = new Cell(new ItemStack(Items.MYCELIUM), "The bed: mycelium, podzol or nylium, one block lower than the post. Leave the space over it free.");
        Cell[][] rows = new Cell[7][7];
        for (int z = 1; z <= 5; z++) {
            for (int x = 1; x <= 5; x++) rows[z][x] = x >= 2 && x <= 4 && z >= 2 && z <= 4 ? bed : wall;
        }
        rows[5][3] = new Cell(new ItemStack(ModRegistries.FORAGER_POST_ITEM.get()), "The post, facing out. Right-click it to see what is missing, then to choose the mushrooms.");
        rows[5][2] = new Cell(new ItemStack(ModRegistries.STOCK_ITEM.get()), "Stock: on this side of the post. One of each mushroom to grow, and bone meal. Both optional.");
        rows[5][4] = new Cell(new ItemStack(ModRegistries.BASKET_ITEM.get()), "Basket: on this side of the post. The front stays open above the stock and the basket.");
        return new Layout("jei.alchyfarm.mushroom_farm", rows, "Walls 3 high, a floor under, a roof over");
    }

    /** The mine, a fixed structure like the hollow: its middle layer seen from above. */
    private static Layout shaft() {
        Cell wall = new Cell(new ItemStack(Items.COBBLESTONE), "Rough stone (cobblestone, stone, deepslate, tuff...): the walls, 3 high, on a floor of the same, under a roof of the same. It turns to rock when the mine forms, and is used up. The ore being dug shows on these walls.");
        Cell timber = new Cell(new ItemStack(Items.OAK_LOG), "Logs: the two front corners, 3 high, and a beam of five across the roof's front edge.");
        Cell floor = new Cell(new ItemStack(Items.STONE), "The floor inside: the same rough stone, one block lower than the post. Leave the space over it free.");
        Cell[][] rows = new Cell[7][7];
        for (int z = 1; z <= 5; z++) {
            for (int x = 1; x <= 5; x++) rows[z][x] = x >= 2 && x <= 4 && z >= 2 && z <= 4 ? floor : wall;
        }
        rows[5][1] = timber;
        rows[5][5] = timber;
        rows[5][3] = new Cell(new ItemStack(ModRegistries.MINER_POST_ITEM.get()), "The post, facing out. Right-click it to see what is missing, then to choose what it keeps.");
        rows[5][2] = new Cell(new ItemStack(ModRegistries.STOCK_ITEM.get()), "Stock: on this side of the post. Pickaxes, and torches if you like.");
        rows[5][4] = new Cell(new ItemStack(ModRegistries.BASKET_ITEM.get()), "Basket: on this side of the post. The front stays open above the stock and the basket.");
        return new Layout("jei.alchyfarm.mine", rows, "Walls 3 high, a floor under, a roof over");
    }

    /** The nether mine: the mine's shape, of the Nether's stone. */
    private static Layout netherShaft() {
        Cell wall = new Cell(new ItemStack(Items.NETHERRACK), "Nether stone (netherrack, blackstone, basalt, nether bricks): the walls, 3 high, on a floor of the same, under a roof of the same. It turns to blackstone when the mine forms, and is used up. What is being dug shows on these walls.");
        Cell timber = new Cell(new ItemStack(Items.CRIMSON_STEM), "Logs or stems: the two front corners, 3 high, and a beam of five across the roof's front edge.");
        Cell floor = new Cell(new ItemStack(Items.BLACKSTONE), "The floor inside: the same stone, one block lower than the post. Leave the space over it free.");
        Cell[][] rows = new Cell[7][7];
        for (int z = 1; z <= 5; z++) {
            for (int x = 1; x <= 5; x++) rows[z][x] = x >= 2 && x <= 4 && z >= 2 && z <= 4 ? floor : wall;
        }
        rows[5][1] = timber;
        rows[5][5] = timber;
        rows[5][3] = new Cell(new ItemStack(ModRegistries.NETHER_MINER_POST_ITEM.get()), "The post, facing out. Right-click it to see what is missing, then to choose what it digs.");
        rows[5][2] = new Cell(new ItemStack(ModRegistries.STOCK_ITEM.get()), "Stock: on this side of the post. Pickaxes, and torches if you like.");
        rows[5][4] = new Cell(new ItemStack(ModRegistries.BASKET_ITEM.get()), "Basket: on this side of the post. The front stays open above the stock and the basket.");
        return new Layout("jei.alchyfarm.nether_mine", rows, "Walls 3 high, a floor under, a roof over");
    }

    // ---- Layouts: one possible way to build each workstation, seen from above ----

    public static class LayoutCategory extends AbstractRecipeCategory<Layout> {
        private static final int GRID_X = 25, GRID_Y = 12, CELL = 18;

        public LayoutCategory(IGuiHelper gui) {
            super(LAYOUT, Component.translatable("jei.alchyfarm.layout"), gui.createDrawableItemLike(Items.OAK_FENCE), 176, GRID_Y + 7 * CELL + 34);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Layout recipe, IFocusGroup focuses) {
            for (int z = 0; z < recipe.rows().length; z++) {
                for (int x = 0; x < recipe.rows()[z].length; x++) {
                    Cell cell = recipe.rows()[z][x];
                    if (cell == null || cell.stack().isEmpty()) continue;
                    builder.addInputSlot(GRID_X + x * CELL + 1, GRID_Y + z * CELL + 1).add(cell.stack())
                            .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal(cell.note()).withStyle(ChatFormatting.GRAY)));
                }
            }
        }

        @Override
        public void draw(Layout recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            graphics.text(Minecraft.getInstance().font, Component.translatable(recipe.name()), 4, 0, TEXT, false);
            text(graphics, recipe.name().endsWith("mushroom_farm") || recipe.name().endsWith("mine") ? "Fixed structure, post level" : "Example layout, from above", 4, GRID_Y + 7 * CELL + 3);
            words(graphics, recipe.words(), 4, GRID_Y + 7 * CELL + 13, 176);
        }
    }

    // ---- Orchard, pond, mushroom hollow: what is planted, on what -> what comes of it, in rows of five ----

    public static class GrownCategory extends AbstractRecipeCategory<Grown> {
        private static final int RESULTS_X = 72, SPACE = 20, PER_ROW = 5, ROW = 30;
        private final int rows;

        public GrownCategory(IGuiHelper gui, IRecipeType<Grown> type, String title, Item icon, int rows) {
            super(type, Component.translatable(title), gui.createDrawableItemLike(icon), 176, rows * ROW + 24);
            this.rows = rows;
        }

        private int shown(Grown recipe) {
            return Math.min(rows * PER_ROW, recipe.results().size());
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Grown recipe, IFocusGroup focuses) {
            if (!recipe.seed().isEmpty()) builder.addInputSlot(2, 4).setStandardSlotBackground().add(recipe.seed());
            if (!recipe.ground().isEmpty()) {
                builder.addInputSlot(22, 4).setStandardSlotBackground().addItemStacks(recipe.ground())
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.literal(recipe.groundNote()).withStyle(ChatFormatting.GRAY)));
            }
            for (int i = 0; i < shown(recipe); i++) result(builder, recipe.results().get(i), RESULTS_X + i % PER_ROW * SPACE, 4 + i / PER_ROW * ROW);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Grown recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(44, 4);
        }

        @Override
        public void draw(Grown recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            for (int i = 0; i < shown(recipe); i++) label(graphics, recipe.results().get(i), RESULTS_X + i % PER_ROW * SPACE, 4 + i / PER_ROW * ROW);
            words(graphics, recipe.words(), 4, rows * ROW + 3, 176);
        }
    }

    // ---- Ranch: animal + feed -> drops, bucket or bowl -> milk or stew ----

    // From the top: the animal's name, its drops with their chance or range under them, one line for each vessel
    // (a mooshroom has two: bucket and bowl), then the two lines of words, under where a second vessel line would be
    public static class RanchCategory extends AbstractRecipeCategory<Ranch> {
        private static final int RESULTS_X = 84, SPACE = 22, DROPS_Y = 12, VESSELS_Y = 42, LINE = 20, WORDS_Y = 86;

        public RanchCategory(IGuiHelper gui) {
            super(RANCH, Component.translatable("jei.alchyfarm.ranch"), gui.createDrawableItemLike(ModRegistries.HITCHING_POST_ITEM.get()), 176, 108);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Ranch recipe, IFocusGroup focuses) {
            if (!recipe.egg().isEmpty()) builder.addInputSlot(4, DROPS_Y).add(recipe.egg());
            if (!recipe.feed().isEmpty()) builder.addInputSlot(28, DROPS_Y).setStandardSlotBackground().addItemStacks(recipe.feed());
            for (int i = 0; i < Math.min(4, recipe.drops().size()); i++) result(builder, recipe.drops().get(i), RESULTS_X + i * SPACE, DROPS_Y);
            for (int i = 0; i < recipe.vessel().size(); i++) {
                builder.addInputSlot(28, VESSELS_Y + i * LINE).setStandardSlotBackground().add(recipe.vessel().get(i));
                builder.addOutputSlot(RESULTS_X, VESSELS_Y + i * LINE).setStandardSlotBackground().add(recipe.milk().get(i));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Ranch recipe, IFocusGroup focuses) {
            if (!recipe.drops().isEmpty() || recipe.usual()) builder.addRecipeArrowWidget().setPosition(52, DROPS_Y);
            for (int i = 0; i < recipe.vessel().size(); i++) builder.addRecipeArrowWidget().setPosition(52, VESSELS_Y + i * LINE);
        }

        @Override
        public void draw(Ranch recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            text(graphics, recipe.name(), 4, 0);
            if (recipe.usual()) text(graphics, "its usual drops", RESULTS_X, DROPS_Y + 4);
            for (int i = 0; i < Math.min(4, recipe.drops().size()); i++) label(graphics, recipe.drops().get(i), RESULTS_X + i * SPACE, DROPS_Y);
            int feed = Config.feedPerDrop();
            String every = "Every " + Config.secondsPerDrop() + " s" + (feed > 0 ? " for " + feed + " feed" : "");
            text(graphics, recipe.drops().isEmpty() && !recipe.usual() ? "Milked every " + Config.secondsPerMilk() + " s" : every, 4, WORDS_Y);
            text(graphics, "Breeding: " + Config.feedPerBreeding() + " feed", 4, WORDS_Y + 10);
        }
    }
}
