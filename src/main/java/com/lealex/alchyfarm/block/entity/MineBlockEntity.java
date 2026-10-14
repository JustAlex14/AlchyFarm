package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.block.PumpBlock;
import com.lealex.alchyfarm.registry.ModRegistries;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * The mine: a MACHINE, as the mushroom hollow is (a fixed structure, data/alchyfarm/multiblock/mine.json, checked by
 * AlchyX): a shaft's mouth of rough stone with a timber front, the miner's post in the middle of the front between
 * a stock and a basket. When it forms its stone becomes machine shells that look like rock (named after the mine)
 * and a lantern is hung inside; a miner comes through a rift, and goes when it breaks.
 *
 * It digs with the PICKAXES in its stock, any mod's (item tag minecraft:pickaxes, which mods add theirs to). Each
 * block it digs costs the pickaxe one use, as by hand (Unbreaking counts), and the pickaxe decides what comes up: a
 * block the pickaxe could not harvest by hand is not dug, and its enchantments apply to the drops (Fortune, Silk
 * Touch). Nothing is dug from the world: what comes up is picked among Config mine.veins, by weight, among the
 * entries whose heights hold the height the mine DIGS AT, which the player sets on its screen anywhere from the
 * world's bottom up to the post's own height (a shaft goes down). Torches in the stock (mine.torch) make it quicker.
 *
 * The digging is SEEN, and it only digs what is seen: every Config mine.secondsPerVein the rock gives up one vein
 * of that height (by weight), which comes to show on a block of the mine's walls (that shell takes the block's
 * look), up to WAITING of them. A vein the player turned off on the screen never comes: its turn is lost, and no
 * swing or wear is spent on it. The miner walks to a vein that shows and works it, one block an errand: as many
 * swings as that block is hard for this pickaxe (as breaking it by hand), all of them biting but the last, which
 * breaks it with the block's own breaking. The wall is then rock again. With nothing showing it has nothing to do,
 * and wanders. One errand's action: MINE, played with the felling gesture and the pickaxe in hand, its swings timed
 * here in everyTick.
 *
 * Its screen lists what can come up at that height, each dug or not (KindChoice), and holds the dial for the height.
 */
