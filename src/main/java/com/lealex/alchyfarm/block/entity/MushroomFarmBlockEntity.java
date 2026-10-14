package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The mushroom hollow: a MACHINE, as AlchyMastery's are, not a field. It is a fixed structure
 * (data/alchyfarm/multiblock/mushroom_farm.json, checked by AlchyX): a small cave of rough stone, open in front,
 * over a bed of 3 by 3, with the forager's post in the middle of its front between a stock and a basket. When it
 * forms (the bed itself is free: its ground can be changed while the hollow stands, and only decides what grows),
 * the stone becomes machine shells that look like moss and mossy stone (named after the hollow), a light and a
 * spore blossom appear under its roof, and as for AlchyMastery's machines the stone is used up for good. Right-
 * clicking the post while it is not whole says what is missing. Once it is whole a bogged (the mushroom-grown
 * skeleton) comes through a rift, and goes when it breaks.
 *
 * It grows mushrooms by itself: every Config mushroomHollow.secondsPerMushroom one comes up on a free square of the
 * bed, as a real block, and the bogged picks them into the basket. Which kinds: those the stock holds a sample of
 * (item tag alchyfarm:mushroom_spawn: red and brown mushrooms, nether fungi, nether wart, and mods' through c:mushrooms; a
 * sample is never used up), or red and brown mushrooms when it holds none. The post's screen lists them, and each
 * can be turned off there. A kind only comes up on a bed it can stand on (nether fungi want nylium, nether wart soul sand). Bone meal in
 * the stock makes one come up on every free square at once, one piece each.
 *
 * Its errands' actions are the soul's gestures: HARVEST (picking), FERTILIZE (the bone meal).
 */
