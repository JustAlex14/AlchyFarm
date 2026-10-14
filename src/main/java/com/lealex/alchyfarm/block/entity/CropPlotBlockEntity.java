package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.block.TrellisBlock;
import com.lealex.alchyfarm.menu.CropPlotMenu;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.common.ticket.AABBTicket;
import org.jspecify.annotations.Nullable;

/**
 * The crop plot: a carved stake whose fence closes around a field (see SoulPlotBlockEntity for the fence, the stores
 * and the soul). The field is the world's own: any soil, vanilla or modded, and REAL crops on it, so they grow by
 * the game's rules and anything else that grows crops works on them. The plot does not make crops grow faster by
 * itself.
 *
 * Its soul harvests a grown crop (a share of the seeds back to the stock, the rest to the basket), and sows an empty
 * square from the stock with the seed chosen for it on the stake's screen, or any seed the soil takes, standing on
 * the square before it. Squares the player keeps bare are never sown. Pumpkins and melons work too: their stems are
 * sown like any seed and left alone, and the fruit (block tag alchyfarm:plot_fruits) is taken from whatever square
 * of the field it grew on, which is what bare squares are for. Glow berries are not sown: the player stands a
 * trellis (block/TrellisBlock) on a square, and the soul sets a vine on it and picks its berries. With nothing else to do it goes back beside the stake and casts the stock's bone meal over
 * the whole field, one piece for each growing crop. While it is there, the field counts as watered, and the field's
 * farmland cannot be trampled (AlchyFarm's event listener asks isFieldSoil).
 *
 * Its errands' actions are the soul's gestures themselves: SOW, HARVEST, both, or FERTILIZE.
 */