public class MineBlockEntity extends SoulPlotBlockEntity implements KindChoice {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "mine");
    public static final int MINE = 1;
    /** Within one swing, when the pickaxe lands (client/SoulModel.chopSwing strikes at the same moment). */
    public static final float IMPACT = 0.62F;
    private static final int NOT_SET = Integer.MIN_VALUE;

    // The pickaxe in hand (synced: the figure holds it), how long a swing takes in hundredths of the usual (synced:
    // torches shorten it), how many swings the block it is at takes (synced: the figure swings that many times), and
    // how many more blocks the last torch lights
    private @Nullable Item pick;
    private int blockWait = 100;
    private int blockSwings = 3;
    private int litBlocks = 0;
    // The height it digs at (NOT_SET: the post's own), what can come up there and what the player leaves on the heap
    // (all saved and synced, for the screen)
    private int depth = NOT_SET;
    private List<Item> offered = List.of();
    private final Set<Item> refused = new HashSet<>();

    /** A vein waiting on the wall: where the miner stands for it, the wall block it is on, and what it is. */
    private record Waiting(BlockPos stand, BlockPos wall, BlockState vein) {}

    private static final int WAITING = 3;
    private static final int TOP_PACE = 5; // at the top a vein comes this many times as often

    // A vein is worked through its swings, not in the two moments of an errand
    @Override
    protected boolean instantWork() {
        return false;
    }
    // Not saved: the veins waiting on the walls (after a reload the walls are made rock again and new ones come),
    // the one being worked, and the swings at it already landed
    private final List<Waiting> waiting = new ArrayList<>();
    private boolean wallsKnown = false;
    private @Nullable Waiting working;
    private long nextVein = 0; // game time the rock next gives something up
    private @Nullable BlockPos lastWall; // the wall block it dug last: the next vein comes elsewhere, and it goes elsewhere
    private int swingsDone = Integer.MAX_VALUE;

    public MineBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistries.MINE_BE.get(), pos, state);
    }

    /** For mines built on this one (the nether mine). */
    protected MineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Mine";
    }

    @Override
    protected String plotName() {
        return "mine";
    }

    @Override
    public String soulLook() {
        return "toolsmith";
    }

    @Override
    protected int maxSize() {
        return 3;
    }

    // ---- The structure (as the mushroom hollow's) ----

    @Override
    protected boolean quiet() {
        return false;
    }

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        if (nowFormed) rescan();
        else closeDown();
    }

    /** The floor is the 3 by 3 behind the post, as the pattern is written (front south: north of the post), turned with it. */
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
        List<BlockPos> floor = new ArrayList<>(9);
        for (int z = -3; z <= -1; z++) {
            for (int x = -1; x <= 1; x++) floor.add(new BlockPos(x, 0, z).rotate(turn));
        }
        floor.sort(Comparator.comparingInt((BlockPos square) -> square.getZ()).thenComparingInt(square -> square.getX()));
        // Its stores: the stock and the basket beside the post, and what stands on top of them (a cask)
        BlockPos left = worldPosition.offset(new BlockPos(-1, 0, 0).rotate(turn)), right = worldPosition.offset(new BlockPos(1, 0, 0).rotate(turn));
        return new Ground(floor, List.of(left, right, left.above(), right.above()), null);
    }

    @Override
    protected String arrivedWords() {
        return "The mine is whole: someone has come through to work its rock.";
    }

    @Override
    protected String goneWords() {
        return "The mine is broken: its miner is gone.";
    }

    // ---- The errand's timing and look (both sides) ----

    /** How long one swing takes, in ticks. */
    private int swingTicks() {
        return Math.max(14, Config.soulWorkTicks() * 7 / 10 * blockWait / 100);
    }

    @Override
    public int workTicks() {
        return action() == MINE ? blockSwings * swingTicks() : Config.soulWorkTicks();
    }

    // Digging is played as felling is, with the pickaxe where the woodsman has its axe
    @Override
    public int gesture() {
        return action() == MINE ? CHOP : 0;
    }

    @Override
    public int swings() {
        return action() == MINE ? blockSwings : super.swings();
    }

    private static int level(ServerLevel server, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return server.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(all -> all.get(enchantment))
                .map(found -> EnchantmentHelper.getItemEnchantmentLevel(found, stack)).orElse(0);
    }

    /**
     * How fast this pickaxe digs this block, as by hand: its own speed on it, and Efficiency on top as the game
     * counts it (level x level + 1), which an item's own speed leaves out.
     */
    private static float speed(ServerLevel server, ItemStack pickaxe, BlockState block) {
        float speed = pickaxe.getDestroySpeed(block);
        int efficiency = speed > 1.0F ? level(server, pickaxe, Enchantments.EFFICIENCY) : 0;
        return Math.max(1.0F, speed + (efficiency > 0 ? efficiency * efficiency + 1 : 0));
    }

    /** The pickaxes of the stock. */
    private List<Slot> pickaxes(ServerLevel server) {
        List<Slot> picks = new ArrayList<>(2);
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                if (isPickaxe(stock.getItem(slot))) picks.add(new Slot(stock, slot));
            }
        }
        return picks;
    }

    private static boolean canBreak(List<Slot> picks, BlockState block) {
        for (Slot pick : picks) {
            if (pick.stack().isCorrectToolForDrops(block)) return true;
        }
        return false;
    }

    /**
     * The pickaxe of the stock to dig this block with: one that can harvest it, and of those the one that gives
     * the most (Fortune, then Silk Touch), then the fastest. Null if the stock holds no pickaxe.
     */
    private @Nullable Slot best(ServerLevel server, BlockState block) {
        Slot best = null;
        float bestScore = -1.0F;
        for (Slot slot : pickaxes(server)) {
            ItemStack pickaxe = slot.stack();
            float score = speed(server, pickaxe, block) + 200.0F * level(server, pickaxe, Enchantments.FORTUNE)
                    + 100.0F * level(server, pickaxe, Enchantments.SILK_TOUCH) + (pickaxe.isCorrectToolForDrops(block) ? 10000.0F : 0.0F);
            if (score > bestScore) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * How many swings this block takes with this pickaxe: by the time breaking it by hand would take (its hardness
     * over the pickaxe's speed on it), a swing for every five ticks of that, two at least and seven at most.
     */
    private static int swingsFor(ServerLevel server, BlockPos at, BlockState block, ItemStack pickaxe) {
        float speed = speed(server, pickaxe, block);
        float ticks = Math.max(0.0F, block.getDestroySpeed(server, at)) * 30.0F / speed;
        return Math.clamp(Math.round(ticks / 5.0F), 2, 7);
    }

    @Override
    public Item swingTool() {
        return pick != null ? pick : Items.WOODEN_PICKAXE;
    }

    // Its swings are timed in everyTick, not by the two moments of an errand
    @Override
    protected float firstAt() {
        return 0.5F;
    }

    @Override
    protected float secondAt() {
        return 0.9F;
    }

    @Override
    protected void first(ServerLevel server) {}

    @Override
    protected void second(ServerLevel server) {}

    // ---- The height it digs at ----

    /** The height whose veins it digs (both sides). */
    public int depth() {
        return depth == NOT_SET ? startDepth() : depth;
    }

    /** The height it digs at until the player sets one: the post's own. */
    protected int startDepth() {
        return worldPosition.getY();
    }

    /** The lowest and highest heights the dial goes to: from the world's bottom up to the post (a shaft goes down). */
    protected int lowest() {
        return level == null ? -64 : level.getMinY();
    }

    protected int highest() {
        return worldPosition.getY();
    }

    /** What a pump brings up: the config's springs for this kind of mine. */
    protected List<Config.Spring> springs() {
        return Config.mineSprings();
    }

    /**
     * A spring's share as the haste stamp makes it (the bounty stamp adds its own in toCasks): half as much again,
     * two and a half times, eight times, and at tier IV as much as its casks can take: it never runs short.
     */
    private int pumped(int perSecond) {
        return switch (hasteTier()) {
            case 0 -> perSecond;
            case 1 -> perSecond * 3 / 2;
            case 2 -> perSecond * 5 / 2;
            case 3 -> perSecond * 8;
            default -> ENDLESS_PUMP;
        };
    }

    private static final int ENDLESS_PUMP = 4_000_000; // mB a second: more than any row of casks holds

    /** The square a pump stands on: the middle of the floor. */
    private BlockPos pumpSquare() {
        return new BlockPos(0, 0, -2).rotate(rotation());
    }

    private long lastPour = Long.MIN_VALUE / 2; // game time it last poured something into a cask (not saved)

    /** True if this is the pump of this mine and it poured something a moment ago (the pump asks, to know when to rest). */
    public boolean drives(BlockPos pump) {
        return isFormed() && hasSoul() && level != null && pump.equals(world(pumpSquare())) && level.getGameTime() - lastPour <= 60;
    }

    /**
     * Once a second: with a pump in the middle of its floor and a cask on its stock or its basket, each spring of
     * the height it digs at (Config mine.springs) gives its share to the casks, for as long as they have room. It
     * needs no pickaxe and no one's hand. The pump works (its look: PumpBlock.PUMPING) only while something is
     * really poured: not at a height with no spring, not with no cask, not with the casks full.
     */
    private void pump(ServerLevel server, long now) {
        if (now % 20 != 0) return;
        if (now % 100 == 0) rescan(); // a cask stood on the stock is found within a few seconds
        BlockPos at = world(pumpSquare());
        BlockState there = server.getBlockState(at);
        if (!there.is(ModRegistries.PUMP.get())) return;
        boolean poured = false;
        if (!casks.isEmpty()) {
            int y = depth();
            for (Config.Spring spring : springs()) {
                if (y >= spring.minY() && y <= spring.maxY()) poured |= toCasks(server, spring.fluid(), pumped(spring.perSecond())) > 0;
            }
        }
        if (poured) lastPour = now;
        if (there.getValue(PumpBlock.PUMPING) != poured) {
            server.setBlock(at, there.setValue(PumpBlock.PUMPING, poured), Block.UPDATE_CLIENTS);
            playSound(at, poured ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, 0.3f, 0.6f);
        }
        if (poured) {
            server.scheduleTick(at, there.getBlock(), PumpBlock.CHECK); // (nothing new if one is already waiting)
            if (now % 80 == 0) {
                playSound(at, SoundEvents.PISTON_EXTEND, 0.2f, 0.6f);
                server.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 1.05, at.getZ() + 0.5, 3, 0.1, 0.05, 0.1, 0.0);
            }
        }
    }

    /** What can come up: the config's veins for this kind of mine. */
    protected List<Config.Vein> veins() {
        return Config.mineVeins();
    }

    /** The lang key of its screen's title. */
    protected String screenTitle() {
        return "container.alchyfarm.mine";
    }

    /** What its walls are made of where no vein shows. */
    protected BlockState rock() {
        return (depth() < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
    }

    // ---- The stock ----

    /** Any mod's pickaxe: the vanilla item tag every mod adds its pickaxes to (minecraft:pickaxes). */
    public static boolean isPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
    }

    private record Slot(Container stock, int slot) {
        ItemStack stack() {
            return stock.getItem(slot);
        }
    }

    private @Nullable Slot find(ServerLevel server, Predicate<ItemStack> what) {
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                if (what.test(stock.getItem(slot))) return new Slot(stock, slot);
            }
        }
        return null;
    }

    // ---- The wall ----

    /** The rock it can swing at: beside a square of its floor, and not the post, the stock or the basket. */
    private List<BlockPos[]> faces() {
        Rotation turn = rotation();
        Set<BlockPos> front = Set.of(BlockPos.ZERO, new BlockPos(-1, 0, 0).rotate(turn), new BlockPos(1, 0, 0).rotate(turn));
        List<BlockPos[]> faces = new ArrayList<>(9);
        for (BlockPos square : field()) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos wall = square.relative(side);
                if (!field().contains(wall) && !front.contains(wall)) faces.add(new BlockPos[] {square, wall});
            }
        }
        return faces;
    }

    private static boolean isOre(BlockState vein) {
        return vein.is(Tags.Blocks.ORES);
    }

    /**
     * How a vein looks on the wall: itself. Plain rock of the wall's own kind would not show on it, so it shows as
     * its broken form (cobblestone on stone, cobbled deepslate on deepslate).
     */
    protected BlockState lookOf(BlockState vein) {
        if (vein.is(Blocks.STONE)) return Blocks.COBBLESTONE.defaultBlockState();
        if (vein.is(Blocks.DEEPSLATE)) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        return vein;
    }

    /** A wall block takes the look of the vein waiting there, or is rock again (null). */
    private void look(ServerLevel server, BlockPos wall, @Nullable BlockState vein) {
        if (server.getBlockEntity(world(wall)) instanceof ChamberShellBlockEntity shell) shell.setDisguise(vein == null ? rock() : lookOf(vein));
    }

    /** No vein waits any more (the height changed, the pickaxe is gone): the walls are rock again. */
    private void clearWalls(ServerLevel server) {
        for (Waiting vein : waiting) look(server, vein.wall(), null);
        waiting.clear();
        if (working != null) look(server, working.wall(), null);
        working = null;
        swingsDone = Integer.MAX_VALUE;
    }

    /**
     * Now and then (Config mine.secondsPerVein, sooner by torchlight) the rock gives something up: one vein of the
     * height it digs at, by weight, comes to show on a wall block that has none, unless WAITING already wait. A
     * vein the player turned off on the screen does not come at all: that time is simply lost, and so is the
     * pickaxe's wear on it saved.
     */
    private void appear(ServerLevel server, long now) {
        if (now < nextVein) return;
        long wait = hasted(Math.max(10, Config.mineVeinSeconds() * 20 * (litBlocks > 0 ? Config.mineTorchPercent() : 100) / 100));
        nextVein = now + (maxed() ? Math.max(2, wait / TOP_PACE) : wait); // at the top the rock gives much more often
        List<Slot> picks = pickaxes(server);
        if (picks.isEmpty()) {
            if (!waiting.isEmpty()) clearWalls(server);
            return;
        }
        List<BlockPos[]> faces = faces();
        if (!wallsKnown) { // after a reload: whatever the walls still showed is rock again
            wallsKnown = true;
            for (BlockPos[] face : faces) look(server, face[1], null);
        }
        // (at the top, every wall block but one can hold a vein at once, and all come away in one cast)
        if (faces.isEmpty() || waiting.size() >= Math.min(maxed() ? Math.max(WAITING, faces.size() - 1) : WAITING, faces.size())) return;
        BlockState vein = vein(server, picks);
        if (vein == null || refused.contains(vein.getBlock().asItem())) return; // nothing it can break, or nothing wanted
        for (int tries = 0; tries < 8; tries++) {
            BlockPos[] face = faces.get(server.getRandom().nextInt(faces.size()));
            boolean taken = faces.size() > WAITING && face[1].equals(lastWall); // not where it has just dug
            for (Waiting other : waiting) taken |= other.wall().equals(face[1]);
            if (working != null) taken |= working.wall().equals(face[1]);
            if (taken) continue;
            waiting.add(new Waiting(face[0], face[1], vein));
            look(server, face[1], vein); // the rock crumbles off it
            BlockPos at = world(face[1]);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock()), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.02);
            playSound(at, SoundEvents.GRAVEL_FALL, 0.5f, 0.8f);
            lookSoon();
            return;
        }
    }

    // ---- Work ----

    /**
     * It digs only what shows on its walls: with a pickaxe in the stock and room in the basket, it goes to one of
     * the veins waiting there. With none waiting it has nothing to do (it wanders its mine).
     */
    @Override
    protected void findWork(ServerLevel server, long now) {
        if (waiting.isEmpty()) return;
        if (!basketHasRoom(server)) return;
        // Any of them, but not on the wall block it has just dug if another waits: it moves about its mine
        Waiting next = waiting.get(server.getRandom().nextInt(waiting.size()));
        for (int tries = 0; tries < 4 && next.wall().equals(lastWall) && waiting.size() > 1; tries++) {
            next = waiting.get(server.getRandom().nextInt(waiting.size()));
        }
        waiting.remove(next);
        working = next;
        lastWall = next.wall();
        Slot tool = best(server, next.vein());
        if (tool == null) { // the pickaxes are gone
            clearWalls(server);
            return;
        }
        pick = tool.stack().getItem();
        blockSwings = maxed() ? 1 : swingsFor(server, world(next.wall()), next.vein(), tool.stack()); // (at the top: one stroke)
        swingsDone = 0;
        // A torch from the stock lights the next few blocks: quicker work
        if (litBlocks <= 0 && endless()) litBlocks = Math.max(1, Config.mineTorchBlocks()); // the Quality Work stamp: always lit
        if (litBlocks <= 0 && Config.mineTorchBlocks() > 0) {
            Slot torch = find(server, stack -> stack.is(Items.TORCH));
            if (torch != null) {
                if (!spared(server)) torch.stock().removeItem(torch.slot(), 1);
                litBlocks = Config.mineTorchBlocks();
                playSound(worldPosition, SoundEvents.FIRECHARGE_USE, 0.3f, 1.3f); // a fresh torch lit
            }
        }
        blockWait = litBlocks > 0 ? Config.mineTorchPercent() : 100;
        go(next.stand(), next.wall(), MINE, now, false, false);
    }

    /** Veins come to show on the walls; and the swings at the block it works land one after the other. */
    @Override
    protected void everyTick(ServerLevel server) {
        pump(server, server.getGameTime());
        appear(server, server.getGameTime());
        if (action() != MINE || working == null || swingsDone >= swings()) return;
        long since = server.getGameTime() - taskStart() - travelTicks();
        int work = errandTicks(), swings = swings();
        while (working != null && swingsDone < swings && since >= (swingsDone + IMPACT) / swings * work) {
            if (swingsDone + 1 < swings) bite(server);
            else dig(server);
            swingsDone++;
        }
    }

    /** The pickaxe bites: the block's own sound and chips, nothing comes away yet. */
    private void bite(ServerLevel server) {
        BlockState of = working == null ? rock() : working.vein();
        chips(server, of, 7);
        // The pickaxe on the block: the block's own "hit", low as when it is mined by hand, and the ring of the iron
        // (an ore rings clearer than plain rock)
        BlockPos at = world(soulTarget());
        playSound(at, of.getSoundType().getHitSound(), 1.0f, 0.5f + server.getRandom().nextFloat() * 0.15f);
        playSound(at, SoundEvents.NETHERITE_BLOCK_HIT, isOre(of) ? 0.45f : 0.25f, isOre(of) ? 1.5f : 1.1f);
    }

    /**
     * The block comes away: it breaks (its own chips and sound) and gives what it drops to the pickaxe in use,
     * which wears by one use. Where an ore showed, the wall is rock again.
     */
    private void dig(ServerLevel server) {
        Waiting done = working;
        working = null;
        if (done == null) return;
        take(server, done);
        if (magic()) { // by magic: every other vein that shows comes away with it (each wears a pickaxe as usual)
            for (Waiting more : List.copyOf(waiting)) {
                waiting.remove(more);
                take(server, more);
            }
        }
    }

    /** One vein comes away from the wall. */
    private void take(ServerLevel server, Waiting done) {
        BlockPos wall = world(done.wall());
        BlockState dug = done.vein();
        server.levelEvent(2001, wall, Block.getId(dug)); // the block's own breaking
        chips(server, dug, 14);
        playSound(wall, dug.getSoundType().getBreakSound(), 1.0f, 0.8f); // (the level event's own is quiet from afar)
        look(server, done.wall(), null);
        if (isOre(dug)) playSound(wall, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.4f); // something worth having came away
        Slot tool = best(server, dug);
        if (tool == null) return; // the pickaxe was taken away mid swing: the block is lost
        ItemStack pickaxe = tool.stack();
        List<ItemStack> drops = pickaxe.isCorrectToolForDrops(dug) ? Block.getDrops(dug, server, wall, null, null, pickaxe) : List.of();
        // Gilded blackstone always comes as itself: the game's one time in ten of gold nuggets instead would be
        // gold the player did not ask for (nuggets are the nether gold ore's, a kind of its own on the screen)
        if (!drops.isEmpty() && dug.is(net.minecraft.world.level.block.Blocks.GILDED_BLACKSTONE)) drops = List.of(new ItemStack(Items.GILDED_BLACKSTONE));
        if (!spared(server)) pickaxe.hurtAndBreak(1, server, null, broken -> playSound(worldPosition, SoundEvents.ITEM_BREAK.value(), 0.8f, 1.0f));
        tool.stock().setChanged();
        if (litBlocks > 0) litBlocks--;
        if (refused.contains(dug.getBlock().asItem())) return; // left on the heap
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) toBasket(server, drop);
        }
    }

    /** A vein of the height it digs at, by weight, that one of these pickaxes can harvest. Null if none of them can any. */
    private @Nullable BlockState vein(ServerLevel server, List<Slot> picks) {
        int y = depth(), total = 0;
        List<Config.Vein> here = new ArrayList<>();
        for (Config.Vein vein : veins()) {
            if (y < vein.minY() || y > vein.maxY()) continue;
            if (sureLoot() && refused.contains(vein.block().asItem())) continue; // at the top: only what is wanted comes
            here.add(vein);
            total += vein.weight();
        }
        if (total <= 0) return null;
        int at = server.getRandom().nextInt(total);
        for (Config.Vein vein : here) {
            at -= vein.weight();
            if (at >= 0) continue;
            BlockState state = vein.block().defaultBlockState();
            if (canBreak(picks, state)) return state;
            break;
        }
        for (Config.Vein vein : here) { // too hard for it: plain rock instead, the first kind it can break
            BlockState state = vein.block().defaultBlockState();
            if (canBreak(picks, state)) return state;
        }
        return null;
    }

    /** Chips of this block flying from the wall's face, toward the miner. */
    private void chips(ServerLevel server, BlockState of, int count) {
        BlockPos from = world(soulTo()), wall = world(soulTarget());
        double x = from.getX() * 0.35 + wall.getX() * 0.65 + 0.5, z = from.getZ() * 0.35 + wall.getZ() * 0.65 + 0.5;
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, of), x, from.getY() + 0.9, z, count, 0.12, 0.25, 0.12, 0.05);
    }

    // ---- Its screen: what it keeps of what comes up, and the height it digs at ----

    @Override
    public void openGui(Player player) {
        if (!isFormed() || !(player instanceof ServerPlayer serverPlayer)) {
            super.openGui(player); // not whole: AlchyX says what is missing
            return;
        }
        refreshOffered();
        serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, opener) -> new ChoiceMenu(ModRegistries.CHOICE_MENU.get(), containerId, inventory, this),
                Component.translatable(screenTitle())), buffer -> buffer.writeBlockPos(worldPosition));
    }

    /** A vein's share of what comes at this height: of everything, and (when some are off at the top) of what is on. */
    @Override
    public List<String> odds(Item kind) {
        int y = depth(), total = 0, wanted = 0, own = 0;
        for (Config.Vein vein : veins()) {
            if (y < vein.minY() || y > vein.maxY()) continue;
            Item item = vein.block().asItem();
            total += vein.weight();
            if (!refused.contains(item)) wanted += vein.weight();
            if (item == kind) own += vein.weight();
        }
        if (total <= 0 || own <= 0) return List.of();
        List<String> told = new ArrayList<>(2);
        told.add("Comes " + percent(own, total) + " of the time");
        if (sureLoot() && wanted > 0 && wanted < total && !refused.contains(kind)) told.add("With the others off: " + percent(own, wanted));
        return told;
    }

    private static String percent(int part, int whole) {
        int tenths = Math.round(part * 1000.0f / whole);
        return tenths >= 100 || tenths % 10 == 0 ? tenths / 10 + "%" : tenths / 10 + "." + tenths % 10 + "%";
    }

    /** The veins of the height it digs at, as the items of their blocks. */
    private void refreshOffered() {
        List<Item> kinds = new ArrayList<>();
        int y = depth();
        for (Config.Vein vein : veins()) {
            Item item = vein.block().asItem();
            if (y >= vein.minY() && y <= vein.maxY() && item != Items.AIR && !kinds.contains(item)) kinds.add(item);
        }
        // What each vein puts in the basket (one roll of its drops with a good pickaxe): the screen tells from it
        // whether the rift marks it (raw metal) or only gives more of it (stone, a gem)
        boolean learned = false;
        if (level instanceof ServerLevel server) {
            for (Item kind : kinds) {
                if (gives.containsKey(kind) || kind == Items.GILDED_BLACKSTONE || !(kind instanceof net.minecraft.world.item.BlockItem block)) continue;
                for (ItemStack drop : Block.getDrops(block.getBlock().defaultBlockState(), server, worldPosition, null, null, new ItemStack(Items.NETHERITE_PICKAXE))) {
                    if (drop.isEmpty()) continue;
                    gives.put(kind, drop.getItem());
                    learned = true;
                    break;
                }
            }
        }
        if (kinds.equals(offered) && !learned) return;
        offered = List.copyOf(kinds);
        setChanged();
        syncToClients();
    }

    private final java.util.Map<Item, Item> gives = new java.util.HashMap<>(); // vein block item -> what it drops (synced)

    @Override
    public Item given(Item kind) {
        return kind == Items.GILDED_BLACKSTONE ? kind : gives.getOrDefault(kind, kind);
    }

    @Override
    public List<Item> offered() {
        return offered;
    }

    @Override
    public boolean chosen(Item kind) {
        return !refused.contains(kind);
    }

    @Override
    public void toggle(int index) {
        if (index < 0 || index >= offered.size()) return;
        Item kind = offered.get(index);
        if (!refused.remove(kind)) refused.add(kind);
        setChanged();
        syncToClients();
    }

    // A vein the pickaxe last in hand could not harvest is told so (it is passed over for plain rock)
    @Override
    public @Nullable String warning(Item kind) {
        if (pick == null || !(kind instanceof BlockItem block)) return null;
        return new ItemStack(pick).isCorrectToolForDrops(block.getBlock().defaultBlockState()) ? null : "pickaxe too weak";
    }

    @Override
    public String chosenLabel() {
        return "Dug";
    }

    @Override
    public List<String> notes() {
        return List.of(pick == null ? "Put a pickaxe in the stock." : "It digs what shows on the walls.",
                "Off: that block never shows.", "Torches in the stock: faster.");
    }

    @Override
    public String dial() {
        return "Height " + depth();
    }

    // From the world's bottom up to the post's own height: a shaft goes down
    @Override
    public void turnDial(int by) {
        if (level == null) return;
        int before = depth();
        depth = Math.clamp(before + by, lowest(), highest());
        if (depth != before && level instanceof ServerLevel server) { // other veins at that height: those waiting go
            int landed = swingsDone;
            Waiting kept = working;
            working = null;
            clearWalls(server);
            working = kept; // the block under the pickaxe is finished as it is
            swingsDone = landed;
        }
        refreshOffered();
        setChanged();
        syncToClients();
    }

    // ---- Saving (also what clients get) ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (pick != null) output.store("pick", BuiltInRegistries.ITEM.byNameCodec(), pick);
        output.putInt("blockWait", blockWait);
        output.putInt("blockSwings", blockSwings);
        output.putInt("litBlocks", litBlocks);
        if (depth != NOT_SET) output.putInt("depth", depth);
        output.store("offered", BuiltInRegistries.ITEM.byNameCodec().listOf(), offered);
        List<Item> dropped = new ArrayList<>(offered.size());
        for (Item kind : offered) dropped.add(given(kind));
        output.store("offeredGives", BuiltInRegistries.ITEM.byNameCodec().listOf(), dropped);
        if (!refused.isEmpty()) output.store("refused", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(refused));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pick = input.read("pick", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        blockWait = Math.clamp(input.getIntOr("blockWait", 100), 10, 400);
        blockSwings = Math.clamp(input.getIntOr("blockSwings", 3), 1, 12);
        litBlocks = input.getIntOr("litBlocks", 0);
        depth = input.getIntOr("depth", NOT_SET);
        offered = List.copyOf(input.read("offered", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
        List<Item> dropped = input.read("offeredGives", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of());
        gives.clear();
        for (int i = 0; i < Math.min(dropped.size(), offered.size()); i++) {
            if (dropped.get(i) != offered.get(i)) gives.put(offered.get(i), dropped.get(i));
        }
        refused.clear();
        refused.addAll(input.read("refused", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
    }
}