public class MushroomFarmBlockEntity extends SoulPlotBlockEntity implements MenuProvider, KindChoice {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "mushroom_farm");
    /** What a hollow can grow: items of this tag that place a block. */
    public static final TagKey<Item> SPAWN_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "mushroom_spawn"));

    private static final int CAST_REST = 100; // ticks between two casts of bone meal

    // The kinds it could grow now (those of the stock's samples, or the two wild ones), and those the player turned
    // off on the screen (both saved and synced: the screen reads them)
    private List<Item> offered = List.of();
    private final Set<Item> refused = new HashSet<>();

    // Not saved: both count again from a reload
    private long nextSprout = 0, nextCast = 0;

    public MushroomFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.MUSHROOM_FARM_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Mushroom hollow";
    }

    @Override
    protected String plotName() {
        return "mushroom hollow";
    }

    // "Enhanced shroom / flower is weird": nothing it picks comes back as a rifted thing. The rift only gives more
    @Override
    public boolean riftOnlyMore() {
        return true;
    }

    @Override
    public String soulLook() {
        return "bogged";
    }

    @Override
    protected int maxSize() {
        return 3;
    }

    // ---- The structure ----

    // AlchyX speaks for it: it says when the hollow forms and breaks, as for any machine
    @Override
    protected boolean quiet() {
        return false;
    }

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        if (nowFormed) rescan();
        else closeDown();
    }

    /** The bed is the 3 by 3 behind the post, as the pattern is written (front south: north of the post), turned with it. */
    @Override
    public boolean linkable() {
        return false; // one fixed structure: its ground and stores are where it is built
    }

    @Override
    protected boolean flatGround() {
        return false;
    }

    @Override
    protected Ground ground(ServerLevel server) {
        Rotation turn = rotation();
        List<BlockPos> bed = new ArrayList<>(9);
        for (int z = -3; z <= -1; z++) {
            for (int x = -1; x <= 1; x++) bed.add(new BlockPos(x, 0, z).rotate(turn));
        }
        bed.sort(Comparator.comparingInt((BlockPos square) -> square.getZ()).thenComparingInt(square -> square.getX()));
        List<BlockPos> beside = List.of(worldPosition.offset(new BlockPos(-1, 0, 0).rotate(turn)), worldPosition.offset(new BlockPos(1, 0, 0).rotate(turn)));
        return new Ground(bed, beside, null);
    }

    /** What a bed square can be (the pattern leaves the bed free, so it can be changed while the hollow stands). */
    public static final TagKey<Block> BEDS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "mushroom_beds"));

    // The bed is not part of the structure: the hollow says so itself when none of it is ground for a mushroom
    @Override
    protected @Nullable String lacks() {
        if (level == null) return null;
        for (BlockPos square : field()) {
            if (level.getBlockState(world(square).below()).is(BEDS_TAG)) return null;
        }
        return field().isEmpty() ? null : "No bed: lay spore loam or mycelium";
    }

    // Its mushrooms come by its own clock: the game's growth ticks would only make them spread over the bed
    @Override
    protected boolean riftGrows() {
        return false;
    }

    @Override
    protected String arrivedWords() {
        return "The hollow is whole: something has come through to tend its mushrooms.";
    }

    @Override
    protected String goneWords() {
        return "The mushroom hollow is broken: what tended it is gone.";
    }

    // ---- The errand's timing (both sides) ----

    @Override
    public int workTicks() {
        return action() == HARVEST ? Config.soulWorkTicks() * 9 / 5 : Config.soulWorkTicks();
    }

    @Override
    public int gesture() {
        return action();
    }

    @Override
    protected float firstAt() {
        return HARVEST_MOMENT;
    }

    @Override
    protected float secondAt() {
        return 0.6F;
    }

    // ---- The stock ----

    // The tag, and vanilla's own mushrooms and fungi whatever the tag says (a data pack may have emptied it)
    private boolean isSpawn(ItemStack stack) {
        return stack.getItem() instanceof BlockItem placed
                && (stack.is(SPAWN_TAG) || placed.getBlock() instanceof MushroomBlock || placed.getBlock() instanceof NetherFungusBlock
                || placed.getBlock() instanceof NetherWartBlock);
    }

    private static boolean isBoneMeal(ItemStack stack) {
        return stack.is(CropPlotBlockEntity.FERTILIZERS_TAG);
    }

    private int count(ServerLevel server, Predicate<ItemStack> what) {
        int count = 0;
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                if (what.test(stock.getItem(slot))) count += stock.getItem(slot).getCount();
            }
        }
        return count;
    }

    /** Takes one piece of the first thing in the stocks that passes the test. False if there is none. */
    private boolean takeOne(ServerLevel server, Predicate<ItemStack> what) {
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                ItemStack stack = stock.getItem(slot);
                if (stack.isEmpty() || !what.test(stack)) continue;
                stock.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    // ---- The kinds ----

    /** The kinds it could grow now, in the screen's order (both sides). */
    public List<Item> offered() {
        return offered;
    }

    /** True if this kind grows (the player has not turned it off). */
    public boolean grows(Item kind) {
        return !refused.contains(kind);
    }

    /** True if some square of the bed can take this kind (both sides: the screen warns when none can). */
    public boolean fits(Item kind) {
        if (level == null || !(kind instanceof BlockItem placed)) return false;
        BlockState mushroom = placed.getBlock().defaultBlockState();
        for (BlockPos square : field()) {
            if (mushroom.canSurvive(level, world(square))) return true;
        }
        return false;
    }

    // ---- KindChoice: what its screen reads ----

    @Override
    public boolean chosen(Item kind) {
        return grows(kind);
    }

    @Override
    public @Nullable String warning(Item kind) {
        return fits(kind) ? null : "wrong bed for it";
    }

    @Override
    public String chosenLabel() {
        return "Grown";
    }

    @Override
    public List<String> notes() {
        return List.of("A mushroom every " + Config.mushroomGrowSeconds() + " s, on its own.", "Other kinds: keep one in the stock.",
                "Each kind needs the right bed.", "Bone meal: fills the bed at once.");
    }

    /** The screen's click on a kind: off, or on again. */
    public void toggle(int index) {
        if (index < 0 || index >= offered.size()) return;
        Item kind = offered.get(index);
        if (!refused.remove(kind)) refused.add(kind);
        setChanged();
        syncToClients();
    }

    /** Reads the stock's samples again: one of each kind, or the two wild mushrooms when it holds none. */
    private void refreshOffered(ServerLevel server) {
        List<Item> kinds = new ArrayList<>(4);
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                ItemStack stack = stock.getItem(slot);
                if (isSpawn(stack) && !kinds.contains(stack.getItem())) kinds.add(stack.getItem());
            }
        }
        if (kinds.isEmpty()) {
            kinds.add(Items.RED_MUSHROOM);
            kinds.add(Items.BROWN_MUSHROOM);
        }
        if (kinds.equals(offered)) return;
        offered = List.copyOf(kinds);
        refused.retainAll(kinds); // a kind that comes back to the stock later starts on
        setChanged();
        syncToClients();
    }

    // ---- Growing, by itself ----

    private boolean isMushroom(BlockState state) {
        return !state.isAir() && isSpawn(new ItemStack(state.getBlock().asItem()));
    }

    private List<BlockPos> free(ServerLevel server) {
        List<BlockPos> free = new ArrayList<>(9);
        for (BlockPos square : field()) {
            if (server.getBlockState(world(square)).isAir()) free.add(square);
        }
        return free;
    }

    /** Up to this many mushrooms come up on free squares of the bed. Returns how many did. */
    private int sprout(ServerLevel server, int most, boolean mealed) {
        refreshOffered(server);
        List<BlockState> kinds = new ArrayList<>(offered.size());
        for (Item kind : offered) {
            if (grows(kind) && kind instanceof BlockItem placed) kinds.add(placed.getBlock().defaultBlockState());
        }
        List<BlockPos> free = free(server);
        int grown = 0;
        while (grown < most && !free.isEmpty() && !kinds.isEmpty()) {
            BlockPos pos = world(free.remove(server.getRandom().nextInt(free.size())));
            // One of the kinds this square's ground takes, picked at random
            List<BlockState> fit = new ArrayList<>(kinds.size());
            for (BlockState kind : kinds) {
                if (kind.canSurvive(server, pos)) fit.add(kind);
            }
            if (fit.isEmpty()) continue;
            BlockState comes = fit.get(server.getRandom().nextInt(fit.size()));
            if (comes.getBlock() instanceof NetherWartBlock) comes = comes.setValue(NetherWartBlock.AGE, 3); // up full grown, as a mushroom is
            server.setBlock(pos, comes, Block.UPDATE_ALL);
            if (mealed) server.levelEvent(1505, pos, 8); // vanilla's bone meal sparkles and sound
            else server.sendParticles(ParticleTypes.MYCELIUM, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 8, 0.25, 0.15, 0.25, 0.0);
            grown++;
        }
        if (grown > 0) {
            if (!mealed) playSound(worldPosition, SoundEvents.FUNGUS_PLACE, 0.6f, 0.8f);
            lookSoon();
        }
        return grown;
    }

    @Override
    protected void everyTick(ServerLevel server) {
        long now = server.getGameTime();
        if (nextSprout == 0) nextSprout = now + hasted(Config.mushroomGrowSeconds() * 20L);
        if (now < nextSprout) return;
        nextSprout = now + hasted(Config.mushroomGrowSeconds() * 20L);
        sprout(server, 1, false);
    }

    // ---- Work ----

    /**
     * The nearest mushroom of the bed to pick, if a basket has room. With none, bone meal in the stock and free
     * squares, a cast over the bed.
     */
    @Override
    protected void findWork(ServerLevel server, long now) {
        refreshOffered(server); // the stock's samples, so the screen and the bed follow what is put in at once
        BlockPos here = soulTo(), best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean anyFree = false;
        for (BlockPos square : field()) {
            BlockState state = server.getBlockState(world(square));
            if (state.isAir()) anyFree = true;
            if (!isMushroom(state) || square.distSqr(here) >= bestDistance) continue;
            best = square;
            bestDistance = square.distSqr(here);
        }
        if (best != null) {
            if (basketHasRoom(server)) go(best, best, HARVEST, now, true, false); // bent over it, on its square
        } else if (anyFree && now >= nextCast && (endless() || count(server, MushroomFarmBlockEntity::isBoneMeal) > 0)) {
            go(byThePost(), middle(), FERTILIZE, now, false, true);
        }
    }

    /** The picking: the mushroom, held up, then into the basket. */
    @Override
    protected void first(ServerLevel server) {
        BlockPos pos = world(soulTarget());
        BlockState state = server.getBlockState(pos);
        if (!isMushroom(state)) return;
        Item mushroom = state.getBlock().asItem();
        server.destroyBlock(pos, false);
        hold(mushroom);
        List<Config.Drop> set = Config.hollowHarvest(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (set == null) toBasket(server, new ItemStack(mushroom));
        else for (ItemStack given : Config.roll(set, server.getRandom())) toBasket(server, given);
    }

    /** The cast: one piece of bone meal for each free square of the bed, a mushroom on each at once. */
    @Override
    protected void second(ServerLevel server) {
        nextCast = server.getGameTime() + CAST_REST;
        int pieces = endless() ? free(server).size() : Math.min(free(server).size(), count(server, MushroomFarmBlockEntity::isBoneMeal));
        int grown = sprout(server, pieces, true);
        for (int i = 0; i < grown; i++) {
            if (!endless() && !spared(server)) takeOne(server, MushroomFarmBlockEntity::isBoneMeal);
        }
        if (grown > 0) nextSprout = server.getGameTime() + hasted(Config.mushroomGrowSeconds() * 20L);
    }

    // ---- Its screen: the kinds, each on or off ----

    @Override
    public void openGui(Player player) {
        if (!isFormed() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
            super.openGui(player); // not whole: AlchyX says what is missing
            return;
        }
        refreshOffered(server);
        serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchyfarm.mushroom_farm");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChoiceMenu(ModRegistries.CHOICE_MENU.get(), containerId, playerInventory, this);
    }

    // ---- Saving (also what clients get) ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("offered", BuiltInRegistries.ITEM.byNameCodec().listOf(), offered);
        if (!refused.isEmpty()) output.store("refused", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(refused));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        offered = List.copyOf(input.read("offered", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
        refused.clear();
        refused.addAll(input.read("refused", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
    }
}