public class CropPlotBlockEntity extends SoulPlotBlockEntity implements MenuProvider {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "crop_plot");
    /** What a plot sows: items of this tag that place a block (wheat seeds, carrots, potatoes, beetroot seeds...). */
    public static final TagKey<Item> SEEDS_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "plot_seeds"));
    /** What a plot casts over growing crops when its stock holds some (bone meal). */
    public static final TagKey<Item> FERTILIZERS_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "plot_fertilizers"));

    /**
     * What grows beside a stem and is harvested whole (pumpkin, melon): the soul takes it wherever it lies in the
     * field and leaves the stem.
     */
    public static final TagKey<Block> FRUITS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "plot_fruits"));

    private static final int SPREAD_REST = 100; // ticks between two casts of fertilizer
    private static final int FRUIT_TRIES = 160; // growth ticks a cast gives a grown stem to bear its fruit

    // The seed chosen for a square on the stake's screen (squares without one take whatever the stock holds)
    private final Map<BlockPos, Item> wanted = new HashMap<>();
    // Squares the player keeps bare: never sown (room for a stem's fruit, or a path)
    private final Set<BlockPos> bare = new HashSet<>();
    private int keepSeeds = 50;  // percent of harvested seeds that go back to the stock
    private int boneMeal = 0;    // fertilizer in the stocks (synced: shown on the screen)

    // Not saved
    private long nextSpread = 0;
    private @Nullable AABBTicket water; // NeoForge's "this farmland is near water" ticket over the field

    public CropPlotBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistries.CROP_PLOT_BE.get(), pos, state);
    }

    /** For workstations built on the crop plot (the orchard). */
    protected CropPlotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Crop plot";
    }

    @Override
    protected String plotName() {
        return "crop plot";
    }

    @Override
    protected int maxSize() {
        return Config.maxFieldSize();
    }

    public static boolean isSeed(ItemStack stack) {
        return stack.is(SEEDS_TAG) && stack.getItem() instanceof BlockItem;
    }

    /** What this plot plants and takes back as "seed" (an orchard says saplings). The crop plot: its seeds tag. */
    public boolean plants(ItemStack stack) {
        return isSeed(stack) || stack.is(net.minecraft.world.item.Items.APPLE); // (an apple is sown as the little apple tree)
    }

    /**
     * True if a square without a chosen seed is sown anyway (with whatever the stock holds). The crop plot: always.
     * An orchard leaves gaps.
     */
    public boolean sownByDefault(BlockPos square) {
        return true;
    }

    // The soul casts bone meal from on top of the post (square 0, 0), and stays there until work calls it down
    @Override
    protected boolean standsOnPost() {
        return true;
    }

    /** The tag of items this plot plants (the orchard: its saplings). */
    public TagKey<Item> plantTag() {
        return SEEDS_TAG;
    }

    /**
     * True if this plant can go on this square (both sides: the soul asks before sowing, the screen to warn): the
     * config's ground rules allow the block under it (Config ground.grounds), and the game lets the plant stand
     * there (its own canSurvive: light, free sides, water beside...).
     */
    public boolean suits(Item plant, BlockPos square) {
        BlockState planted = plantOf(plant);
        if (level == null || planted == null) return false;
        BlockPos pos = world(square);
        return Config.groundSuits(new ItemStack(plant), level.getBlockState(pos.below())) && planted.canSurvive(level, pos);
    }

    /** What this seed becomes in the ground: the block its item places (a gilded garden: the crop the config names for it). */
    protected @Nullable BlockState plantOf(Item seed) {
        if (seed == net.minecraft.world.item.Items.APPLE) return ModRegistries.APPLE_TREE.get().defaultBlockState();
        return seed instanceof BlockItem placed ? placed.getBlock().defaultBlockState() : null;
    }

    /** Puts the plant in the ground (a flower garden's tall flowers take two blocks). */
    protected void place(ServerLevel server, BlockPos pos, BlockState plant) {
        server.setBlock(pos, plant, Block.UPDATE_ALL);
    }

    /** True if the stock holds what sowing this seed costs besides the seed itself (nothing, but in a gilded garden). */
    protected boolean affords(ServerLevel server, Item seed) {
        return true;
    }

    /** Takes that price from the stock, as the seed goes in the ground. False if it is not there any more: not sown. */
    protected boolean pay(ServerLevel server, Item seed) {
        return true;
    }

    /** True if at least one thing this plot plants can go on this square. */
    public boolean suitsAny(BlockPos square) {
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(plantTag())) {
            if (suits(item.value(), square)) return true;
        }
        return false;
    }

    /** True if this square is water (a pond's): the screen draws it so. Nowhere else. */
    public boolean isWater(BlockPos square) {
        return false;
    }

    /** True if the field's soil is kept watered while a soul is here. */
    protected boolean waters() {
        return true;
    }

    // ---- The errand's timing (both sides) ----

    // A harvest takes longer: it bends, pulls, then holds the crop up and looks at it
    @Override
    public int workTicks() {
        int base = Config.soulWorkTicks();
        if ((action() & HARVEST) == 0) return base;
        return (action() & SOW) != 0 ? base * 13 / 5 : base * 9 / 5;
    }

    @Override
    public int gesture() {
        return action();
    }

    // The crop comes out of the ground as the hands start to rise
    @Override
    protected float firstAt() {
        return (action() & SOW) != 0 ? HARVEST_SHARE * HARVEST_MOMENT : HARVEST_MOMENT;
    }

    // The seed is in the ground, or the bone meal on the crops, in the middle of that gesture
    @Override
    protected float secondAt() {
        return (action() & HARVEST) != 0 ? HARVEST_SHARE + (1.0F - HARVEST_SHARE) * 0.6F : 0.6F;
    }

    // ---- What the screen reads (both sides) ----

    public @Nullable Item wanted(BlockPos square) {
        return wanted.get(square);
    }

    /** True if the player keeps this square bare (never sown). */
    public boolean bare(BlockPos square) {
        return bare.contains(square);
    }

    public int keepSeeds() {
        return keepSeeds;
    }

    public int boneMeal() {
        return boneMeal;
    }

    /** True if this world position is soil of the field (one below a field square). */
    public boolean isFieldSoil(BlockPos pos) {
        return pos.getY() == worldPosition.getY() - 1 && field().contains(square(pos));
    }

    // ---- The screen's clicks (server) ----

    /**
     * A click on a square of the field map. With a seed in hand: that seed is chosen for it. With anything else: its
     * choice is cleared, and a square that had none is kept bare, or sown again if it was kept bare.
     */
    public void choose(int index, ItemStack inHand) {
        if (index < 0 || index >= field().size()) return;
        BlockPos square = field().get(index);
        if (plants(inHand)) {
            wanted.put(square, inHand.getItem());
            bare.remove(square);
        } else if (wanted.remove(square) == null && !bare.remove(square)) {
            bare.add(square);
        }
        changed();
    }

    /** A right click on a square: what grows there now becomes its seed. */
    public void chooseGrowing(int index) {
        if (level == null || index < 0 || index >= field().size()) return;
        Item growing = level.getBlockState(world(field().get(index))).getBlock().asItem();
        if (!plants(new ItemStack(growing))) return;
        wanted.put(field().get(index), growing);
        bare.remove(field().get(index));
        changed();
    }

    public void setKeep(int percent) {
        keepSeeds = Math.clamp(percent, 0, 100);
        changed();
    }

    private void changed() {
        setChanged();
        syncToClients();
        lookSoon(); // a new choice may mean work
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!isFormed()) { // a workstation that is a structure, not built right yet: AlchyX says what is wrong
            super.openGui(player);
            return;
        }
        if (level instanceof ServerLevel server) refreshBoneMeal(survey(server)); // the screen shows it
        serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchyfarm.crop_plot");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CropPlotMenu(containerId, playerInventory, this);
    }

    // ---- The field ----

    @Override
    protected @Nullable String lacks() {
        if (stocks.isEmpty()) return "No stock: nothing to sow";
        if (baskets.isEmpty()) return "No basket: no harvest";
        return null;
    }

    @Override
    protected void groundChanged(ServerLevel server) {
        // A fence opened for a moment keeps its choices: they are sorted out when a field is closed in again
        if (!field().isEmpty()) {
            Set<BlockPos> squares = new HashSet<>(field());
            wanted.keySet().retainAll(squares);
            bare.retainAll(squares);
        }
        water(server);
    }

    /**
     * While a soul works the field its soil counts as near water: NeoForge's farmland ticket over the field (farmland
     * asks for it on its own random ticks, so nothing here ticks for it). The ticket is not saved and goes with its
     * chunk: it is put back when the field is found again, and whenever the soul looks for work.
     */
    private void water(ServerLevel server) {
        List<BlockPos> field = field();
        if (!waters() || !hasSoul() || field.isEmpty()) {
            if (water != null) water.invalidate();
            water = null;
            return;
        }
        BlockPos first = field.getFirst();
        int minX = first.getX(), maxX = minX, minZ = first.getZ(), maxZ = minZ;
        for (BlockPos square : field) {
            minX = Math.min(minX, square.getX());
            maxX = Math.max(maxX, square.getX());
            minZ = Math.min(minZ, square.getZ());
            maxZ = Math.max(maxZ, square.getZ());
        }
        BlockPos at = worldPosition;
        AABB box = new AABB(at.getX() + minX, at.getY() - 2, at.getZ() + minZ, at.getX() + maxX + 1, at.getY() + 1, at.getZ() + maxZ + 1);
        if (water != null && water.isValid() && water.axisAlignedBB.equals(box)) return;
        if (water != null) water.invalidate();
        water = FarmlandWaterManager.addAABBTicket(server, box);
        // Wet at once, without waiting for each square's random tick
        for (BlockPos square : field) {
            BlockPos pos = world(square).below();
            BlockState soil = server.getBlockState(pos);
            if (soil.hasProperty(BlockStateProperties.MOISTURE) && soil.getValue(BlockStateProperties.MOISTURE) < 7) {
                server.setBlock(pos, soil.setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (water != null) water.invalidate();
        water = null;
    }

    // ---- The stocks, read once per look ----

    protected record Slot(Container stock, int slot) {}

    protected record Seed(Item item, BlockState plant, Slot slot) {}

    /** What the stocks hold that the soul can use: one slot per kind of seed, one of fertilizer, and how much of it. */
    protected record Survey(List<Seed> seeds, @Nullable Slot fertilizer, int fertilizerCount) {}

    private @Nullable Survey surveyed; // the last survey, good while no stock changed and its stocks are the same
    private long surveyedAt = -1;
    private List<BlockPos> surveyedStocks = List.of();

    /**
     * What the stocks hold, read once and kept until a stock changes (PlotStoreBlockEntity.stockChanges) or the
     * plot's stocks do: at the top a look for work asks dozens of times in one tick.
     */
    protected Survey survey(ServerLevel server) {
        if (surveyed != null && surveyedAt == PlotStoreBlockEntity.stockChanges() && surveyedStocks == stocks) return surveyed;
        surveyedAt = PlotStoreBlockEntity.stockChanges();
        surveyedStocks = stocks;
        return surveyed = readStocks(server);
    }

    private Survey readStocks(ServerLevel server) {
        List<Seed> seeds = new ArrayList<>(4);
        Slot fertilizer = null;
        int count = 0;
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                ItemStack stack = stock.getItem(slot);
                if (stack.isEmpty()) continue;
                if (stack.is(FERTILIZERS_TAG)) {
                    count += stack.getCount();
                    if (fertilizer == null) fertilizer = new Slot(stock, slot);
                } else if (plants(stack)) {
                    Item item = stack.getItem();
                    boolean known = false;
                    for (Seed seed : seeds) known |= seed.item() == item;
                    BlockState plant = plantOf(item);
                    if (!known && plant != null) seeds.add(new Seed(item, plant, new Slot(stock, slot)));
                }
            }
        }
        return new Survey(seeds, fertilizer, count);
    }

    protected void refreshBoneMeal(Survey survey) {
        if (survey.fertilizerCount() == boneMeal) return;
        boneMeal = survey.fertilizerCount();
        syncToClients();
    }

    /** A seed for this square: the one chosen for it, or any the soil under it takes. Null if none. */
    protected @Nullable Seed seedFor(ServerLevel server, Survey survey, BlockPos square, BlockPos pos) {
        Item chosen = wanted.get(square);
        for (Seed seed : survey.seeds()) {
            if (chosen != null && seed.item() != chosen) continue;
            if (onTrellis(seed.item())) continue; // never in the soil: only on a trellis
            if (!affords(server, seed.item())) continue;
            if (suits(seed.item(), square)) return seed; // the ground decides
        }
        return null;
    }

    /** True for what is set on a trellis instead of sown (glow berries). */
    private static boolean onTrellis(Item item) {
        return item.builtInRegistryHolder().is(TrellisBlock.PLANTS_TAG);
    }

    /** Something in the stocks to set on a trellis, or null. */
    private static @Nullable Seed trellisSeed(Survey survey) {
        for (Seed seed : survey.seeds()) {
            if (onTrellis(seed.item())) return seed;
        }
        return null;
    }

    // ---- Work ----

    /**
     * The nearest square that needs the soul: a grown crop (if a basket has room), or bare soil it has a seed for.
     * With neither, and fertilizer in stock, a cast over the field.
     */
    @Override
    protected void findWork(ServerLevel server, long now) {
        water(server); // the ticket goes with its chunk: back if it is gone (a few comparisons otherwise)
        Survey survey = survey(server);
        refreshBoneMeal(survey);
        BlockPos here = soulTo(), best = null;
        int bestAction = 0;
        double bestDistance = Double.MAX_VALUE;
        boolean anyGrowing = false, roomKnown = false, room = false;
        for (BlockPos square : field()) {
            BlockPos pos = world(square);
            BlockState state = server.getBlockState(pos);
            int needed = 0;
            if (state.getBlock() instanceof TrellisBlock) { // frames for what hangs, stacked or not: set, left to bear, picked
                boolean bareFrame = false, berries = false;
                for (int up = 0; up < TrellisBlock.MAX_STACK; up++) {
                    BlockState frame = server.getBlockState(pos.above(up));
                    if (!(frame.getBlock() instanceof TrellisBlock)) break;
                    int age = frame.getValue(TrellisBlock.AGE);
                    if (age == 0) bareFrame = true;
                    else if (age == TrellisBlock.VINE) anyGrowing = true;
                    else berries = true;
                }
                boolean sets = bareFrame && trellisSeed(survey) != null;
                if ((berries || sets) && square.distSqr(here) < bestDistance) {
                    if (berries) {
                        if (!roomKnown) {
                            roomKnown = true;
                            room = basketHasRoom(server);
                        }
                        if (room) needed = HARVEST | (sets ? SOW : 0);
                    }
                    if (needed == 0 && sets) needed = SOW;
                }
            } else if (state.isAir()) {
                if (!survey.seeds().isEmpty() && !bare.contains(square) && (wanted.containsKey(square) || sownByDefault(square))
                        && square.distSqr(here) < bestDistance
                        && seedFor(server, survey, square, pos) != null) needed = SOW;
            } else if (state.is(FRUITS_TAG)) { // a pumpkin, a melon: taken whole, its stem stays where it is
                if (square.distSqr(here) < bestDistance) {
                    if (roomFor(server, state)) needed = HARVEST | (!bare.contains(square) && seedFor(server, survey, square, pos) != null ? SOW : 0);
                }
            } else if (isCrop(state)) {
                if (!isGrown(state)) {
                    anyGrowing = true;
                } else if (square.distSqr(here) < bestDistance) {
                    if (roomFor(server, state)) needed = HARVEST | (!bare.contains(square) && seedFor(server, survey, square, pos) != null ? SOW : 0);
                }
            }
            if (stemWantsMeal(server, pos, state)) anyGrowing = true; // bone meal is for stems too
            if (needed == 0) continue;
            best = square;
            bestAction = needed;
            bestDistance = square.distSqr(here);
        }
        if (best != null) {
            // It sows from the square before, and harvests standing on the square (bent over the plant)
            go(bestAction == SOW ? besides(best) : best, best, bestAction, now, (bestAction & HARVEST) != 0, (bestAction & SOW) != 0);
        } else if (anyGrowing && (survey.fertilizer() != null || endless()) && now >= nextSpread) {
            // Bone meal is cast over the whole field: from on top of the stake, toward the middle
            go(BlockPos.ZERO, middle(), FERTILIZE, now, false, true);
        }
    }

    /**
     * What one block of the harvest gives: what the config says for it (cropPlot.harvest, by the block's id), or what
     * it drops when broken.
     */
    protected List<ItemStack> harvestOf(ServerLevel server, BlockPos pos, BlockState state) {
        List<Config.Drop> set = configured(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return set != null ? Config.roll(set, server.getRandom()) : Block.getDrops(state, server, pos, null);
    }

    /** The config's word on what this block gives here, or null (an orchard reads its own list). */
    protected @Nullable List<Config.Drop> configured(Identifier block) {
        return Config.cropHarvest(block);
    }

    // With nothing to do it goes to look at something still growing, when there is some
    @Override
    protected @Nullable BlockPos strollTo(ServerLevel server) {
        List<BlockPos> growing = hungry(server);
        if (growing.isEmpty()) return super.strollTo(server);
        BlockPos square = square(growing.get(server.getRandom().nextInt(growing.size())));
        return field().contains(square) ? square : super.strollTo(server);
    }

    // Its care does the plant good: what one piece of bone meal would, for nothing (Config souls.idleCare)
    @Override
    protected void tended(ServerLevel server, BlockPos square) {
        if (!Config.soulIdleCare()) return;
        for (BlockPos pos : hungry(server)) {
            if (!square(pos).equals(square)) continue;
            feed(server, pos, server.getBlockState(pos));
            server.levelEvent(1505, pos, 6); // vanilla's bone meal sparkles and sound
            return;
        }
    }

    /** The errand's harvest: the grown crop gives what it drops, and the soul holds the best of it up. */
    @Override
    protected void first(ServerLevel server) {
        BlockPos pos = world(soulTarget());
        BlockState state = server.getBlockState(pos);
        if (state.getBlock() instanceof TrellisBlock) { // the berries of every frame of the stack: the vines stay
            ItemStack berries = ItemStack.EMPTY;
            boolean picked = false;
            for (int up = 0; up < TrellisBlock.MAX_STACK; up++) {
                BlockState frame = server.getBlockState(pos.above(up));
                if (!(frame.getBlock() instanceof TrellisBlock)) break;
                if (frame.getValue(TrellisBlock.AGE) != TrellisBlock.BERRIES) continue;
                List<Config.Drop> set = configured(BuiltInRegistries.BLOCK.getKey(frame.getBlock()));
                for (ItemStack more : set != null ? Config.roll(set, server.getRandom()) : List.of(TrellisBlock.berries(server.getRandom()))) {
                    if (berries.isEmpty()) berries = more;
                    else if (ItemStack.isSameItem(berries, more)) berries.grow(more.getCount());
                    else gather(server, more);
                }
                server.setBlock(pos.above(up), frame.setValue(TrellisBlock.AGE, TrellisBlock.VINE), Block.UPDATE_CLIENTS);
                picked = true;
            }
            if (!picked) return;
            playSound(pos, SoundEvents.CAVE_VINES_PICK_BERRIES, 1.0f, 1.0f);
            if (!berries.isEmpty()) {
                hold(berries.getItem());
                gather(server, berries);
            }
            return;
        }
        if (!isGrown(state) && !state.is(FRUITS_TAG)) return;
        List<ItemStack> drops = harvestOf(server, pos, state);
        // A fruit's stem starts over once its fruit is taken: the stems that hold this one are noted first
        List<BlockPos> stems = new ArrayList<>(1);
        if (state.is(FRUITS_TAG)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = pos.relative(side);
                BlockState holder = server.getBlockState(beside);
                if (holder.getBlock() instanceof AttachedStemBlock && holder.getValue(HorizontalDirectionalBlock.FACING) == side.getOpposite()) stems.add(beside);
            }
        }
        server.destroyBlock(pos, false);
        for (BlockPos stem : stems) { // the game has just made it a plain grown stem again: back to a sprout
            BlockState freed = server.getBlockState(stem);
            if (freed.getBlock() instanceof StemBlock) server.setBlock(stem, freed.setValue(StemBlock.AGE, 0), Block.UPDATE_CLIENTS);
        }
        // What it shows: the crop itself rather than its seeds, when the plant gives both (wheat, beetroot)
        Item shown = null;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (shown == null || !plants(drop)) shown = drop.getItem();
            if (!plants(drop)) break;
        }
        hold(shown);
        for (ItemStack drop : drops) gather(server, drop);
    }

    @Override
    protected void second(ServerLevel server) {
        if ((action() & FERTILIZE) != 0) fertilize(server);
        else sow(server);
    }

    /** The errand's sowing, on the square the soul faces. */
    private void sow(ServerLevel server) {
        BlockPos pos = world(soulTarget());
        BlockState there = server.getBlockState(pos);
        if (there.getBlock() instanceof TrellisBlock) { // a vine set on every bare frame of the stack, as far as the stock goes
            for (int up = 0; up < TrellisBlock.MAX_STACK; up++) {
                BlockState frame = server.getBlockState(pos.above(up));
                if (!(frame.getBlock() instanceof TrellisBlock)) break;
                if (frame.getValue(TrellisBlock.AGE) != 0) continue;
                Seed vine = trellisSeed(survey(server));
                if (vine == null) return;
                if (!spared(server)) vine.slot().stock().removeItem(vine.slot().slot(), 1);
                server.setBlock(pos.above(up), frame.setValue(TrellisBlock.AGE, TrellisBlock.VINE), Block.UPDATE_CLIENTS);
                playSound(pos.above(up), SoundEvents.CAVE_VINES_PLACE, 1.0f, 1.0f);
            }
            return;
        }
        if (!there.isAir() || bare.contains(soulTarget())) return;
        Seed seed = seedFor(server, survey(server), soulTarget(), pos);
        if (seed == null || !pay(server, seed.item())) return;
        if (!spared(server)) seed.slot().stock().removeItem(seed.slot().slot(), 1);
        place(server, pos, seed.plant());
        playSound(pos, SoundEvents.CROP_PLANTED, 1.0f, 1.0f);
    }

    /**
     * Fertilizer cast over the whole field: every growing crop gets what bone meal does by hand. One piece covers
     * Config.cropsPerBoneMeal() crops (1 by default: one crop, one piece; three crops, three pieces), so a cast
     * takes as many pieces as the field needs (or as the stocks have, and then the crops furthest behind come first).
     */
    /** True once the rest after the last cast of fertilizer is over. */
    protected boolean castDue(long now) {
        return now >= nextSpread;
    }

    /** What a cast of fertilizer is for: the growing crops, and the stems it does something for. */
    protected List<BlockPos> hungry(ServerLevel server) {
        List<BlockPos> crops = new ArrayList<>();
        for (BlockPos square : field()) {
            BlockPos pos = world(square);
            BlockState state = server.getBlockState(pos);
            if ((isCrop(state) && !isGrown(state)) || stemWantsMeal(server, pos, state)) crops.add(pos);
            for (int up = 0; up < TrellisBlock.MAX_STACK; up++) { // every frame of a stack whose vine has no berries
                BlockState frame = up == 0 ? state : server.getBlockState(pos.above(up));
                if (!(frame.getBlock() instanceof TrellisBlock)) break;
                if (frame.getValue(TrellisBlock.AGE) == TrellisBlock.VINE) crops.add(pos.above(up));
            }
        }
        return crops;
    }

    private void fertilize(ServerLevel server) {
        nextSpread = server.getGameTime() + SPREAD_REST;
        List<BlockPos> crops = hungry(server);
        if (crops.isEmpty()) return;
        int each = Config.cropsPerBoneMeal(), needed = (crops.size() + each - 1) / each, taken = 0;
        Item cast = null;
        if (endless()) { // the Quality Work stamp: all the bone meal the field needs, and none taken
            taken = needed;
            cast = net.minecraft.world.item.Items.BONE_MEAL;
        }
        while (taken < needed) {
            Slot source = survey(server).fertilizer();
            if (source == null) break;
            if (cast == null) cast = source.stock().getItem(source.slot()).getItem();
            int take = Math.min(needed - taken, source.stock().getItem(source.slot()).getCount());
            source.stock().removeItem(source.slot(), unspared(server, take)); // (the thrift stamp spares some)
            taken += take;
        }
        if (taken == 0) return;
        crops.sort(Comparator.comparingInt(pos -> stage(server.getBlockState(pos))));
        int covered = Math.min(crops.size(), taken * each);
        // The cast itself: handfuls of it flung from the soul's hand toward the field, in a fan
        if (cast != null) {
            BlockPos to = soulTo(), target = soulTarget();
            // (a soul standing on its post is a block higher)
            double handX = worldPosition.getX() + to.getX() + 0.5, handY = worldPosition.getY() + (to.equals(BlockPos.ZERO) ? 2.3 : 1.3),
                    handZ = worldPosition.getZ() + to.getZ() + 0.5;
            double aimX = target.getX() - to.getX(), aimZ = target.getZ() - to.getZ(), far = Math.sqrt(aimX * aimX + aimZ * aimZ);
            if (far < 0.01) {
                aimX = 0;
                aimZ = 0;
            } else {
                aimX /= far;
                aimZ /= far;
            }
            // (by magic nothing is thrown by hand: the plants just take it, below)
            ItemParticleOption crumbs = new ItemParticleOption(ParticleTypes.ITEM, cast);
            for (int i = 0; i < (magic() ? 0 : 28); i++) {
                double spread = (server.getRandom().nextDouble() - 0.5) * 1.4; // across the aim
                double x = aimX - aimZ * spread, z = aimZ + aimX * spread;
                // count 0: the three "offsets" are the direction, the last number the speed
                server.sendParticles(crumbs, handX + aimX * 0.4, handY, handZ + aimZ * 0.4, 0,
                        x, 0.35 + server.getRandom().nextDouble() * 0.5, z, 0.22 + server.getRandom().nextDouble() * 0.22);
            }
            if (!magic()) server.sendParticles(ParticleTypes.WHITE_ASH, handX + aimX, handY + 0.3, handZ + aimZ, 30, 0.8, 0.4, 0.8, 0.01);
        }
        for (int i = 0; i < covered; i++) {
            BlockPos pos = crops.get(i);
            BlockState state = server.getBlockState(pos);
            feed(server, pos, state);
            server.levelEvent(1505, pos, 8); // vanilla's bone meal sparkles and sound
            // and the dust settling on the crop
            server.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.0);
        }
        refreshBoneMeal(survey(server));
    }

    /** What one piece of a cast does to one of the plants it is for. */
    protected void feed(ServerLevel server, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof StemBlock && state.getValue(StemBlock.AGE) >= StemBlock.MAX_AGE) {
            // A grown stem: its fruit. The game only lets it try now and then, with one chance in several, toward
            // one side picked at random: left to itself that takes many minutes. Here it tries until it has one.
            for (int tries = 0; tries < FRUIT_TRIES && server.getBlockState(pos) == state; tries++) {
                state.randomTick(server, pos, server.getRandom());
            }
        } else if (state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(server, pos, state)) {
            plant.performBonemeal(server, server.getRandom(), pos, state); // what bone meal does by hand
        } else {
            state.randomTick(server, pos, server.getRandom()); // crops bone meal does not work on: one growth tick
        }
    }

    private static int stage(BlockState state) {
        if (state.getBlock() instanceof StemBlock) return state.getValue(StemBlock.AGE);
        return state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : 0;
    }

    /** One harvested stack: of seeds, each has keepSeeds chances in a hundred to go back to a stock. The rest: a basket. */
    protected void gather(ServerLevel server, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (plants(stack) && keepSeeds > 0) {
            int kept = 0;
            for (int i = 0; i < stack.getCount(); i++) {
                if (server.getRandom().nextInt(100) < keepSeeds) kept++;
            }
            if (kept > 0) {
                ItemStack back = put(server, stocks, stack.split(kept));
                if (!back.isEmpty()) stack.grow(back.getCount()); // the stocks are full: with the rest
            }
        }
        if (voidTier() >= 1 && surplus(stack)) return; // the Void stamp: leftover seeds are destroyed, not kept
        toBasket(server, stack);
    }

    /**
     * True for what a harvest gives that is only good for sowing again (wheat seeds, beetroot seeds, stem seeds):
     * what this plot plants and is not food. The Void stamp destroys what of it is not kept for the stock.
     */
    protected boolean surplus(ItemStack stack) {
        return plants(stack) && stack.get(net.minecraft.core.component.DataComponents.FOOD) == null;
    }

    /**
     * A pumpkin or melon stem bone meal does something for: one still growing, or a grown one with room for its
     * fruit (a free square beside it, on soil), which the cast then makes it bear.
     */
    private static boolean stemWantsMeal(ServerLevel server, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StemBlock)) return false;
        if (state.getValue(StemBlock.AGE) < StemBlock.MAX_AGE) return true;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = pos.relative(side);
            if (!server.getBlockState(beside).isAir()) continue;
            BlockState under = server.getBlockState(beside.below());
            if (under.is(Blocks.FARMLAND) || under.is(BlockTags.DIRT)) return true;
        }
        return false;
    }

    private static boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock || (state.is(BlockTags.CROPS) && !(state.getBlock() instanceof StemBlock));
    }

    /** A crop at its last age: vanilla crops, and any block of the crops tag with an "age" (stems are left alone). */
    private static boolean isGrown(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof StemBlock || !state.is(BlockTags.CROPS)) return false;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && age.getName().equals("age")) {
                int last = 0;
                for (int value : age.getPossibleValues()) last = Math.max(last, value);
                return state.getValue(age) == last;
            }
        }
        return false;
    }

    // ---- Saving (also what clients get) ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!wanted.isEmpty()) {
            int[] chosen = new int[wanted.size()];
            List<Item> seeds = new ArrayList<>(wanted.size());
            int i = 0;
            for (Map.Entry<BlockPos, Item> entry : wanted.entrySet()) {
                chosen[i++] = pack(entry.getKey());
                seeds.add(entry.getValue());
            }
            output.putIntArray("chosenSquares", chosen);
            output.store("chosenSeeds", BuiltInRegistries.ITEM.byNameCodec().listOf(), seeds);
        }
        if (!bare.isEmpty()) {
            int[] kept = new int[bare.size()];
            int i = 0;
            for (BlockPos square : bare) kept[i++] = pack(square);
            output.putIntArray("bareSquares", kept);
        }
        output.putInt("keepSeeds", keepSeeds);
        output.putInt("boneMeal", boneMeal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        wanted.clear();
        int[] chosen = input.getIntArray("chosenSquares").orElse(new int[0]);
        List<Item> seeds = input.read("chosenSeeds", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(chosen.length, seeds.size()); i++) wanted.put(unpack(chosen[i]), seeds.get(i));
        bare.clear();
        for (int packed : input.getIntArray("bareSquares").orElse(new int[0])) bare.add(unpack(packed));
        keepSeeds = input.getIntOr("keepSeeds", 50);
        boneMeal = input.getIntOr("boneMeal", 0);
    }
}
