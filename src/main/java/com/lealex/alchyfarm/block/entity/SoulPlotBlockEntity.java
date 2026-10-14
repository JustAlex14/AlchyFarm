package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyx.block.entity.WorkerCoreBlockEntity;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.block.CaskBlock;
import com.lealex.alchyfarm.block.PlotStoreBlock;
import com.lealex.alchyfarm.upgrade.FarmStamps;
import com.lealex.alchyfarm.upgrade.Rifted;
import com.lealex.alchyx.upgrade.UpgradeItem;
import com.lealex.alchyx.upgrade.UpgradeStorage;
import com.lealex.alchyx.upgrade.UpgradeType;
import com.lealex.alchyfarm.registry.ModRegistries;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.Enclosure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What every workstation of the estate shares: a post found in the hut, and fence (fences, fence gates, walls)
 * joined to it that closes around a piece of ground of any shape, up to a size (AlchyX's Enclosure). What is closed
 * in is the workstation's ground: a field, a pen. Stores (a stock, a basket, a trough) join it by touching its
 * fence or standing in its line. As soon as the ground is closed in, a lost soul comes through a rift over it, and
 * goes when the fence opens. The soul runs errands: it goes to a square, works there, and waits on it.
 *
 * A workstation says what its soul does: {@link #findWork} picks an errand and starts it with {@link #go},
 * {@link #first} and {@link #second} do its work at two moments of the gesture, and {@link #gesture} tells the
 * client's figure which gesture to play.
 *
 * What it costs the server: the fence is followed again only when a block changes near the post (and twice a
 * minute, in case a change was missed). A soul with nothing to do looks for work every two seconds. Clients get
 * one small update per errand, nothing while it moves.
 *
 * To AlchyX this is a quiet machine whose pattern is the post alone (it carries the animation file, and later the
 * upgrades).
 */
public abstract class SoulPlotBlockEntity extends WorkerCoreBlockEntity {
    /** What a workstation's ring is made of. */
    public static final TagKey<Block> FENCES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "plot_fences"));

    /**
     * The soul's gestures (client/SoulModel). SOW: bent to the ground in front of it, pressing something in.
     * HARVEST: bent over, both hands pulling, then upright with what it got held in both hands and looked over.
     * FERTILIZE: a bag at the chest, the other hand casting. SOW | HARVEST: harvest, then sow.
     */
    public static final int SOW = 1, HARVEST = 2, FERTILIZE = 4;
    /** One more gesture: CHOP, both arms swinging an axe at something in front of it, three times. */
    public static final int CHOP = 8;
    /** And FISH: a rod held out in both hands, cast, a long wait, a pull, and the catch held up. */
    public static final int FISH = 16;
    /**
     * An errand of the soul's own, when nothing needs doing: it strolls to a square and tends what is there, bent a
     * little to it, a hand reaching down (a crop looked over, an animal petted). Not one of a workstation's actions:
     * the base class starts it, times it and calls {@link #tended} for it.
     */
    public static final int TEND = 1024;
    /**
     * Working by magic (a violet stamp on the post): whatever the work, the soul stands and casts as an evoker
     * does, both arms raised, a small rift open over its hands that takes in what the work gives. No tool, no rod.
     */
    public static final int CAST = 2048;
    private static final int TEND_TICKS = 50;
    /** Of a harvest-then-sow gesture, the share spent harvesting. */
    public static final float HARVEST_SHARE = 0.7F;
    /** Within a harvest gesture, when the hands come up with what they got. */
    public static final float HARVEST_MOMENT = 0.42F;

    private static final int RESCAN = 600;    // ticks between two searches of the fence that nothing asked for
    private static final int IDLE_LOOK = 40;  // ticks between two looks for work when the last found nothing
    private static final int MAX_RING = 512;
    private static final String NOBODY = "Nobody works it yet";

    // (The ground's squares `field` and the errand's numbers from / to / target / action / travel / taskStart are
    // AlchyX WorkerCoreBlockEntity's, with the geometry helpers and the errand's two moments.)
    private String problem = ""; // what the workstation lacks, for its screen
    // Its stores (server only, found again by every search)
    protected List<BlockPos> stocks = List.of(), baskets = List.of(), troughs = List.of();
    // Its casks, for what it gives as a fluid (found the same way)
    protected List<BlockPos> casks = List.of();

    // Its stamps (AlchyX's upgrade slots: one of each kind of upgrade/FarmStamps; saved, dropped and shown as
    // corruption by the base class), and their tiers as plain numbers, which is what clients get (the figure's
    // pace follows the haste)
    private final UpgradeStorage stamps = createUpgrades(FarmStamps.ALL);
    private int haste = 0, bounty = 0, thrift = 0, quality = 0, voiding = 0;
    private static final int FULL_WAIT = 100;  // ticks between two tries to hand over what found no room in the baskets
    private static final int HELD_MOST = 54;   // stacks it can keep meanwhile
    private final List<ItemStack> pending = new ArrayList<>(); // what found no room (saved)
    private static final int BATCH = 32;  // errands a maxed soul does in one cast, at most

    private boolean soul = false; // a lost soul works here
    private int soulWait = 0;     // ticks the closed ground has waited for one
    private long soulSince = Long.MIN_VALUE / 2; // game time it came (synced: the client opens a rift for it)
    private @Nullable Item held; // what the soul holds up during a HARVEST gesture (synced)

    // Not saved
    private boolean scanRequested = true;
    private int ticks = 0;
    private long nextLook = 0;
    private long nextStroll = 0;
    private long lastPop = 0; // the tick it last made a basket's sound
    private long restedFor = 0; // the errand (by its start) after which it has already taken its breath

    protected SoulPlotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- What a workstation says ----

    /** How it is called in chat lines, e.g. "crop plot". */
    protected abstract String plotName();

    /** The largest ground its fence may close in, in blocks along each side. */
    protected abstract int maxSize();

    /** How long the current errand's work lasts, in ticks (both sides: the client times the gesture with it). */
    public abstract int workTicks();

    /**
     * How this workstation's soul is dressed: the name of its skin, textures/entity/lost_soul_<look>.png (both
     * sides; tools/entity_textures.py makes them: farmer, shepherd, fletcher, fisherman, and bogged, which is
     * drawn on the bogged skeleton's body instead of the villager's).
     */
    public String soulLook() {
        return "farmer";
    }

    /** How many swings the CHOP gesture of the current errand has (both sides): three to fell a tree. */
    public int swings() {
        return 3;
    }

    /** What the figure holds while it plays the CHOP gesture (both sides): an axe, or the miner's pickaxe. */
    public Item swingTool() {
        return Items.WOODEN_AXE;
    }

    /** The gesture of the current errand: SOW, HARVEST, both, or FERTILIZE (both sides). */
    public abstract int gesture();

    /** What it lacks once its fence is right (a missing store...), or null. Shown on its screen. */
    protected @Nullable String lacks() {
        return null;
    }

    /** The ground, or whether a soul is here, changed. */
    protected void groundChanged(ServerLevel server) {}

    /**
     * True if the soul may stand on the post itself (square 0, 0: the figure then floats on top of it), which is not
     * a square of the ground. A workstation whose ground fills up (trees) rests its soul there.
     */
    protected boolean standsOnPost() {
        return false;
    }

    /**
     * True if its soul wanders about its ground while the workstation has nothing for it (both sides: the figure
     * does the wandering by itself, nothing is sent for it). Those that wait on their post do not.
     */
    public boolean wanders() {
        return true;
    }

    /** True if its soul strolls when it has nothing to do (the fisherman does not: it fishes). */
    protected boolean strolls() {
        return true;
    }

    /** Where an idle soul strolls to: a square of the ground to go and see. Here: any other square. Null: nowhere. */
    protected @Nullable BlockPos strollTo(ServerLevel server) {
        if (field.size() < 2) return null;
        BlockPos square = field.get(server.getRandom().nextInt(field.size()));
        return square.equals(to) ? null : square;
    }

    /** The soul has strolled to this square and bends to it: what its care does there (nothing, here). */
    protected void tended(ServerLevel server, BlockPos square) {}

    /** How long the current errand's work lasts (both sides): the workstation's, or a stroll's own. */
    public int errandTicks() {
        return action == TEND ? TEND_TICKS : Math.round(hasted(workTicks()) * (magic() ? MAGIC_PACE : 1.0F) * (0.88F + 0.3F * whim()));
    }

    // ---- The stamps ----

    public int hasteTier() {
        return haste;
    }

    public int bountyTier() {
        return bounty;
    }

    public int thriftTier() {
        return thrift;
    }

    /** The tier of the stamp of this kind (its place in FarmStamps.ALL) on the post, 0 for none (both sides: the letter reads it). */
    public int stampTier(int kind) {
        return switch (kind) {
            case 0 -> haste;
            case 1 -> bounty;
            case 2 -> thrift;
            case 3 -> quality;
            case 4 -> voiding;
            default -> 0;
        };
    }

    /**
     * True with the Quality Work stamp: the workstation is never short of what quickens it. Each one treats its
     * bone meal and its torches as always there, and uses none (CropPlotBlockEntity.fertilize, the hollow's cast,
     * the garden's blooms, the mine's light).
     */
    public boolean endless() {
        return quality > 0;
    }

    /** The Void stamp's tier: 1 destroys leftover seeds (CropPlotBlockEntity.gather), 2 keeps only what is rifted (toBasket). */
    public int voidTier() {
        return voiding;
    }

    @Override
    protected void onUpgradesChanged() {
        haste = stamps.tier(FarmStamps.HASTE);
        bounty = stamps.tier(FarmStamps.BOUNTY);
        thrift = stamps.tier(FarmStamps.THRIFT);
        quality = stamps.tier(FarmStamps.QUALITY);
        voiding = stamps.tier(FarmStamps.VOID);
        nextLook = 0; // a stamp changes how it works: it looks at once (and takes its place, at the top)
        super.onUpgradesChanged();
    }

    /** A length of time as the haste stamp leaves it (both sides). */
    protected long hasted(long ticks) {
        return haste <= 0 ? ticks : Math.max(1, ticks * Math.max(5, 100 - Config.hasteOf(haste)) / 100);
    }

    /** True if, by the thrift stamp, what is about to be used is NOT used up this time. */
    protected boolean spared(ServerLevel server) {
        return thrift > 0 && server.getRandom().nextInt(100) < Config.thriftOf(thrift);
    }

    /** Of this many pieces about to be used, how many really are (each has the thrift stamp's chance to be spared). */
    protected int unspared(ServerLevel server, int pieces) {
        int used = 0;
        for (int i = 0; i < pieces; i++) {
            if (!spared(server)) used++;
        }
        return used;
    }

    /** How many of this come back, with the bounty stamp's chance of one more for each (never for what does not stack). */
    private int bountied(ServerLevel server, ItemStack stack) {
        int count = stack.getCount();
        if (bounty <= 0 || stack.getMaxStackSize() <= 1) return count;
        int percent = Config.bountyOf(bounty), more = count * (percent / 100); // (above a hundred: so many more for sure)
        for (int i = 0; i < count; i++) {
            if (server.getRandom().nextInt(100) < percent % 100) more++;
        }
        return count + more;
    }

    /** The screens' buttons for the stamps: PRESS + a kind's number, TAKE + a kind's number (its place in FarmStamps.ALL). */
    public static final int PRESS = 3000, TAKE = 3100;
    /** With a violet stamp on its post a soul works by magic: its work takes this share of the usual time. */
    public static final float MAGIC_PACE = 0.6F;

    /**
     * How far the rift has this workstation: one for each tier above the second of each of its stamps (the tiers
     * pressed in his violet wax), 0 to 6 (both sides).
     */
    public int riftLevel() {
        return Math.max(0, haste - 2) + Math.max(0, bounty - 2) + Math.max(0, thrift - 2);
    }

    /**
     * True once a violet stamp is on the post: the soul then works BY MAGIC. It no longer walks to its work: it
     * casts from where it stands, and what it would have done by hand happens at a distance (a line of sparks to
     * the place), in MAGIC_PACE of the time, with no breath between two errands. And what grows on its ground grows
     * quicker (quicken). Each workstation may add its own (a mine digs every vein that shows at once).
     */
    public boolean magic() {
        return riftLevel() > 0;
    }

    /** False for a workstation whose harvest the rift leaves as it is (the hollow, the flower garden: his choice). */
    private long lastSound = Long.MIN_VALUE / 2;
    private static final int QUIET_GAP = 12;        // at the top: ticks between two sounds
    private static final float QUIET = 0.3F;        // and how loud they are

    /**
     * At the top a workstation does in a second what took a minute, and every part of it had its sound: hives,
     * splashes, picks. There it is heard at most once in QUIET_GAP ticks, and softly.
     */
    @Override
    protected void playSound(BlockPos pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (maxed() && level != null) {
            long now = level.getGameTime();
            if (now - lastSound < QUIET_GAP) return;
            lastSound = now;
            volume *= QUIET;
        }
        super.playSound(pos, sound, volume, pitch);
    }

    /** The chance in a hundred that something it brings back comes rifted (0 without a violet stamp, or where the rift enhances nothing). */
    public int riftedChance() {
        return magic() && riftEnhances() ? Math.min(100, riftLevel() * Config.riftedPercent()) : 0;
    }

    /**
     * True for a workstation whose harvest the rift never MARKS (the hollow, the flower garden): when its roll
     * passes, what stacks simply comes two for one (three from rift level 5), and nothing is changed.
     */
    public boolean riftOnlyMore() {
        return false;
    }

    protected boolean riftEnhances() {
        return true;
    }

    /** False for a workstation whose ground must not be made to grow quicker (the hollow: its mushrooms would spread). */
    protected boolean riftGrows() {
        return true;
    }

    /**
     * Once a second, by magic: each square of the ground has riftLevel x Config stamps.riftGrowthPercent chances in
     * a hundred that what grows on it (and up to two blocks above: a stalk, stacked trellises) gets one of the
     * game's own growth ticks. So anything the game grows, of any mod, grows quicker here, by its own rules.
     */
    private void quicken(ServerLevel server) {
        if (server.getGameTime() % 20 != 0 || !riftGrows()) return;
        int chance = riftLevel() * Config.riftGrowthPercent();
        if (chance <= 0) return;
        for (BlockPos square : field) {
            if (server.getRandom().nextInt(100) >= chance) continue;
            BlockPos pos = world(square);
            for (int up = 0; up < 3; up++) {
                BlockState state = server.getBlockState(pos.above(up));
                if (state.isAir()) break;
                if (!state.isRandomlyTicking()) continue;
                state.randomTick(server, pos.above(up), server.getRandom());
                if (server.getRandom().nextInt(4) == 0) {
                    server.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + up + 0.5, pos.getZ() + 0.5, 2, 0.25, 0.25, 0.25, 0.0);
                }
            }
        }
    }

    /** How high over the square it stands on the rift of its cast hangs (the figure draws it there: client/Farmhand). */
    public static final float CAST_RIFT = 1.4F, CAST_AHEAD = 1.0F; // the cast's rift: so high, so far before the soul

    private long lastMotes = -1;

    /**
     * By magic, what is brought back is seen coming: three or four small bits of the thing itself (the game's own
     * item crumbs, so in its colours) leave the place it came from toward the rift before the soul's hands. Nothing
     * else: no beam, no sound. One burst a tick at most.
     */
    private void motes(ServerLevel server, ItemStack of) {
        if (of.isEmpty() || server.getGameTime() == lastMotes) return;
        lastMotes = server.getGameTime();
        BlockPos soulPos = world(to), place = world(target);
        // The rift is before the soul: toward what it works on, or at the top straight away from the post
        double faceX = maxed() ? to.getX() : target.getX() - to.getX(), faceZ = maxed() ? to.getZ() : target.getZ() - to.getZ();
        double facing = Math.sqrt(faceX * faceX + faceZ * faceZ);
        double aheadX = facing < 0.01 ? 0.0 : faceX / facing * CAST_AHEAD, aheadZ = facing < 0.01 ? 0.0 : faceZ / facing * CAST_AHEAD;
        double dx = soulPos.getX() + aheadX - place.getX(), dy = soulPos.getY() + CAST_RIFT - (place.getY() + 0.5), dz = soulPos.getZ() + aheadZ - place.getZ();
        double far = Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz));
        ItemParticleOption bits = new ItemParticleOption(ParticleTypes.ITEM, of.getItem());
        for (int i = 0; i < 4; i++) {
            // count 0: the three "offsets" are the direction, the last number the speed
            server.sendParticles(bits, place.getX() + 0.5, place.getY() + 0.6, place.getZ() + 0.5, 0,
                    dx / far + (server.getRandom().nextDouble() - 0.5) * 0.3, dy / far + 0.45, dz / far + (server.getRandom().nextDouble() - 0.5) * 0.3,
                    0.16 + Math.min(0.2, far * 0.03));
        }
    }

    /**
     * AT THE TOP (rift level Config stamps.sureLootRiftLevel, 6 = three tier IV stamps): the soul stands before the
     * post, its back to it, and casts without pause (no walking, no wandering, no strolls: client/Farmhand holds
     * the cast); everything that needs doing is done in the one cast (tickFormed, for workstations whose
     * instantWork() is true); and only what is wanted comes (sureLoot).
     */
    public boolean maxed() {
        return sureLoot();
    }

    /** False for a workstation whose errands take their time whatever the magic (a cast in the water, a vein under the pickaxe). */
    protected boolean instantWork() {
        return true;
    }

    /** Where a maxed soul stands: the square of its ground nearest the post. */
    public BlockPos frontOfPost() {
        return nearest(0.0, 0.0);
    }

    /** True at the top: with rift level Config stamps.sureLootRiftLevel (6: three tier IV stamps), a mine only brings up what the player kept, and a cast never comes back with a refused catch. */
    public boolean sureLoot() {
        int needed = Config.sureLootLevel();
        return needed > 0 && riftLevel() >= needed;
    }

    /** Puts this stamp in its kind's place; the one that was there goes back to the player. */
    private void press(Player player, ItemStack from, UpgradeItem stamp) {
        int slot = stamps.types().indexOf(stamp.type());
        if (slot < 0) return;
        ItemStack old = stamps.stack(stamp.type());
        if (UpgradeItem.tierOf(old, stamp.type()) == stamp.tier()) {
            say(player, "This post already bears " + old.getHoverName().getString());
            return;
        }
        ItemStack pressed = from.copyWithCount(1);
        from.shrink(1);
        stamps.handler().set(slot, ItemResource.of(pressed), 1);
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
        playSound(worldPosition, SoundEvents.SMITHING_TABLE_USE, 0.8f, 1.3f);
        say(player, pressed.getHoverName().getString() + " pressed on" + (old.isEmpty() ? "" : " (the old one is back with you)"));
    }

    /** A player sneaks and uses a stamp on the post (AlchyFarm's own listener: a plain right-click opens the screen). */
    public void useStamp(Player player, InteractionHand hand, ItemStack inHand) {
        if (inHand.getItem() instanceof UpgradeItem stamp && level instanceof ServerLevel) press(player, inHand, stamp);
    }

    /**
     * A click on a stamp's place in the workstation's screen. PRESS + kind: the stamp on the mouse if it is of that
     * kind, else the best one of that kind in the player's inventory that is better than the one on the post.
     * TAKE + kind: the one on the post goes back to the player.
     */
    /**
     * The list screen's extra buttons (AlchyX KindChoice.button, which the workstations with such a screen inherit
     * from here): a stamp's place on the letter.
     */
    public boolean button(Player player, int id, ItemStack carried) {
        if (id < PRESS || id >= TAKE + 100) return false;
        stampButton(player, id, carried);
        return true;
    }

    public void stampButton(Player player, int id, ItemStack carried) {
        if (!(level instanceof ServerLevel)) return;
        boolean take = id >= TAKE;
        int kind = id - (take ? TAKE : PRESS);
        if (kind < 0 || kind >= stamps.types().size()) return;
        UpgradeType type = stamps.types().get(kind);
        ItemStack old = stamps.stack(type);
        if (take) {
            if (old.isEmpty()) return;
            stamps.handler().set(kind, ItemResource.EMPTY, 0);
            if (!player.getInventory().add(old)) player.drop(old, false);
            playSound(worldPosition, SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f);
            return;
        }
        ItemStack from = carried.getItem() instanceof UpgradeItem held && held.type() == type ? carried : ItemStack.EMPTY;
        int best = UpgradeItem.tierOf(old, type);
        if (from.isEmpty()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (UpgradeItem.tierOf(stack, type) > best) {
                    best = UpgradeItem.tierOf(stack, type);
                    from = stack;
                }
            }
        }
        if (from.isEmpty()) {
            say(player, "No better " + Component.translatable(type.translationKey()).getString() + " in your inventory");
            return;
        }
        press(player, from, (UpgradeItem) from.getItem());
    }

    private static void say(Player player, String words) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(words), true);
    }

    /** The gesture the figure plays for the current errand (both sides): the workstation's, or a stroll's own. */
    public int errandGesture() {
        if (action == TEND) return TEND;
        // By magic it does not bend, swing, dig or fish: whatever the work, it casts
        return magic() && gesture() != 0 ? CAST : gesture();
    }

    /** Every tick while a soul is here, before its errand is looked at (for work that goes on by itself). */
    protected void everyTick(ServerLevel server) {}

    // ---- AlchyX ----

    // The one-block pattern forms at once: not what the player built, so AlchyX says nothing about it
    @Override
    protected boolean quiet() {
        return true;
    }

    // Block changes this far from the post ask for a new search of the fence
    @Override
    protected int watchReach() {
        return maxSize() + 2;
    }

    @Override
    public void requestStructureCheck() {
        super.requestStructureCheck();
        scanRequested = true;
    }

    /**
     * A block changed near the post. Most such changes are the soul's own work (a crop cut, a seed set, a log
     * felled), and none of those can move the border: the border lies on the post's level, and what changed is a
     * square of the ground that holds no piece of border now. Those ask for nothing; everything else searches again.
     */
    @Override
    public void requestStructureCheck(BlockPos changed) {
        if (flatGround() && level != null && !linkedStores.contains(changed)) {
            int up = changed.getY() - worldPosition.getY();
            if (up != 0) return; // above or under the ground: no border there
            if (field.contains(changed.subtract(worldPosition)) && !joins(level.getBlockState(changed))) return;
        }
        requestStructureCheck();
    }

    /** False for a workstation that is one fixed structure (its own ground()): every change near it is looked at. */
    protected boolean flatGround() {
        return true;
    }

    // ---- What screens and the figure read (both sides) ----

    public String problem() {
        return problem;
    }

    public boolean hasSoul() {
        return soul;
    }

    public BlockPos soulFrom() {
        return from;
    }

    public BlockPos soulTo() {
        return to;
    }

    /** What the errand works on, which the soul faces once it stands on soulTo(). */
    public BlockPos soulTarget() {
        return target;
    }

    /** The game time the soul came through its rift. */
    public long soulSince() {
        return soulSince;
    }

    /** What the soul holds up during a HARVEST gesture, or null. */
    public @Nullable Item held() {
        return held;
    }

    // ---- For workstations ----

    /**
     * Starts an errand: the soul goes to {@code stand}, faces {@code target} and works for workTicks().
     *
     * @param hasFirst whether {@link #first} is to be called for it; hasSecond the same for {@link #second}
     */
    protected void go(BlockPos stand, BlockPos target, int action, long now, boolean hasFirst, boolean hasSecond) {
        if (magic() && action != TEND) stand = maxed() ? frontOfPost() : to; // by magic it works from where it stands (at the top: before the post)
        int way = to.equals(stand) ? 0 : (int) Math.max(3, hasted(Math.round(Math.sqrt(to.distSqr(stand)) * Config.soulGlideTicks())));
        startErrand(stand, target, action, now, way, hasFirst, hasSecond);
    }

    /** Looks for work on the next tick it is free (something changed that may mean work). */
    protected void lookSoon() {
        nextLook = 0;
    }

    /** What the soul shows in its hands during a HARVEST gesture (synced if it changed). */
    protected void hold(@Nullable Item item) {
        if (item == held) return;
        held = item;
        syncToClients();
    }

    /** Puts a stack into these stores, as far as it fits. Returns what is left. */
    protected static ItemStack put(ServerLevel server, List<BlockPos> stores, ItemStack stack) {
        for (BlockPos pos : stores) {
            if (stack.isEmpty()) break;
            if (server.getBlockEntity(pos) instanceof Container store) stack = HopperBlockEntity.addItem(null, store, stack, null);
        }
        return stack;
    }

    /** Puts a stack into the baskets. What does not fit pops out at the post. */
    protected void toBasket(ServerLevel server, ItemStack stack) {
        // What it drops in is heard: one soft pop a tick at most (a felled tree drops a great deal at once)
        if (!stack.isEmpty() && !baskets.isEmpty() && server.getGameTime() != lastPop) {
            lastPop = server.getGameTime();
            playSound(baskets.getFirst(), SoundEvents.ITEM_PICKUP, 0.25f, 0.9f + server.getRandom().nextFloat() * 0.5f);
        }
        if (magic()) motes(server, stack);
        // By magic, a share of what it brings back is rifted: enhanced (upgrade/Rifted)
        boolean rifted = magic() && riftEnhances() && server.getRandom().nextInt(100) < riftLevel() * Config.riftedPercent();
        // The Void stamp's second tier: where the rift enhances, only what it enhanced is kept, and what it made itself
        // (a pond's rift potion comes already marked). What the rift could do nothing with (a tool, a saddle, a
        // bottle of water: things that do not stack and are no food) is not kept either.
        boolean only = voiding >= 2 && magic() && riftEnhances(), was = Rifted.isRifted(stack);
        if (only && !rifted && !was) return;
        if (rifted) {
            int had = stack.getCount();
            if (riftOnlyMore()) {
                if (stack.getMaxStackSize() > 1) stack.setCount(stack.getCount() * (riftLevel() >= 5 ? 3 : 2));
            } else {
                stack = Rifted.touch(server, stack, riftLevel()); // (the more violet tiers, the stronger)
            }
            if (only && !was && !Rifted.isRifted(stack) && stack.getCount() <= had && !stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) return;
            if (!baskets.isEmpty()) {
                BlockPos at = baskets.getFirst();
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 4, 0.2, 0.15, 0.2, 0.02);
            }
        }
        int all = bountied(server, stack); // (the bounty stamp)
        while (all > 0) {
            ItemStack piece = stack.copyWithCount(Math.min(all, stack.getMaxStackSize()));
            all -= piece.getCount();
            ItemStack left = put(server, baskets, piece);
            if (!left.isEmpty()) keep(left); // no room for it: the soul holds on to it (NEVER dropped in the world)
        }
    }

    /**
     * What found no room in a basket waits here, in the soul's keeping, and all work stops until it has gone in
     * (tickFormed). It is never thrown on the ground: a full basket under a busy workstation once covered the world
     * in items. Held up to HELD_MOST stacks; past that (a great tree coming down on a full basket) the rest is lost.
     */
    private void keep(ItemStack left) {
        for (ItemStack held : pending) {
            if (!ItemStack.isSameItemSameComponents(held, left)) continue;
            int moved = Math.min(left.getCount(), held.getMaxStackSize() - held.getCount());
            held.grow(moved);
            left.shrink(moved);
            if (left.isEmpty()) break;
        }
        if (!left.isEmpty() && pending.size() < HELD_MOST) pending.add(left.copy());
        setChanged();
    }

    /** Tries again to put what it keeps into the baskets. True once it keeps nothing. */
    private boolean handOver(ServerLevel server) {
        if (pending.isEmpty()) return true;
        for (int i = pending.size() - 1; i >= 0; i--) {
            ItemStack left = put(server, baskets, pending.get(i));
            if (left.isEmpty()) pending.remove(i);
            else pending.set(i, left);
        }
        setChanged();
        return pending.isEmpty();
    }

    /** How much of this fluid its casks can still take. */
    protected int caskRoom(ServerLevel server, Fluid fluid) {
        int room = 0;
        for (BlockPos pos : casks) {
            if (server.getBlockEntity(pos) instanceof CaskBlockEntity cask) room += cask.room(fluid);
        }
        return room;
    }

    /** Pours a fluid into its casks, as far as it fits. Returns how much went in. */
    protected int toCasks(ServerLevel server, Fluid fluid, int amount) {
        amount = amount * (100 + Config.bountyOf(bounty)) / 100; // (the bounty stamp)
        int poured = 0;
        for (BlockPos pos : casks) {
            if (poured >= amount) break;
            if (server.getBlockEntity(pos) instanceof CaskBlockEntity cask) poured += cask.fill(fluid, amount - poured);
        }
        return poured;
    }

    /**
     * True if a basket can still take something: an empty slot, or a stack that is not full. (It used to ask for an
     * empty slot: a basket whose 27 slots each held a few of something stopped all work, though every harvest would
     * have gone onto those stacks.) What does not fit after all pops out at the post, and the soul then waits a
     * while before it tries again (toBasket), so a truly full basket is not showered with items.
     */
    protected boolean basketHasRoom(ServerLevel server) {
        if (!pending.isEmpty()) return false; // it still keeps something that found no room
        for (BlockPos basketPos : baskets) {
            if (!(server.getBlockEntity(basketPos) instanceof Container basket)) continue;
            for (int slot = 0; slot < basket.getContainerSize(); slot++) {
                ItemStack held = basket.getItem(slot);
                if (held.isEmpty() || held.getCount() < Math.min(held.getMaxStackSize(), basket.getMaxStackSize())) return true;
            }
        }
        return false;
    }

    /**
     * True if a basket can take THIS thing: an empty slot, or a stack of it that is not full. Asked where the work
     * is known to give mostly one thing (a stalk, a log, a crop), so that a basket full of something else (a pond's
     * fish) does not stop what would still go on its own stack (the pond's sugar cane). Unlike basketHasRoom it does
     * not mind what the soul is still keeping for want of room: that is of another kind.
     */
    protected boolean basketTakes(ServerLevel server, ItemStack what) {
        if (what.isEmpty()) return false;
        for (BlockPos basketPos : baskets) {
            if (!(server.getBlockEntity(basketPos) instanceof Container basket)) continue;
            for (int slot = 0; slot < basket.getContainerSize(); slot++) {
                ItemStack held = basket.getItem(slot);
                if (held.isEmpty()) return true;
                if (ItemStack.isSameItemSameComponents(held, what) && held.getCount() < Math.min(held.getMaxStackSize(), basket.getMaxStackSize())) return true;
            }
        }
        return false;
    }

    /** True if a basket has room for what this block gives when it is harvested (judged by the block's own item). */
    protected boolean roomFor(ServerLevel server, BlockState harvested) {
        return basketHasRoom(server) || basketTakes(server, new ItemStack(harvested.getBlock().asItem()));
    }

    // ---- Every tick: a few comparisons, unless something is due ----

    @Override
    protected void tickFormed() {
        if (!(level instanceof ServerLevel server)) return;
        if (scanRequested || ticks % RESCAN == 0) {
            scanRequested = false;
            scan(server);
        }
        ticks++;
        if (field.isEmpty()) return;
        if (!soul) {
            if (++soulWait >= Config.soulArrivalTicks()) arrive(server);
            return;
        }
        everyTick(server);
        if (magic()) quicken(server);
        long now = server.getGameTime(), since = now - taskStart;
        int work = errandTicks();
        if (action == TEND) { // a stroll: its one moment is the base's, not the workstation's
            if (!secondDone && since >= travel + work * 0.5F) {
                secondDone = true;
                tended(server, target);
                setChanged();
            }
        } else {
            runMoments(server, since, work);
        }
        if (since < travel + work) return; // still at it
        if (restedFor != taskStart) { // the errand is just over: a breath, not always as long, before it looks for the next
            restedFor = taskStart;
            nextLook = Math.max(nextLook, now + (magic() ? 1 : 3 + server.getRandom().nextInt(16)));
        }
        // At the top it stands before the post whatever it was doing: if it is anywhere else, it goes there first
        if (maxed() && !field.isEmpty() && !to.equals(frontOfPost())) {
            go(frontOfPost(), middle(), 0, now, false, false);
            return;
        }
        if (now >= nextLook) {
            nextLook = now + IDLE_LOOK; // unless an errand starts, in which case it looks again when that is over
            // While it keeps something that found no room in a basket, most work waits (basketHasRoom says no): only
            // what is sure to go on a stack of its own is still done (basketTakes), and it looks less often
            if (!handOver(server)) nextLook = now + FULL_WAIT;
            long before = taskStart;
            went = false;
            findWork(server, now);
            // At the top, one cast does everything that needs doing: each errand found is done at once and the next
            // looked for (the last one found is left to play out, so the cast goes on)
            if (went && maxed() && instantWork()) {
                for (int more = 0; more < BATCH; more++) {
                    if (!firstDone) {
                        firstDone = true;
                        first(server);
                    }
                    if (!secondDone) {
                        secondDone = true;
                        second(server);
                    }
                    if (!pending.isEmpty()) break; // the baskets are full: no more this cast
                    went = false;
                    findWork(server, now);
                    if (!went) break;
                }
                setChanged();
            }
            if (taskStart == before) stroll(server, now); // nothing to do
        }
    }

    /**
     * Nothing needs doing: now and then (about every Config souls.idleSeconds, never if that is 0) the soul strolls
     * to a square of its ground and tends what is there.
     */
    private void stroll(ServerLevel server, long now) {
        int rest = Config.soulIdleSeconds() * 20;
        if (rest <= 0 || !strolls() || maxed()) return;
        if (nextStroll == 0) nextStroll = now + rest / 2 + server.getRandom().nextInt(rest + 1);
        if (now < nextStroll) return;
        nextStroll = now + rest / 2 + server.getRandom().nextInt(rest + 1);
        BlockPos square = strollTo(server);
        if (square == null) return;
        go(besides(square), square, TEND, now, false, true);
        // The figure wanders by itself and may be a few blocks from where it is thought to stand: time to walk over
        travel = Math.max(travel, 4 * Config.soulGlideTicks());
        syncToClients();
    }

    // ---- The fence and the ground ----

    /**
     * What closes a border: fences, gates and walls (FENCES_TAG, which the posts also reach their arms to), and the
     * quieter things of BORDERS_TAG (hedges of leaves, glass panes, bars, trapdoors, carpets to walk over).
     */
    public static final TagKey<Block> BORDERS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "plot_borders"));

    private static boolean joins(BlockState state) {
        return state.is(FENCES_TAG) || state.is(BORDERS_TAG) || state.getBlock() instanceof PlotStoreBlock || state.getBlock() instanceof CaskBlock;
    }

    /**
     * With no closed border: the squares within Config souls.openGroundRadius of the post (sorted by z then x), but
     * for the post's own, those holding a piece of border or a store, and those another workstation already works
     * (the first there keeps them). Empty when the config asks for a border.
     */
    private List<BlockPos> openGround(ServerLevel server) {
        int radius = Math.min(Config.openGroundRadius(), maxSize() / 2);
        if (radius <= 0) return List.of();
        List<BlockPos> open = new ArrayList<>();
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if (x == 0 && z == 0) continue;
                BlockPos pos = worldPosition.offset(x, 0, z);
                if (joins(server.getBlockState(pos)) || worked(server, pos)) continue;
                open.add(new BlockPos(x, 0, z));
            }
        }
        return List.copyOf(open);
    }

    // ---- The surveyor's cord (item/SurveyorCordItem): a field and stores given by hand, away from the post ----

    private List<BlockPos> linkedField = List.of();                   // squares counted from the post; empty: none given
    private final List<BlockPos> linkedStores = new ArrayList<>();    // world positions

    /** False for a workstation that is one fixed structure: the cord gives it nothing. */
    public boolean linkable() {
        return true;
    }

    public String linkName() {
        return plotName();
    }

    /** The stores the cord joined to this post (world positions). Clients know them too (client/CordLinks). */
    public List<BlockPos> linkedStores() {
        return linkedStores;
    }

    private boolean far(BlockPos pos) {
        int reach = watchReach(); // (block changes that far are what asks for a new look at the ground)
        return Math.abs(pos.getX() - worldPosition.getX()) > reach || Math.abs(pos.getZ() - worldPosition.getZ()) > reach
                || Math.abs(pos.getY() - worldPosition.getY()) > 4;
    }

    /** Joins this stock, basket, trough or cask to the post, or lets it go if it was. What the player is told. */
    public String linkStore(BlockPos pos) {
        boolean was = linkedStores.remove(pos);
        if (!was) {
            if (far(pos)) return "Too far from the post (" + watchReach() + " blocks at most).";
            linkedStores.add(pos.immutable());
        }
        setChanged();
        syncToClients();
        rescan();
        return was ? "Let go by the " + plotName() + "." : "Joined to the " + plotName() + ".";
    }

    /** What is wrong with this block as a corner of the field, or null. The field lies on the post's level. */
    public @Nullable String cornerProblem(BlockPos pos) {
        if (far(pos)) return "Too far from the post (" + watchReach() + " blocks at most).";
        int up = pos.getY() - worldPosition.getY();
        return up == 0 || up == -1 ? null : "The field must be on the post's level.";
    }

    /** The field is the rectangle between these two blocks (their x and z), on the post's level. What the player is told. */
    public String linkField(BlockPos a, BlockPos b) {
        String wrong = cornerProblem(b);
        if (wrong != null) return wrong;
        int minX = Math.min(a.getX(), b.getX()) - worldPosition.getX(), maxX = Math.max(a.getX(), b.getX()) - worldPosition.getX();
        int minZ = Math.min(a.getZ(), b.getZ()) - worldPosition.getZ(), maxZ = Math.max(a.getZ(), b.getZ()) - worldPosition.getZ();
        int most = maxSize();
        if (maxX - minX + 1 > most || maxZ - minZ + 1 > most) return "Too large (max " + most + " x " + most + ").";
        List<BlockPos> squares = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (x != 0 || z != 0) squares.add(new BlockPos(x, 0, z));
            }
        }
        if (squares.isEmpty()) return "That is the post itself.";
        linkedField = List.copyOf(squares);
        setChanged();
        rescan();
        return "Field of the " + plotName() + ": " + (maxX - minX + 1) + " x " + (maxZ - minZ + 1) + ".";
    }

    /** Forgets what the cord gave: the ground is found from the post again. */
    public void unlink() {
        linkedField = List.of();
        linkedStores.clear();
        hadBorder = false;
        setChanged();
        syncToClients();
        rescan();
    }

    // True once a closed border gave it its ground (saved): from then on it waits for a border, it never takes the
    // squares round the post by itself (a border whose every piece touching the post is gone would look like none).
    // The surveyor's cord's "forget" clears it.
    private boolean hadBorder = false;

    /** True if this block is on the ground of another workstation. */
    private boolean worked(ServerLevel server, BlockPos pos) {
        for (BlockPos core : com.lealex.alchyx.multiblock.CoreTracker.matricesNear(server, pos)) {
            if (core.equals(worldPosition) || core.getY() != pos.getY()) continue;
            if (server.getBlockEntity(core) instanceof SoulPlotBlockEntity other && other.field().contains(pos.subtract(core))) return true;
        }
        return false;
    }

    /**
     * What a search found: the ground's squares (counted from the post, sorted by z then x), the blocks among which
     * its stores are looked for, and what is wrong with it (null if nothing: the squares are then its ground).
     */
    protected record Ground(List<BlockPos> inside, Iterable<BlockPos> around, @Nullable String problem) {}

    /**
     * Finds the workstation's ground. Here: what the border joined to the post closes in, up to maxSize() each way,
     * or with no closed border the squares round the post (openGround).
     * A workstation that is a fixed structure says where its ground is in it instead.
     */
    protected Ground ground(ServerLevel server) {
        int most = maxSize();
        Enclosure.Found found = Enclosure.find(server, worldPosition, SoulPlotBlockEntity::joins, most + 2, MAX_RING);
        if (!linkedStores.isEmpty() || !linkedField.isEmpty()) {
            // Given by the surveyor's cord: its stores besides those joined to the post, and its field if it has one
            java.util.Set<BlockPos> around = new java.util.LinkedHashSet<>(found.line());
            around.addAll(linkedStores);
            if (!linkedField.isEmpty()) {
                List<BlockPos> squares = new ArrayList<>(linkedField.size());
                for (BlockPos square : linkedField) {
                    BlockPos pos = worldPosition.offset(square);
                    if (!joins(server.getBlockState(pos)) && !worked(server, pos)) squares.add(square);
                }
                return new Ground(List.copyOf(squares), around, squares.isEmpty() ? "Its linked field has no free square" : null);
            }
            found = new Enclosure.Found(around, found.inside(), found.minX(), found.minZ(), found.maxX(), found.maxZ());
        }
        if (found.inside().isEmpty()) {
            // A border that is there but not closed (a piece of it was just broken: a fence, a basket standing in
            // its line) is an OPEN FENCE, as it always was: no ground until it is closed again, and every choice
            // made for its squares is kept meanwhile. Only a post with no border at all works the ground round it.
            // (Else breaking a basket swapped the field for the squares round the post, and its choices were lost.)
            boolean bordered = false;
            for (BlockPos pos : found.line()) {
                BlockState there = server.getBlockState(pos);
                if (!pos.equals(worldPosition) && (there.is(FENCES_TAG) || there.is(BORDERS_TAG))) {
                    bordered = true;
                    break;
                }
            }
            List<BlockPos> open = bordered || hadBorder ? List.of() : openGround(server);
            return open.isEmpty() ? new Ground(List.of(), found.line(), "The fence is not closed") : new Ground(open, found.line(), null);
        }
        hadBorder = true;
        // One fence, one workstation: if another post stands on this fence and already works the ground it closes,
        // this one stays out of it (the first to have closed its ground keeps it)
        for (BlockPos pos : found.line()) {
            if (pos.equals(worldPosition)) continue;
            if (server.getBlockEntity(pos) instanceof SoulPlotBlockEntity other && !other.field().isEmpty()) {
                return new Ground(List.of(), found.line(), "This fence is the " + other.plotName() + "'s");
            }
        }
        if (found.width() > most || found.depth() > most) return new Ground(List.of(), found.line(), "Too large (max " + most + " x " + most + ")");
        return new Ground(found.inside(), found.line(), null);
    }

    /** What nearby players are told when the soul comes, and when it goes. */
    protected String arrivedWords() {
        return "Someone has come through to work the " + plotName() + ".";
    }

    protected String goneWords() {
        return "The " + plotName() + " has no ground left: its farmhand is gone.";
    }

    /** Asks for a new search of the ground on the next tick. */
    protected void rescan() {
        scanRequested = true;
    }

    /**
     * For a workstation whose structure AlchyX checks: it is no longer whole. Its soul goes and its ground is
     * forgotten until it is whole again (tickFormed is not called meanwhile).
     */
    protected void closeDown() {
        if (field.isEmpty() && !soul) return;
        field = List.of();
        if (soul) {
            leave();
            playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.5f);
        }
        setChanged();
        syncToClients();
    }

    /** Follows the fence from the post and takes what it closes in as the ground (asked for by block changes). */
    private void scan(ServerLevel server) {
        Ground ground = ground(server);
        List<BlockPos> foundStocks = new ArrayList<>(), foundBaskets = new ArrayList<>(), foundTroughs = new ArrayList<>();
        List<BlockPos> foundCasks = new ArrayList<>(1);
        for (BlockPos pos : ground.around()) {
            if (server.getBlockState(pos).getBlock() instanceof CaskBlock) foundCasks.add(pos.immutable());
            if (!(server.getBlockState(pos).getBlock() instanceof PlotStoreBlock store)) continue;
            switch (store.kind()) {
                case STOCK -> foundStocks.add(pos);
                case BASKET -> foundBaskets.add(pos);
                case TROUGH -> foundTroughs.add(pos);
            }
        }
        stocks = foundStocks;
        baskets = foundBaskets;
        troughs = foundTroughs;
        casks = foundCasks;

        List<BlockPos> closed = ground.inside();
        String says;
        if (ground.problem() != null) {
            says = ground.problem();
        } else {
            String more = lacks();
            says = more != null ? more : soul ? "" : NOBODY;
        }

        boolean different = !says.equals(problem);
        problem = says;
        if (!closed.equals(field)) {
            different = true;
            field = closed;
            if (field.isEmpty()) {
                if (soul) {
                    leave();
                    playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.5f);
                    notifyNearbyPlayers(goneWords());
                }
            } else if (!field.contains(to) && !(standsOnPost() && to.equals(BlockPos.ZERO))) { // its square is gone: by the post
                to = from = target = byThePost();
                action = 0;
                firstDone = secondDone = true;
            }
            groundChanged(server);
        }
        nextLook = 0;
        if (different) {
            setChanged();
            syncToClients();
        }
    }

    // ---- The soul ----

    private void arrive(ServerLevel server) {
        soul = true;
        soulSince = server.getGameTime();
        // The animation file draws the farmhand while this display holds something
        setAnimationDisplay("soul", new ItemStack(ModRegistries.CARVED_STAKE_ITEM.get()));
        setAnimationWorking(true);
        BlockPos at = world(to);
        server.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 16, 0.4, 0.5, 0.4, 0.02);
        playSound(at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f); // the rift tearing open
        playSound(at, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.8f);
        notifyNearbyPlayers(arrivedWords());
        if (problem.equals(NOBODY)) problem = "";
        groundChanged(server);
        setChanged();
        syncToClients();
    }

    private void leave() {
        soul = false;
        soulWait = 0;
        setAnimationDisplay("soul", ItemStack.EMPTY);
        setAnimationWorking(false);
    }

    // ---- Saving (also what clients get: squares are packed into one int each to keep the update small) ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveWork(output); // (the ground and the errand: AlchyX WorkerCoreBlockEntity)
        output.putString("problem", problem);
        output.putBoolean("hadBorder", hadBorder);
        if (!linkedField.isEmpty()) {
            int[] linked = new int[linkedField.size()];
            for (int i = 0; i < linked.length; i++) linked[i] = pack(linkedField.get(i));
            output.putIntArray("linkedSquares", linked);
        }
        if (!linkedStores.isEmpty()) {
            int[] linked = new int[linkedStores.size() * 3];
            for (int i = 0; i < linkedStores.size(); i++) {
                BlockPos store = linkedStores.get(i).subtract(worldPosition);
                linked[i * 3] = store.getX();
                linked[i * 3 + 1] = store.getY();
                linked[i * 3 + 2] = store.getZ();
            }
            output.putIntArray("linkedStores", linked);
        }
        output.putBoolean("soul", soul);
        output.putInt("soulWait", soulWait);
        output.putLong("soulSince", soulSince);
        output.putInt("haste", haste);
        output.putInt("bounty", bounty);
        output.putInt("thrift", thrift);
        output.putInt("quality", quality);
        output.putInt("voiding", voiding);
        if (held != null) output.store("held", BuiltInRegistries.ITEM.byNameCodec(), held);
        if (!pending.isEmpty()) output.store("kept", ItemStack.CODEC.listOf(), List.copyOf(pending));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pending.clear();
        for (ItemStack stack : input.read("kept", ItemStack.CODEC.listOf()).orElse(List.of())) pending.add(stack.copy());
        haste = input.getIntOr("haste", 0);
        bounty = input.getIntOr("bounty", 0);
        thrift = input.getIntOr("thrift", 0);
        quality = input.getIntOr("quality", 0);
        voiding = input.getIntOr("voiding", 0);
        loadWork(input);
        problem = input.getStringOr("problem", "");
        hadBorder = input.getBooleanOr("hadBorder", false);
        int[] linked = input.getIntArray("linkedSquares").orElse(new int[0]);
        List<BlockPos> given = new ArrayList<>(linked.length);
        for (int packed : linked) given.add(unpack(packed));
        linkedField = List.copyOf(given);
        linked = input.getIntArray("linkedStores").orElse(new int[0]);
        linkedStores.clear();
        for (int i = 0; i + 2 < linked.length; i += 3) linkedStores.add(worldPosition.offset(linked[i], linked[i + 1], linked[i + 2]));
        soul = input.getBooleanOr("soul", false);
        soulWait = input.getIntOr("soulWait", 0);
        soulSince = input.getLongOr("soulSince", Long.MIN_VALUE / 2);
        held = input.read("held", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
    }
}
