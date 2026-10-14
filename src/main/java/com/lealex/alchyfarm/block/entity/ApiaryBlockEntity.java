package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The apiary: a beekeeper's post whose fence closes around a bee yard (see SoulPlotBlockEntity for the fence, the
 * stores and the soul). The player stands HIVES in the yard (real beehives and bee nests: any block that is a
 * BeehiveBlock) and plants FLOWERS in it (block tag minecraft:flowers). No real bee is needed: while a soul is
 * here the yard's own ghost bees work it (client/Swarm draws them, from the hives to the flowers and back), and the
 * hives fill by themselves, one level of honey at a time (the hive's own HONEY_LEVEL, so it shows on the block):
 * every Config apiary.secondsPerLevel for each hive, quicker for every flower of the yard (apiary.flowerPercent
 * each, up to apiary.maxFlowers of them). With no flower there is no honey. Real bees living in the hives keep
 * working as they do, on top.
 *
 * The beekeeper goes to a full hive and empties it: with a glass bottle in the stock, one is used and the hive
 * gives Config apiary.honey (a honey bottle); with none it gives apiary.comb (three honeycombs).
 *
 * With a CASK joined to the yard and a honey fluid in the game (vanilla has none: another mod's, Config
 * apiary.fluid), the hive is emptied into the cask instead, apiary.fluidAmount of it, with no bottle.
 *
 * Its one errand's action is HARVEST.
 */
public class ApiaryBlockEntity extends SoulPlotBlockEntity implements KindChoice {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "apiary");
    private static final int LOOK_EVERY = 40; // ticks between two looks at what stands in the yard

    // The squares of the yard that hold a hive, and a flower (saved and synced: the ghost bees fly between them)
    private List<BlockPos> hives = List.of(), flowers = List.of();

    // Not saved
    private long nextHoney = 0, nextLook = 0;

    public ApiaryBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.APIARY_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Apiary";
    }

    @Override
    protected String plotName() {
        return "apiary";
    }

    @Override
    public String soulLook() {
        return "butcher";
    }

    @Override
    protected int maxSize() {
        return Config.maxFieldSize();
    }

    @Override
    protected @Nullable String lacks() {
        return baskets.isEmpty() ? "No basket: no honey" : null;
    }

    // ---- What the ghost bees read (both sides) ----

    public List<BlockPos> hives() {
        return hives;
    }

    public List<BlockPos> flowers() {
        return flowers;
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
        return 0.9F;
    }

    // ---- The yard ----

    private static boolean isHive(BlockState state) {
        return state.getBlock() instanceof BeehiveBlock && state.hasProperty(BeehiveBlock.HONEY_LEVEL);
    }

    /** What stands in the yard now: its hives and its flowers. */
    private void look(ServerLevel server) {
        List<BlockPos> foundHives = new ArrayList<>(4), foundFlowers = new ArrayList<>();
        for (BlockPos square : field()) {
            BlockState state = server.getBlockState(world(square));
            if (isHive(state)) foundHives.add(square);
            else if (state.is(BlockTags.FLOWERS)) foundFlowers.add(square);
        }
        if (foundHives.equals(hives) && foundFlowers.equals(flowers)) return;
        hives = List.copyOf(foundHives);
        flowers = List.copyOf(foundFlowers);
        setChanged();
        syncToClients();
    }

    /** Ticks between two levels of honey in the yard: each hive's own time, shared out, quicker for every flower. */
    private long honeyWait() {
        int counted = Math.min(flowers.size(), Config.apiaryMaxFlowers());
        long each = Config.apiaryLevelSeconds() * 20L * 100L / (100L + (long) Config.apiaryFlowerPercent() * Math.max(0, counted - 1));
        return hasted(Math.max(10, each / Math.max(1, hives.size())));
    }

    /** The ghost bees' work: now and then one hive that is not full gains a level of honey. */
    @Override
    protected void everyTick(ServerLevel server) {
        long now = server.getGameTime();
        if (now >= nextLook) {
            nextLook = now + LOOK_EVERY;
            look(server);
        }
        if (hives.isEmpty() || flowers.isEmpty()) {
            nextHoney = 0;
            return;
        }
        if (nextHoney == 0) nextHoney = now + honeyWait();
        if (now < nextHoney) return;
        nextHoney = now + honeyWait();
        List<BlockPos> filling = new ArrayList<>(hives.size());
        for (BlockPos square : hives) {
            BlockState state = server.getBlockState(world(square));
            if (isHive(state) && state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) filling.add(square);
        }
        if (filling.isEmpty()) return;
        BlockPos pos = world(filling.get(server.getRandom().nextInt(filling.size())));
        BlockState state = server.getBlockState(pos);
        int level = state.getValue(BeehiveBlock.HONEY_LEVEL) + 1;
        server.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, level), Block.UPDATE_ALL);
        playSound(pos, SoundEvents.BEEHIVE_ENTER, 0.5f, 1.0f);
        if (level >= BeehiveBlock.MAX_HONEY_LEVELS) lookSoon(); // full: work for the beekeeper
    }

    // ---- Work ----

    /** The nearest full hive, if a basket has room. */
    @Override
    protected void findWork(ServerLevel server, long now) {
        look(server);
        BlockPos here = soulTo(), best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos square : hives) {
            BlockState state = server.getBlockState(world(square));
            if (!isHive(state) || state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) continue;
            if (square.distSqr(here) >= bestDistance) continue;
            best = square;
            bestDistance = square.distSqr(here);
        }
        if (best != null && (basketHasRoom(server) || !casks.isEmpty())) go(besides(best), best, HARVEST, now, true, false);
    }

    /** Takes one glass bottle from the stocks. False if there is none. */
    private boolean takeBottle(ServerLevel server) {
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                if (!stock.getItem(slot).is(Items.GLASS_BOTTLE)) continue;
                if (!spared(server)) stock.removeItem(slot, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * The hive is emptied, as the first of these the player has left on (its screen) that can be done: into a cask
     * (rifted honey by magic and by chance, else honey), into a glass bottle from the stock, or cut out as comb.
     * If none can, the hive is left full.
     */
    @Override
    protected void first(ServerLevel server) {
        BlockPos pos = world(soulTarget());
        BlockState state = server.getBlockState(pos);
        if (!isHive(state) || state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) return;
        BlockState emptied = state.setValue(BeehiveBlock.HONEY_LEVEL, 0);
        int amount = Config.apiaryFluidAmount();
        if (wanted(cask()) && !casks.isEmpty()) {
            // By magic, with the rift's chance: rifted honey of its rift's tier, if a cask can take it (one that is
            // empty or holds that tier)
            Fluid rifted = ModRegistries.RIFTED_HONEY.get(ModRegistries.brewTier(riftLevel()) - 1).fluid(), honey = Config.apiaryFluid();
            boolean violet = magic() && server.getRandom().nextInt(100) < riftLevel() * Config.riftedPercent() && caskRoom(server, rifted) >= amount;
            if (violet || (honey != null && caskRoom(server, honey) >= amount)) {
                server.setBlock(pos, emptied, Block.UPDATE_ALL);
                toCasks(server, violet ? rifted : honey, amount);
                playSound(pos, SoundEvents.HONEY_BLOCK_SLIDE, 1.0f, violet ? 0.7f : 1.0f);
                server.sendParticles(violet ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.FALLING_HONEY, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                        6, 0.4, 0.2, 0.4, violet ? 0.02 : 0.0);
                hold(Items.HONEYCOMB);
                return;
            }
        }
        boolean bottled = wanted(Items.HONEY_BOTTLE) && takeBottle(server);
        if (!bottled && !wanted(Items.HONEYCOMB)) return; // nothing it may make of it: the hive stays full
        server.setBlock(pos, emptied, Block.UPDATE_ALL);
        playSound(pos, bottled ? SoundEvents.BOTTLE_FILL : SoundEvents.BEEHIVE_SHEAR, 1.0f, 1.0f);
        server.sendParticles(ParticleTypes.FALLING_HONEY, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 0.0);
        List<ItemStack> given = Config.roll(bottled ? Config.apiaryHoney() : Config.apiaryComb(), server.getRandom());
        hold(given.isEmpty() ? null : given.getFirst().getItem());
        for (ItemStack stack : given) toBasket(server, stack);
    }

    // ---- Its screen: what it makes of a full hive (KindChoice), and how the yard is doing ----

    /** The three things a full hive can become, in the order they are tried. The cask's honey is shown as a bucket of honey. */
    private static net.minecraft.world.item.Item cask() {
        return ModRegistries.HONEY_BUCKET.get(); // (read when asked: the items are not registered yet when this class loads)
    }

    private static List<net.minecraft.world.item.Item> makes() {
        return List.of(cask(), Items.HONEY_BOTTLE, Items.HONEYCOMB);
    }
    private final java.util.Set<net.minecraft.world.item.Item> refused = new java.util.HashSet<>();

    private boolean wanted(net.minecraft.world.item.Item make) {
        return !refused.contains(make);
    }

    @Override
    public List<net.minecraft.world.item.Item> offered() {
        return makes();
    }

    @Override
    public boolean chosen(net.minecraft.world.item.Item kind) {
        return wanted(kind);
    }

    @Override
    public void toggle(int index) {
        List<net.minecraft.world.item.Item> makes = makes();
        if (index < 0 || index >= makes.size()) return;
        if (!refused.remove(makes.get(index))) refused.add(makes.get(index));
        setChanged();
        syncToClients();
        lookSoon();
    }

    @Override
    public @Nullable String warning(net.minecraft.world.item.Item kind) {
        return kind == cask() ? "poured into a cask" : kind == Items.HONEY_BOTTLE ? "needs glass bottles" : "three for a hive";
    }

    @Override
    public List<String> odds(net.minecraft.world.item.Item kind) {
        return kind == cask() && magic() ? List.of("Rifted honey: " + Math.min(100, riftLevel() * Config.riftedPercent()) + "% of the time") : List.of();
    }

    @Override
    public String chosenLabel() {
        return "Made";
    }

    @Override
    public List<String> notes() {
        return List.of(hives.isEmpty() ? "No hive: stand beehives inside the fence." : flowers.isEmpty() ? hives.size() + " hives, no flowers: no honey."
                        : hives.size() + " hives, " + flowers.size() + " flowers: honey every " + Math.max(1, honeyWait() / 20) + " s.",
                "A full hive becomes the first of these it can.", "More flowers, quicker honey (up to " + Config.apiaryMaxFlowers() + ").");
    }

    @Override
    protected void second(ServerLevel server) {}

    @Override
    public void openGui(Player player) {
        if (!isFormed() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
            super.openGui(player);
            return;
        }
        look(server);
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((containerId, inventory, opener) ->
                new ChoiceMenu(ModRegistries.CHOICE_MENU.get(), containerId, inventory, this), Component.translatable("container.alchyfarm.apiary")),
                buffer -> buffer.writeBlockPos(worldPosition));
    }

    // ---- Saving (also what clients get) ----

    private static int[] packed(List<BlockPos> squares) {
        int[] packed = new int[squares.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = pack(squares.get(i));
        return packed;
    }

    private static List<BlockPos> unpacked(int[] packed) {
        List<BlockPos> squares = new ArrayList<>(packed.length);
        for (int square : packed) squares.add(unpack(square));
        return List.copyOf(squares);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("hives", packed(hives));
        output.putIntArray("flowers", packed(flowers));
        if (!refused.isEmpty()) output.store("refused", net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(refused));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hives = unpacked(input.getIntArray("hives").orElse(new int[0]));
        flowers = unpacked(input.getIntArray("flowers").orElse(new int[0]));
        refused.clear();
        refused.addAll(input.read("refused", net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
    }
}
