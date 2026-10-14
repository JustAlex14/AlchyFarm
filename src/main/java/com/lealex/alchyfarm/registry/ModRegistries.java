package com.lealex.alchyfarm.registry;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.CarvedStakeBlock;
import com.lealex.alchyfarm.block.CaskBlock;
import com.lealex.alchyfarm.block.FacingPostBlock;
import com.lealex.alchyfarm.block.ForagerPostBlock;
import com.lealex.alchyfarm.block.GildedCropBlock;
import com.lealex.alchyfarm.block.PlotStoreBlock;
import com.lealex.alchyfarm.block.PumpBlock;
import com.lealex.alchyfarm.block.TrellisBlock;
import com.lealex.alchyfarm.block.entity.ApiaryBlockEntity;
import com.lealex.alchyfarm.block.entity.CaskBlockEntity;
import com.lealex.alchyfarm.block.entity.CropPlotBlockEntity;
import com.lealex.alchyfarm.block.entity.FlowerGardenBlockEntity;
import com.lealex.alchyfarm.block.entity.GildedGardenBlockEntity;
import com.lealex.alchyfarm.block.entity.MineBlockEntity;
import com.lealex.alchyfarm.block.entity.MushroomFarmBlockEntity;
import com.lealex.alchyfarm.block.entity.NetherMineBlockEntity;
import com.lealex.alchyfarm.block.entity.OrchardBlockEntity;
import com.lealex.alchyfarm.block.entity.PlotStoreBlockEntity;
import com.lealex.alchyfarm.block.entity.PondBlockEntity;
import com.lealex.alchyfarm.block.entity.RanchBlockEntity;
import com.lealex.alchyfarm.item.HerdPostItem;
import com.lealex.alchyfarm.item.StampItem;
import com.lealex.alchyfarm.upgrade.FarmStamps;
import com.lealex.alchyx.upgrade.UpgradeType;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.menu.CropPlotMenu;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Everything AlchyFarm registers: blocks, items, block entity types and the creative tab. */
public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlchyFarm.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AlchyFarm.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AlchyFarm.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AlchyFarm.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, AlchyFarm.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AlchyFarm.MODID);

    /**
     * Data stored on a hitching post item: the herd of the ranch it was the core of, every animal with all its data
     * (RanchBlockEntity). Sent to clients too, so that moving the item around a creative inventory does not empty it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<CompoundTag>>> HERD =
            DATA_COMPONENTS.registerComponentType("herd", builder -> builder
                    .persistent(CompoundTag.CODEC.listOf())
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list())));

    /** The crop plot's core: an old carved stake from the hut, a post of the fence that closes around a field. */
    public static final DeferredBlock<CarvedStakeBlock> CARVED_STAKE = BLOCKS.registerBlock("carved_stake",
            p -> new CarvedStakeBlock(CropPlotBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> CARVED_STAKE_ITEM = ITEMS.registerSimpleBlockItem("carved_stake", CARVED_STAKE);

    /** The ranch's core: an old hitching post from the hut, a post of the fence that closes around a pen. */
    public static final DeferredBlock<CarvedStakeBlock> HITCHING_POST = BLOCKS.registerBlock("hitching_post",
            p -> new CarvedStakeBlock(RanchBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<HerdPostItem> HITCHING_POST_ITEM = ITEMS.registerItem("hitching_post",
            p -> new HerdPostItem(HITCHING_POST.get(), p), p -> p.useBlockDescriptionPrefix());

    /** The orchard's core: an old woodsman's post from the hut, a post of the fence that closes around a grove. */
    public static final DeferredBlock<CarvedStakeBlock> WOODSMAN_POST = BLOCKS.registerBlock("woodsman_post",
            p -> new CarvedStakeBlock(OrchardBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> WOODSMAN_POST_ITEM = ITEMS.registerSimpleBlockItem("woodsman_post", WOODSMAN_POST);

    /** The pond's core: an old angler's post from the hut, a post of the fence that closes around water. */
    public static final DeferredBlock<CarvedStakeBlock> ANGLER_POST = BLOCKS.registerBlock("angler_post",
            p -> new CarvedStakeBlock(PondBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> ANGLER_POST_ITEM = ITEMS.registerSimpleBlockItem("angler_post", ANGLER_POST);

    /** The mushroom hollow's core: an old forager's post from the hut, the front of a fixed structure (not a fence post). */
    public static final DeferredBlock<ForagerPostBlock> FORAGER_POST = BLOCKS.registerBlock("forager_post",
            ForagerPostBlock::new,
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> FORAGER_POST_ITEM = ITEMS.registerSimpleBlockItem("forager_post", FORAGER_POST);

    /**
     * A soil for the mushroom hollow's bed that every kind stands on: mushrooms in any light, both nether fungi and
     * nether wart (it is in the game's own block tags that those ask of the ground under them).
     */
    public static final DeferredBlock<Block> SPORE_LOAM = BLOCKS.registerBlock("spore_loam",
            Block::new,
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(0.6f)
                    .sound(SoundType.ROOTED_DIRT));
    public static final DeferredItem<BlockItem> SPORE_LOAM_ITEM = ITEMS.registerSimpleBlockItem("spore_loam", SPORE_LOAM);

    /** A frame stood on a square of a crop plot's field, for glow berries: a vine hangs in it and bears. */
    public static final DeferredBlock<TrellisBlock> TRELLIS = BLOCKS.registerBlock("trellis",
            TrellisBlock::new,
            p -> p.mapColor(MapColor.WOOD)
                    .strength(1.0f, 2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noCollision()
                    .lightLevel(state -> state.getValue(TrellisBlock.AGE) == TrellisBlock.BERRIES ? 14 : 0));
    public static final DeferredItem<BlockItem> TRELLIS_ITEM = ITEMS.registerSimpleBlockItem("trellis", TRELLIS);

    /** The gilded garden's core: an old gilder's post from the hut, a post of the fence that closes around a field. */
    public static final DeferredBlock<CarvedStakeBlock> GILDER_POST = BLOCKS.registerBlock("gilder_post",
            p -> new CarvedStakeBlock(GildedGardenBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> GILDER_POST_ITEM = ITEMS.registerSimpleBlockItem("gilder_post", GILDER_POST);

    /**
     * The gilded garden's crops. They have no item: only the garden's soul sows them, each from the golden thing it
     * gives (a golden carrot, a golden apple, a glistering melon slice).
     */
    public static final DeferredBlock<GildedCropBlock> GILDED_CARROTS = BLOCKS.registerBlock("gilded_carrots",
            p -> new GildedCropBlock(() -> Items.GOLDEN_CARROT, false, 2, p),
            p -> p.mapColor(MapColor.GOLD)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CROP)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<GildedCropBlock> GILDED_APPLE_TREE = BLOCKS.registerBlock("gilded_apple_tree",
            p -> new GildedCropBlock(() -> Items.GOLDEN_APPLE, true, 4, p),
            p -> p.mapColor(MapColor.PLANT)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CHERRY_SAPLING)
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(GildedCropBlock.AGE) == GildedCropBlock.MAX_AGE ? 6 : 0));

    /** The little tree again, for plain apples: sown by the crop plot's farmhand from an apple, on its tilled field. */
    public static final DeferredBlock<GildedCropBlock> APPLE_TREE = BLOCKS.registerBlock("apple_tree",
            p -> new GildedCropBlock(() -> Items.APPLE, true, 3, p),
            p -> p.mapColor(MapColor.PLANT)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CHERRY_SAPLING)
                    .pushReaction(PushReaction.DESTROY));

    /** The same little tree for enchanted golden apples: much slower to grow, and brighter when it bears. */
    public static final DeferredBlock<GildedCropBlock> ENCHANTED_APPLE_TREE = BLOCKS.registerBlock("enchanted_apple_tree",
            p -> new GildedCropBlock(() -> Items.ENCHANTED_GOLDEN_APPLE, true, 16, p),
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CHERRY_SAPLING)
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(GildedCropBlock.AGE) == GildedCropBlock.MAX_AGE ? 10 : 0));

    /**
     * The gilded melon: a stem like the melon's (the game's own StemBlock, on farmland, its fruit on the soil beside
     * it), whose fruit is the glistering melon, a block that gives glistering slices. None of the three has an item.
     */
    private static final ResourceKey<Block> GILDED_MELON_STEM_KEY = blockKey("gilded_melon_stem"),
            ATTACHED_GILDED_MELON_STEM_KEY = blockKey("attached_gilded_melon_stem"), GLISTERING_MELON_KEY = blockKey("glistering_melon");
    private static final ResourceKey<Item> GLISTERING_SLICE_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("glistering_melon_slice"));

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, name));
    }

    public static final DeferredBlock<Block> GLISTERING_MELON = BLOCKS.registerBlock("glistering_melon",
            Block::new,
            p -> p.mapColor(MapColor.GOLD)
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<StemBlock> GILDED_MELON_STEM = BLOCKS.registerBlock("gilded_melon_stem",
            p -> new StemBlock(GLISTERING_MELON_KEY, ATTACHED_GILDED_MELON_STEM_KEY, GLISTERING_SLICE_KEY,
                    BlockTags.SUPPORTS_MELON_STEM, BlockTags.SUPPORTS_MELON_STEM_FRUIT, p),
            p -> p.mapColor(MapColor.PLANT)
                    .noCollision()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.HARD_CROP)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<AttachedStemBlock> ATTACHED_GILDED_MELON_STEM = BLOCKS.registerBlock("attached_gilded_melon_stem",
            p -> new AttachedStemBlock(GILDED_MELON_STEM_KEY, GLISTERING_MELON_KEY, GLISTERING_SLICE_KEY, BlockTags.SUPPORTS_MELON_STEM, p),
            p -> p.mapColor(MapColor.PLANT)
                    .noCollision()
                    .instabreak()
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY));

    /** The flower garden's core: an old gardener's post from the hut, a post of the fence that closes around a garden. */
    public static final DeferredBlock<CarvedStakeBlock> GARDENER_POST = BLOCKS.registerBlock("gardener_post",
            p -> new CarvedStakeBlock(FlowerGardenBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> GARDENER_POST_ITEM = ITEMS.registerSimpleBlockItem("gardener_post", GARDENER_POST);

    /** The nether mine's core: the front of a fixed structure of the Nether's stone (not a fence post). */
    public static final DeferredBlock<FacingPostBlock> NETHER_MINER_POST = BLOCKS.registerBlock("nether_miner_post",
            p -> new FacingPostBlock(NetherMineBlockEntity::new, p),
            p -> p.mapColor(MapColor.CRIMSON_STEM)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.NETHER_WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> NETHER_MINER_POST_ITEM = ITEMS.registerSimpleBlockItem("nether_miner_post", NETHER_MINER_POST);

    /** The apiary's core: an old beekeeper's post from the hut, a post of the fence that closes around a bee yard. */
    public static final DeferredBlock<CarvedStakeBlock> BEEKEEPER_POST = BLOCKS.registerBlock("beekeeper_post",
            p -> new CarvedStakeBlock(ApiaryBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> BEEKEEPER_POST_ITEM = ITEMS.registerSimpleBlockItem("beekeeper_post", BEEKEEPER_POST);

    /** The mine's core: an old miner's post from the hut, the front of a fixed structure (not a fence post). */
    public static final DeferredBlock<FacingPostBlock> MINER_POST = BLOCKS.registerBlock("miner_post",
            p -> new FacingPostBlock(MineBlockEntity::new, p),
            p -> p.mapColor(MapColor.PODZOL)
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> MINER_POST_ITEM = ITEMS.registerSimpleBlockItem("miner_post", MINER_POST);

    /**
     * Construction items: materials for the recipes, with no other use. Three hands show in them. The HUT's: the
     * carved splinter (old grey wood the rift got into), in every post. The ESTATE's, the nobleman's tidy work: dark
     * timber, wrought iron fittings, pages of his ledger. And his SEALS, which tell his story: the family's crimson
     * for the first workstations, then, once he is disinherited, a violet seal of amethyst and obsidian (the same
     * crest, the family's mark struck out) for the later ones, which takes an ender pearl: by then he calls the rift.
     */
    public static final DeferredItem<Item> CARVED_SPLINTER = ITEMS.registerItem("carved_splinter", Item::new, p -> p);
    /** The surveyor's cord: links a post to stores and a field away from it (item/SurveyorCordItem). */
    public static final DeferredItem<com.lealex.alchyfarm.item.SurveyorCordItem> SURVEYOR_CORD = ITEMS.registerItem("surveyor_cord",
            com.lealex.alchyfarm.item.SurveyorCordItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> ESTATE_TIMBER = ITEMS.registerItem("estate_timber", Item::new, p -> p);
    public static final DeferredItem<Item> IRON_FITTING = ITEMS.registerItem("iron_fitting", Item::new, p -> p);
    public static final DeferredItem<Item> LEDGER_PAGE = ITEMS.registerItem("ledger_page", Item::new, p -> p);
    public static final DeferredItem<Item> CRIMSON_WAX = ITEMS.registerItem("crimson_wax", Item::new, p -> p);
    public static final DeferredItem<Item> CRIMSON_SEAL = ITEMS.registerItem("crimson_seal", Item::new, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> VIOLET_WAX = ITEMS.registerItem("violet_wax", Item::new, p -> p);
    public static final DeferredItem<Item> VIOLET_SEAL = ITEMS.registerItem("violet_seal", Item::new, p -> p.rarity(Rarity.RARE));

    /**
     * The stamps (upgrade/FarmStamps): haste_stamp_1..4, bounty_stamp_1..4, thrift_stamp_1..4, in that order.
     * Tiers 1 and 2 are crimson, 3 and 4 violet.
     */
    public static final List<DeferredItem<StampItem>> STAMPS = stamps();

    /** The stamp of this kind (its place in FarmStamps.ALL) and tier, as a stack. Empty if there is none. */
    public static net.minecraft.world.item.ItemStack stamp(int kind, int tier) {
        if (kind < 0 || kind >= FarmStamps.ALL.size()) return net.minecraft.world.item.ItemStack.EMPTY;
        for (DeferredItem<StampItem> stamp : STAMPS) {
            if (stamp.get().type() == FarmStamps.ALL.get(kind) && stamp.get().tier() == tier) return new net.minecraft.world.item.ItemStack(stamp.get());
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    private static List<DeferredItem<StampItem>> stamps() {
        List<DeferredItem<StampItem>> made = new java.util.ArrayList<>(12);
        for (UpgradeType type : FarmStamps.ALL) {
            for (int tier = 1; tier <= type.maxTier(); tier++) {
                int of = tier;
                made.add(ITEMS.registerItem(type.id().getPath() + "_stamp_" + tier, p -> new StampItem(p, type, of),
                        p -> p.stacksTo(1).rarity(of >= 4 ? Rarity.EPIC : of == 3 ? Rarity.RARE : of == 2 ? Rarity.UNCOMMON : Rarity.COMMON)));
            }
        }
        return List.copyOf(made);
    }

    /**
     * Baits for the pond's fisherman, kept in its stock: one is used at each cast. A better bait means a shorter
     * wait, no junk, and a chance of treasure (Config pond.baits says how much for each, and can name other items).
     */
    public static final DeferredItem<Item> BAIT = ITEMS.registerItem("bait", Item::new, p -> p);
    public static final DeferredItem<Item> FINE_BAIT = ITEMS.registerItem("fine_bait", Item::new, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> RIFT_BAIT = ITEMS.registerItem("rift_bait", Item::new, p -> p.rarity(Rarity.RARE));

    /**
     * A workstation's storage, joined to it by touching its fence: the stock holds what the soul uses, the basket
     * takes what it brings back, the trough holds the feed a ranch's animals eat.
     */
    public static final DeferredBlock<PlotStoreBlock> STOCK = BLOCKS.registerBlock("stock",
            p -> new PlotStoreBlock(PlotStoreBlock.Kind.STOCK, p), p -> p.mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> STOCK_ITEM = ITEMS.registerSimpleBlockItem("stock", STOCK);
    public static final DeferredBlock<PlotStoreBlock> BASKET = BLOCKS.registerBlock("basket",
            p -> new PlotStoreBlock(PlotStoreBlock.Kind.BASKET, p), p -> p.mapColor(MapColor.SAND).strength(1.0f, 2.0f).sound(SoundType.BAMBOO_WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> BASKET_ITEM = ITEMS.registerSimpleBlockItem("basket", BASKET);
    public static final DeferredBlock<PlotStoreBlock> TROUGH = BLOCKS.registerBlock("trough",
            p -> new PlotStoreBlock(PlotStoreBlock.Kind.TROUGH, p),
            p -> p.mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> TROUGH_ITEM = ITEMS.registerSimpleBlockItem("trough", TROUGH);

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, AlchyFarm.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, AlchyFarm.MODID);

    /** Honey's amber, for the fluid's tint in the world. */
    public static final int HONEY_COLOR = 0xFFE9A21B;

    /**
     * Honey as a fluid: what an apiary gives into a cask. Thick and slow. It is in the fluid tag c:honey, which is
     * where other mods' honey is too, so pipes, tanks and machines that take one take the other.
     */
    public static final DeferredHolder<FluidType, FluidType> HONEY_TYPE = FLUID_TYPES.register("honey",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.alchyfarm.honey")
                    .density(1450)
                    .viscosity(6000)
                    .isWaterLike(true) // (moved in as water is: without it NeoForge 26.1 gives a fluid NO movement at all, and one is stuck in it)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BOTTLE_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.HONEY_BLOCK_PLACE)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HONEY =
            FLUIDS.register("honey", () -> new BaseFlowingFluid.Source(honeyProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HONEY_FLOWING =
            FLUIDS.register("flowing_honey", () -> new BaseFlowingFluid.Flowing(honeyProperties()));

    public static final DeferredBlock<LiquidBlock> HONEY_BLOCK = BLOCKS.registerBlock("honey",
            p -> new LiquidBlock(HONEY.get(), p),
            p -> p.mapColor(MapColor.COLOR_ORANGE)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY));

    public static final DeferredItem<BucketItem> HONEY_BUCKET = ITEMS.registerItem("honey_bucket",
            p -> new BucketItem(HONEY.get(), p),
            p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    private static BaseFlowingFluid.Properties honeyProperties() {
        return new BaseFlowingFluid.Properties(HONEY_TYPE, HONEY, HONEY_FLOWING)
                .bucket(HONEY_BUCKET)
                .block(HONEY_BLOCK)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30);
    }

    /**
     * A fluid made by the rift, with its flowing form, its block in the world and its bucket. Each comes in the three
     * tiers of rifted things (1 Rifted, 2 Deep-Rifted, 3 Void-Rifted: a workstation's rift I-II, III-V, VI), and each
     * tier is a fluid of its own, so it needs a cask of its own. None is in a common tag (c:honey...): pipes and
     * machines do not take them for the plain kinds.
     */
    public record Brew(String name, int tier, int color, DeferredHolder<FluidType, FluidType> type,
                       DeferredHolder<Fluid, BaseFlowingFluid.Source> source, DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing,
                       DeferredBlock<LiquidBlock> block, DeferredItem<? extends Item> bucket) {
        public Fluid fluid() {
            return source.get();
        }

        /** The level (of upgrade/Rifted) of what is drawn from it: 2, 4 or 6. */
        public int itemLevel() {
            return tier * 2;
        }
    }

    /** The tier of rifted fluid a workstation makes at this rift level: 1 (I-II), 2 (III-V) or 3 (VI). */
    public static int brewTier(int riftLevel) {
        return riftLevel >= 6 ? 3 : riftLevel >= 3 ? 2 : 1;
    }

    /** The tier's word before the fluid's name, as rifted things are named (RiftMark.grade). */
    private static final String[] GRADES = {"rifted_", "deep_rifted_", "void_rifted_"};

    @SuppressWarnings("unchecked")
    private static Brew brew(String kind, int tier, int color, int density, int viscosity, int tickRate,
                             net.minecraft.sounds.SoundEvent fill, net.minecraft.sounds.SoundEvent empty,
                             java.util.function.BiFunction<Integer, Item.Properties, ? extends Item> drink) {
        String name = GRADES[tier - 1] + kind;
        int light = 4 + 2 * tier; // 6, 8, 10: the deeper, the brighter
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("block.alchyfarm." + name)
                .lightLevel(light)
                .density(density)
                .viscosity(viscosity)
                .isWaterLike(true) // (moved in as water is: without it NeoForge 26.1 gives a fluid NO movement at all, and one is stuck in it)
                .canConvertToSource(false)
                .sound(SoundActions.BUCKET_FILL, fill)
                .sound(SoundActions.BUCKET_EMPTY, empty)));
        // Its parts each need the others: they are read only once all are registered
        Object[] parts = new Object[4];
        java.util.function.Supplier<BaseFlowingFluid.Properties> properties = () -> new BaseFlowingFluid.Properties(type,
                (DeferredHolder<Fluid, BaseFlowingFluid.Source>) parts[0], (DeferredHolder<Fluid, BaseFlowingFluid.Flowing>) parts[1])
                .block((DeferredBlock<LiquidBlock>) parts[2])
                .bucket((DeferredItem<? extends Item>) parts[3])
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(tickRate);
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties.get()));
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(properties.get()));
        DeferredBlock<LiquidBlock> block = BLOCKS.registerBlock(name, p -> new LiquidBlock(source.get(), p),
                p -> p.mapColor(MapColor.COLOR_PURPLE).replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.DESTROY)
                        .noLootTable().liquid().sound(SoundType.EMPTY).lightLevel(state -> light));
        DeferredItem<? extends Item> bucket = ITEMS.registerItem(name + "_bucket",
                p -> drink != null ? drink.apply(tier, p) : new BucketItem(source.get(), p),
                p -> p.craftRemainder(Items.BUCKET).stacksTo(1).rarity(Rarity.EPIC));
        parts[0] = source;
        parts[1] = flowing;
        parts[2] = block;
        parts[3] = bucket;
        return new Brew(name, tier, color, type, source, flowing, block, bucket);
    }

    /**
     * Rifted honey, from an apiary working by magic: a glass bottle used on a cask of it fills with a rifted honey
     * bottle. Light violet to deep violet.
     */
    public static final List<Brew> RIFTED_HONEY = List.of(
            brew("honey", 1, 0xFFA765D8, 1450, 6000, 30, SoundEvents.BOTTLE_FILL, SoundEvents.HONEY_BLOCK_PLACE, null),
            brew("honey", 2, 0xFF7A3FB8, 1450, 6000, 30, SoundEvents.BOTTLE_FILL, SoundEvents.HONEY_BLOCK_PLACE, null),
            brew("honey", 3, 0xFF3F1670, 1450, 6000, 30, SoundEvents.BOTTLE_FILL, SoundEvents.HONEY_BLOCK_PLACE, null));

    /** Mushroom stew from a ranch's mooshrooms. A bowl held to a cask of it fills. */
    public static final int MUSHROOM_STEW_COLOR = 0xFFA8754A;
    public static final DeferredHolder<FluidType, FluidType> MUSHROOM_STEW_TYPE = FLUID_TYPES.register("mushroom_stew",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.alchyfarm.mushroom_stew")
                    .lightLevel(0)
                    .density(1100)
                    .viscosity(2000)
                    .isWaterLike(true) // (moved in as water is: without it a fluid gives no movement at all)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MUSHROOM_STEW =
            FLUIDS.register("mushroom_stew", () -> new BaseFlowingFluid.Source(mushroomStewProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> MUSHROOM_STEW_FLOWING =
            FLUIDS.register("flowing_mushroom_stew", () -> new BaseFlowingFluid.Flowing(mushroomStewProperties()));
    public static final DeferredBlock<LiquidBlock> MUSHROOM_STEW_BLOCK = BLOCKS.registerBlock("mushroom_stew",
            p -> new LiquidBlock(MUSHROOM_STEW.get(), p),
            p -> p.mapColor(MapColor.COLOR_BROWN).replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.DESTROY)
                    .noLootTable().liquid().sound(SoundType.EMPTY).lightLevel(state -> 0));
    public static final DeferredItem<BucketItem> MUSHROOM_STEW_BUCKET = ITEMS.registerItem("mushroom_stew_bucket",
            p -> new BucketItem(MUSHROOM_STEW.get(), p),
            p -> p.craftRemainder(Items.BUCKET).stacksTo(1).rarity(Rarity.COMMON));

    private static BaseFlowingFluid.Properties mushroomStewProperties() {
        return new BaseFlowingFluid.Properties(MUSHROOM_STEW_TYPE, MUSHROOM_STEW, MUSHROOM_STEW_FLOWING).bucket(MUSHROOM_STEW_BUCKET).block(MUSHROOM_STEW_BLOCK)
                .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(20);
    }

    /** Mushroom stew by the rift, from a ranch's mooshrooms: a bowl used on a cask of it is a rifted stew. */
    public static final List<Brew> RIFTED_STEW = List.of(
            brew("stew", 1, 0xFF8E5BB0, 1100, 2000, 20, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY, null),
            brew("stew", 2, 0xFF64348C, 1100, 2000, 20, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY, null),
            brew("stew", 3, 0xFF33124F, 1100, 2000, 20, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY, null));

    /** Milk by the rift, from a ranch: drunk, it takes away the harmful effects, and more the deeper it is (item/RiftedMilkItem). */
    public static final List<Brew> RIFTED_MILK = List.of(
            brew("milk", 1, 0xFFEDE4FF, 1100, 2000, 20, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY, com.lealex.alchyfarm.item.RiftedMilkItem::new),
            brew("milk", 2, 0xFFC9B2F2, 1100, 2000, 20, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY, com.lealex.alchyfarm.item.RiftedMilkItem::new),
            brew("milk", 3, 0xFF9A78D6, 1100, 2000, 20, SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY, com.lealex.alchyfarm.item.RiftedMilkItem::new));

    /** Every rifted fluid, for what is done to all of them (their look in the world). */
    public static List<Brew> brews() {
        return java.util.stream.Stream.of(RIFTED_HONEY, RIFTED_STEW, RIFTED_MILK).flatMap(List::stream).toList();
    }

    /** A workstation's storage for what it gives as a fluid (lava from a mine, honey from an apiary): one tank. */
    public static final DeferredBlock<CaskBlock> CASK = BLOCKS.registerBlock("cask",
            CaskBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> CASK_ITEM = ITEMS.registerSimpleBlockItem("cask", CASK);

    /** Stood in the middle of a mine's floor: with it the mine gives its springs (lava) to the casks on its stock or basket. */
    public static final DeferredBlock<PumpBlock> PUMP = BLOCKS.registerBlock("pump",
            PumpBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f, 6.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()
                    .lightLevel(state -> state.getValue(PumpBlock.PUMPING) ? 5 : 0));
    public static final DeferredItem<BlockItem> PUMP_ITEM = ITEMS.registerSimpleBlockItem("pump", PUMP);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CaskBlockEntity>> CASK_BE =
            BLOCK_ENTITY_TYPES.register("cask", () -> new BlockEntityType<>(CaskBlockEntity::new, false, CASK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlotStoreBlockEntity>> PLOT_STORE_BE =
            BLOCK_ENTITY_TYPES.register("plot_store",
                    () -> new BlockEntityType<>(PlotStoreBlockEntity::new, false, STOCK.get(), BASKET.get(), TROUGH.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<CropPlotMenu>> CROP_PLOT_MENU =
            MENU_TYPES.register("crop_plot", () -> IMenuTypeExtension.create(CropPlotMenu::new));


    /** For workstations whose screen is a list of kinds to turn on or off (the mushroom hollow, the pond's catch). */
    public static final DeferredHolder<MenuType<?>, MenuType<ChoiceMenu>> CHOICE_MENU =
            MENU_TYPES.register("choice", () -> IMenuTypeExtension.create((id, inventory, data) -> new ChoiceMenu(choiceMenu(), id, inventory, data)));

    private static MenuType<?> choiceMenu() {
        return CHOICE_MENU.get();
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropPlotBlockEntity>> CROP_PLOT_BE =
            BLOCK_ENTITY_TYPES.register("crop_plot",
                    () -> new BlockEntityType<>(CropPlotBlockEntity::new, false, CARVED_STAKE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RanchBlockEntity>> RANCH_BE =
            BLOCK_ENTITY_TYPES.register("ranch",
                    () -> new BlockEntityType<>(RanchBlockEntity::new, false, HITCHING_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrchardBlockEntity>> ORCHARD_BE =
            BLOCK_ENTITY_TYPES.register("orchard",
                    () -> new BlockEntityType<>(OrchardBlockEntity::new, false, WOODSMAN_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PondBlockEntity>> POND_BE =
            BLOCK_ENTITY_TYPES.register("pond",
                    () -> new BlockEntityType<>(PondBlockEntity::new, false, ANGLER_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MushroomFarmBlockEntity>> MUSHROOM_FARM_BE =
            BLOCK_ENTITY_TYPES.register("mushroom_farm",
                    () -> new BlockEntityType<>(MushroomFarmBlockEntity::new, false, FORAGER_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MineBlockEntity>> MINE_BE =
            BLOCK_ENTITY_TYPES.register("mine",
                    () -> new BlockEntityType<>(MineBlockEntity::new, false, MINER_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GildedGardenBlockEntity>> GILDED_GARDEN_BE =
            BLOCK_ENTITY_TYPES.register("gilded_garden",
                    () -> new BlockEntityType<>(GildedGardenBlockEntity::new, false, GILDER_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetherMineBlockEntity>> NETHER_MINE_BE =
            BLOCK_ENTITY_TYPES.register("nether_mine",
                    () -> new BlockEntityType<>(NetherMineBlockEntity::new, false, NETHER_MINER_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowerGardenBlockEntity>> FLOWER_GARDEN_BE =
            BLOCK_ENTITY_TYPES.register("flower_garden",
                    () -> new BlockEntityType<>(FlowerGardenBlockEntity::new, false, GARDENER_POST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ApiaryBlockEntity>> APIARY_BE =
            BLOCK_ENTITY_TYPES.register("apiary",
                    () -> new BlockEntityType<>(ApiaryBlockEntity::new, false, BEEKEEPER_POST.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.alchyfarm"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> CARVED_STAKE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
    }
}
