package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * The orchard: a woodsman's post whose fence closes around a grove (see SoulPlotBlockEntity for the fence, the stores
 * and the soul). It is a crop plot for trees, and shares the crop plot's map, choices, stock reading and bone meal
 * cast by extending it: what differs is here.
 *
 * The trees are REAL: the soul plants saplings from the stock on the ground of the grove, they grow by the game's
 * rules (so any sapling works, and so does bone meal), and their crowns hang over the fence as trees do. A square
 * whose foot is a log is a grown tree: the soul goes to it and chops, and the tree comes down from the top, a few
 * blocks each tick, logs and leaves giving what they drop. Saplings go back to the stock by the "kept" share, the
 * rest to the basket. Trees that need four saplings (dark oak, the giant ones) are not planted in this version.
 *
 * Column plants (block tag alchyfarm:orchard_stalks: cactus, bamboo, and the pond's sugar cane) are planted the same
 * way, on whatever ground they take. Once three blocks tall they are cut above the foot, which stays and grows
 * again. Bushes (block tag alchyfarm:orchard_bushes: sweet berries) are planted the same way too, and picked when
 * ripe (HARVEST): the bush stays.
 *
 * Squares without a chosen sapling are planted like the dark squares of a chessboard, so every tree has a free
 * square on each side. A chosen square is always planted, a bare one never. The soul works from the free squares
 * beside the trees, and with nothing to do it goes back up on its post, where it also casts the bone meal from.
 *
 * Its errands' actions are the soul's gestures: SOW (planting), CHOP (felling), FERTILIZE (bone meal on the saplings).
 */
public class OrchardBlockEntity extends CropPlotBlockEntity {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "orchard");
    /** What an orchard plants: items of this tag that place a block (saplings that grow alone). */
    public static final TagKey<Item> SAPLINGS_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "orchard_saplings"));

    /**
     * Plants that grow as a column and are cut above their foot, which stays and grows again (cactus, sugar cane,
     * bamboo). Planted like saplings, from the same tag of items.
     */
    public static final TagKey<Block> STALKS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "orchard_stalks"));
    /**
     * Bushes that are picked and stay (sweet berries): a block of this tag with an "age" at its last value is ripe,
     * gives what it drops, and goes back to its second age. Planted like saplings, from the same tag of items.
     */
    public static final TagKey<Block> BUSHES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "orchard_bushes"));
    /** What grows on top of a column plant and ends its growth (a cactus's flower): the column is cut for it, whatever its height. */
    public static final TagKey<Block> TOPS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "stalk_tops"));
    private static final int STALK_HEIGHT = 3, MAX_STALK = 24; // cut once this tall, and no more than that many blocks

    // A tree is what hangs together from its foot: logs touching logs, then leaves within a few steps of them
    private static final int MAX_LOGS = 256, MAX_LEAVES = 700, LEAF_STEPS = 4, REACH = 7, HEIGHT = 40;
    private static final int FELL_TICKS = 24; // a tree comes down in about this long

    // The blocks of the tree coming down, highest first (server only: a tree cut short by a reload keeps its foot,
    // and is felled again)
    private final ArrayDeque<BlockPos> falling = new ArrayDeque<>();
    private int perTick = 1;
    private int minX, minZ; // the grove's corner, for the one-in-two planting
    private @Nullable List<BlockPos> cornerOf; // the field it was worked out for

    public OrchardBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistries.ORCHARD_BE.get(), pos, state);
    }

    /** For workstations built on the orchard (the pond). */
    protected OrchardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Orchard";
    }

    @Override
    protected String plotName() {
        return "orchard";
    }

    @Override
    public String soulLook() {
        return "fletcher";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchyfarm.orchard");
    }

    @Override
    public TagKey<Item> plantTag() {
        return SAPLINGS_TAG;
    }

    @Override
    public boolean plants(ItemStack stack) {
        return stack.is(SAPLINGS_TAG) && stack.getItem() instanceof BlockItem;
    }

    // Of what an orchard plants, only saplings are leftovers: cactus, bamboo and berries are its harvest
    @Override
    protected boolean surplus(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.SAPLINGS);
    }

    // Trees want no watered farmland, and there is none to trample
    @Override
    protected boolean waters() {
        return false;
    }

    @Override
    public boolean isFieldSoil(BlockPos pos) {
        return false;
    }

    @Override
    protected @Nullable String lacks() {
        if (stocks.isEmpty()) return "No stock: nothing to plant";
        if (baskets.isEmpty()) return "No basket: no felling";
        return null;
    }

    private void corner() {
        cornerOf = field();
        minX = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        for (BlockPos square : field()) {
            minX = Math.min(minX, square.getX());
            minZ = Math.min(minZ, square.getZ());
        }
    }

    // Like the dark squares of a chessboard, counted from the grove's corner: a 3 x 3 grove gets its four corners and
    // its middle. Every tree then has a free square on each of its four sides, where the soul stands to fell it.
    @Override
    public boolean sownByDefault(BlockPos square) {
        if (cornerOf != field()) corner(); // the field is a new list whenever it changes, on both sides
        return Math.floorMod(square.getX() - minX + square.getZ() - minZ, 2) == 0;
    }

    /** True if a tree is to stand on this square: the soul keeps off it. */
    private boolean treeGoes(BlockPos square) {
        return !bare(square) && (wanted(square) != null || sownByDefault(square));
    }

    // The soul arrives, rests and casts bone meal on top of the post itself: the one place of an orchard where no
    // tree, trunk or branch ever is (on the ground a tree can grow around it, and it is seen working from inside)
    @Override
    protected boolean standsOnPost() {
        return true;
    }

    @Override
    protected BlockPos byThePost() {
        return BlockPos.ZERO;
    }

    // To work on a square it stands beside it, on a square no tree is to stand on when there is one
    @Override
    protected BlockPos besides(BlockPos square) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos beside = square.relative(side);
            if (!field().contains(beside) || treeGoes(beside)) continue;
            double distance = beside.distSqr(soulTo());
            if (distance < bestDistance) {
                best = beside;
                bestDistance = distance;
            }
        }
        return best != null ? best : super.besides(square);
    }

    // What a block gives is read from orchard.harvest
    @Override
    protected @Nullable List<Config.Drop> configured(Identifier block) {
        return Config.orchardHarvest(block);
    }

    // ---- The errand's timing (both sides) ----

    @Override
    public int workTicks() {
        return action() == CHOP ? Config.soulWorkTicks() * 2 : action() == HARVEST ? Config.soulWorkTicks() * 9 / 5 : Config.soulWorkTicks();
    }

    // Three swings: the second bites, the third brings it down. Picking: as the hands come up.
    @Override
    protected float firstAt() {
        return action() == HARVEST ? HARVEST_MOMENT : 0.54F; // (the second swing lands)
    }

    @Override
    protected float secondAt() {
        return action() == CHOP ? 0.87F : 0.6F; // (the third swing lands)
    }

    // ---- Work ----

    private static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    /** A column plant grown tall enough to cut: its foot, with as much of itself above as STALK_HEIGHT asks. */
    /** False for a grown thing the player has it leave standing (the pond: sugar cane turned off on its screen). */
    protected boolean fells(BlockState state) {
        return true;
    }

    private static boolean tallStalk(ServerLevel server, BlockPos pos, BlockState state) {
        if (!state.is(STALKS_TAG)) return false;
        for (int up = 1; up < STALK_HEIGHT; up++) {
            BlockState above = server.getBlockState(pos.above(up));
            if (above.is(TOPS_TAG)) return true; // in flower: a cactus with a flower on it grows no taller, it is cut for the flower
            if (!above.is(state.getBlock())) return false;
        }
        return true;
    }

    private static boolean isSapling(BlockState state) {
        return state.is(BlockTags.SAPLINGS);
    }

    private static @Nullable IntegerProperty ageOf(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && age.getName().equals("age")) return age;
        }
        return null;
    }

    private static int lastAge(IntegerProperty age) {
        int last = 0;
        for (int value : age.getPossibleValues()) last = Math.max(last, value);
        return last;
    }

    /** A bush with its berries on (its "age" at the last value). */
    private static boolean ripeBush(BlockState state) {
        if (!state.is(BUSHES_TAG)) return false;
        IntegerProperty age = ageOf(state);
        return age != null && state.getValue(age) == lastAge(age);
    }

    /** This bush as it stands when ripe (for JEI, which rolls its drops). */
    public static BlockState ripe(Block bush) {
        BlockState state = bush.defaultBlockState();
        IntegerProperty age = ageOf(state);
        return age == null ? state : state.setValue(age, lastAge(age));
    }

    /** Something bone meal helps: a sapling, or a bush not ripe yet. */
    private static boolean growing(BlockState state) {
        return isSapling(state) || (state.is(BUSHES_TAG) && !ripeBush(state));
    }

    /**
     * The nearest square that needs the soul: a grown tree to fell (if a basket has room), or free ground it has a
     * sapling for. With neither, and bone meal in stock, a cast over the saplings. Nothing while a tree comes down.
     */
    @Override
    protected void findWork(ServerLevel server, long now) {
        if (!falling.isEmpty()) return;
        Survey survey = survey(server);
        refreshBoneMeal(survey);
        BlockPos here = soulTo(), best = null;
        int bestAction = 0;
        double bestDistance = Double.MAX_VALUE;
        boolean anySapling = false, roomKnown = false, room = false;
        for (BlockPos square : field()) {
            BlockPos pos = world(square);
            BlockState state = server.getBlockState(pos);
            int needed = 0;
            if ((isLog(state) || tallStalk(server, pos, state)) && fells(state)) {
                if (square.distSqr(here) < bestDistance) {
                    if (roomFor(server, state)) needed = CHOP;
                }
            } else if (ripeBush(state)) {
                if (square.distSqr(here) < bestDistance) {
                    if (roomFor(server, state)) needed = HARVEST;
                }
            } else if (growing(state) || state.is(STALKS_TAG)) { // (a stalk tall enough was taken above)
                anySapling = true;
            } else if (state.isAir() && treeGoes(square) && !square.equals(here)
                    && !survey.seeds().isEmpty() && square.distSqr(here) < bestDistance && seedFor(server, survey, square, pos) != null) {
                needed = SOW;
            }
            if (needed == 0) continue;
            best = square;
            bestAction = needed;
            bestDistance = square.distSqr(here);
        }
        if (best != null) {
            go(besides(best), best, bestAction, now, bestAction != SOW, bestAction != HARVEST); // always from the square beside
        } else if (anySapling && (survey.fertilizer() != null || endless()) && castDue(now)) {
            go(byThePost(), middle(), FERTILIZE, now, false, true);
        } else {
            idle(server, now);
        }
    }

    /**
     * Nothing to plant, fell or feed. A soul that wanders (the woodsman, between the trees) is left to it; one that
     * does not (the pond's fisherman) goes back up on its post.
     */
    protected void idle(ServerLevel server, long now) {
        if (!wanders() && !soulTo().equals(byThePost())) go(byThePost(), middle(), 0, now, false, false);
    }

    /** The saplings: what a bone meal cast is for here. */
    @Override
    protected List<BlockPos> hungry(ServerLevel server) {
        List<BlockPos> saplings = new ArrayList<>();
        for (BlockPos square : field()) {
            BlockPos pos = world(square);
            BlockState state = server.getBlockState(pos);
            if (growing(state) || (state.is(STALKS_TAG) && !tallStalk(server, pos, state))) saplings.add(pos);
        }
        return saplings;
    }

    // Bone meal does nothing to a cactus or to sugar cane by hand: here one piece makes a stalk one block taller
    // (bamboo takes bone meal by itself, and keeps its own way)
    @Override
    protected void feed(ServerLevel server, BlockPos pos, BlockState state) {
        if (!state.is(STALKS_TAG) || (state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(server, pos, state))) {
            super.feed(server, pos, state);
            return;
        }
        BlockPos top = pos;
        while (top.getY() - pos.getY() < MAX_STALK && server.getBlockState(top.above()).is(state.getBlock())) top = top.above();
        BlockState more = state.getBlock().defaultBlockState();
        if (server.getBlockState(top.above()).isAir() && more.canSurvive(server, top.above())) server.setBlock(top.above(), more, Block.UPDATE_ALL);
    }

    /** The second swing: the axe bites (the trunk's own sound and chips), nothing falls yet. */
    @Override
    protected void first(ServerLevel server) {
        BlockPos pos = world(soulTarget());
        BlockState state = server.getBlockState(pos);
        if (action() == HARVEST) { // a ripe bush: its berries, and it stays
            IntegerProperty age = ageOf(state);
            if (!ripeBush(state) || age == null) return;
            List<ItemStack> drops = harvestOf(server, pos, state);
            server.setBlock(pos, state.setValue(age, Math.min(1, lastAge(age))), Block.UPDATE_CLIENTS);
            playSound(pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f);
            hold(drops.isEmpty() ? null : drops.getFirst().getItem());
            for (ItemStack drop : drops) gather(server, drop);
            return;
        }
        if (!isLog(state) && !state.is(STALKS_TAG)) return;
        playSound(pos, state.getSoundType().getHitSound(), 1.0f, 0.8f);
        server.levelEvent(2001, pos, Block.getId(state)); // the block's breaking chips, without breaking it
    }

    @Override
    protected void second(ServerLevel server) {
        if (action() == CHOP) fell(server);
        else super.second(server); // planting is the crop plot's sowing, the cast is its cast
    }

    /** The third swing: the whole tree is found from its foot, and starts coming down from the top. */
    private void fell(ServerLevel server) {
        BlockPos foot = world(soulTarget());
        BlockState trunk = server.getBlockState(foot);
        if (trunk.is(STALKS_TAG)) { // a column: everything above its foot, from the top
            falling.clear();
            List<BlockPos> column = new ArrayList<>();
            for (BlockPos up = foot.above(); column.size() < MAX_STALK; up = up.above()) {
                BlockState part = server.getBlockState(up);
                if (!part.is(trunk.getBlock()) && !part.is(TOPS_TAG)) break;
                column.add(up);
                if (part.is(TOPS_TAG)) break; // its flower: the top of it
            }
            for (int i = column.size() - 1; i >= 0; i--) falling.add(column.get(i));
            perTick = 1;
            playSound(foot, trunk.getSoundType().getBreakSound(), 1.0f, 0.9f);
            return;
        }
        if (!isLog(trunk)) return;
        playSound(foot, trunk.getSoundType().getBreakSound(), 1.0f, 0.7f);

        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.add(foot);
        queue.add(foot);
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos at = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                if (Math.abs(next.getX() - foot.getX()) > REACH || Math.abs(next.getZ() - foot.getZ()) > REACH
                        || next.getY() < foot.getY() || next.getY() > foot.getY() + HEIGHT) continue;
                if (logs.contains(next) || !isLog(server.getBlockState(next))) continue;
                BlockPos found = next.immutable();
                logs.add(found);
                queue.add(found);
            }
        }
        // Its leaves: those within a few steps of its logs that are not a player's (persistent ones are left)
        Set<BlockPos> leaves = new HashSet<>();
        List<BlockPos> edge = new ArrayList<>(logs);
        for (int step = 0; step < LEAF_STEPS && !edge.isEmpty() && leaves.size() < MAX_LEAVES; step++) {
            List<BlockPos> reached = new ArrayList<>();
            for (BlockPos at : edge) {
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                    BlockPos next = at.relative(side);
                    if (leaves.contains(next) || logs.contains(next)) continue;
                    BlockState state = server.getBlockState(next);
                    if (!state.is(BlockTags.LEAVES) || (state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))) continue;
                    leaves.add(next);
                    reached.add(next);
                }
            }
            edge = reached;
        }
        List<BlockPos> tree = new ArrayList<>(logs.size() + leaves.size());
        tree.addAll(logs);
        tree.addAll(leaves);
        tree.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        falling.clear();
        falling.addAll(tree);
        perTick = Math.max(1, (tree.size() + FELL_TICKS - 1) / FELL_TICKS);
        hold(trunk.getBlock().asItem());
    }

    /** A falling tree loses a few blocks each tick, from the top: each gives what it drops. */
    @Override
    protected void everyTick(ServerLevel server) {
        if (falling.isEmpty()) return;
        for (int i = 0; i < perTick && !falling.isEmpty(); i++) {
            BlockPos pos = falling.poll();
            BlockState state = server.getBlockState(pos);
            boolean log = isLog(state) || state.is(STALKS_TAG) || state.is(TOPS_TAG);
            if (!log && !state.is(BlockTags.LEAVES)) continue; // gone, or something else by now
            List<ItemStack> drops = harvestOf(server, pos, state);
            if (log || falling.size() % 3 == 0) server.destroyBlock(pos, false); // with its chips and sound
            else server.removeBlock(pos, false);                                  // most leaves go quietly
            for (ItemStack drop : drops) gather(server, drop);
        }
        if (falling.isEmpty()) lookSoon(); // the last block is down: on to the next tree, or the replanting
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        falling.clear();
    }
}
