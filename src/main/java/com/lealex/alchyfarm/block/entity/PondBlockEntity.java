package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import com.lealex.alchyfarm.upgrade.Rifted;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pond: an angler's post whose fence closes around water and its banks (see SoulPlotBlockEntity for the fence, the
 * stores and the soul). It is an orchard for what grows by the water, and extends it: what differs is here.
 *
 * A square is WATER when the block under it (or the square itself) holds water: a pond dug one block deep inside the
 * fence. The other squares are its banks, where the soul plants what the pond grows (item tag alchyfarm:pond_plants:
 * sugar cane) from the stock, wherever the game lets it stand, and cuts it above the foot once it is three tall, as
 * the orchard does. It works the banks hovering over the water.
 *
 * With nothing to do on the banks, and enough water (Config pond.minWater), it fishes from on top of its post: a
 * cast at some square of the water, a wait (Config pond.catchSeconds), and what the fishing loot table gives goes to
 * the basket. No rod is needed in the stock, and it never stops while a basket has room.
 */
public class PondBlockEntity extends OrchardBlockEntity implements KindChoice {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "pond");
    /** What a pond plants on its banks: items of this tag that place a block (sugar cane). */
    public static final TagKey<Item> PLANTS_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "pond_plants"));

    /** Within a fishing errand: when the float lands, and when the catch comes out (the soul's gesture follows). */
    public static final float CAST_MOMENT = 0.1F, CATCH_MOMENT = 0.86F;
    private static final int WAKE_TICKS = 60; // how long before the bite something is seen coming to the float

    // Where it is fishing, and how many more casts it gives that spot (not saved: it picks again after a reload)
    private @Nullable BlockPos spot;
    private int castsLeft = 0;

    // The cast under way: how long it takes, in hundredths of the usual (synced: the figure times its gesture by
    // it), whether a bait went on the hook, and that bait's chance of treasure
    private int castWait = 100;
    private boolean castBaited = false;
    private int castTreasure = 0;
    // What it can catch (read from its loot tables when its screen opens) and what the player has it throw back
    // (both saved and synced: the screen reads them)
    private List<Item> offered = List.of();
    private final Set<Item> refused = new HashSet<>();
    // How often each thing came up in the last sample of its own table, in thousandths of the casts (for the screen)
    private final java.util.Map<Item, Integer> odds = new java.util.HashMap<>();
    private static final int SAMPLE = 600, BAIT_SAMPLE = 300, REMEASURE = 12000; // (ten minutes)
    private long measuredAt = 0;
    // The same with the richest bait of the config on the hook (and which bait that is)
    private final java.util.Map<Item, Integer> baitOdds = new java.util.HashMap<>();
    private @Nullable Item bestBait;

    public PondBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.POND_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Pond";
    }

    @Override
    protected String plotName() {
        return "pond";
    }

    // What it plants is what it is for (sugar cane; the golden things): none of it is a leftover
    @Override
    protected boolean surplus(ItemStack stack) {
        return false;
    }

    @Override
    public String soulLook() {
        return "fisherman";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchyfarm.pond");
    }

    @Override
    public TagKey<Item> plantTag() {
        return PLANTS_TAG;
    }

    @Override
    public boolean plants(ItemStack stack) {
        // (what the banks grow is on its screen with the catch: turned off, it is neither planted nor cut)
        return stack.is(PLANTS_TAG) && stack.getItem() instanceof BlockItem && !refused.contains(stack.getItem());
    }

    @Override
    protected boolean fells(BlockState state) {
        return !refused.contains(state.getBlock().asItem());
    }

    // Every bank square is planted, where the game lets the plant stand (beside the water)
    @Override
    public boolean sownByDefault(BlockPos square) {
        return true;
    }

    // The fisherman waits and fishes from its post: it does not wander
    // A cast waits for its bite, whatever the magic
    @Override
    protected boolean instantWork() {
        return false;
    }

    @Override
    public boolean wanders() {
        return false;
    }

    // The fisherman does not stroll: with nothing to do on the banks, it fishes
    @Override
    protected boolean strolls() {
        return false;
    }

    // Fishing needs no stock
    @Override
    protected @Nullable String lacks() {
        return baskets.isEmpty() ? "No basket: no catch" : null;
    }

    @Override
    public boolean isWater(BlockPos square) {
        if (level == null) return false;
        BlockPos pos = world(square);
        return level.getFluidState(pos.below()).is(FluidTags.WATER) || level.getFluidState(pos).is(FluidTags.WATER);
    }

    // It works the banks from over the water, when the square has water beside it
    @Override
    protected BlockPos besides(BlockPos square) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = square.relative(side);
            if (!field().contains(beside) || !isWater(beside)) continue;
            double distance = beside.distSqr(soulTo());
            if (distance < bestDistance) {
                best = beside;
                bestDistance = distance;
            }
        }
        return best != null ? best : super.besides(square);
    }

    /**
     * Where in its square of water the float of the current cast lands, from the square's middle (both sides: worked
     * out from the errand's start time, so the figure's float and the server's splashes agree). Never the same spot
     * twice, and never right at the middle.
     */
    public float castX() {
        return castOffset(taskStart() * 0x9E3779B97F4A7C15L);
    }

    public float castZ() {
        return castOffset(taskStart() * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L);
    }

    private static float castOffset(long mixed) {
        mixed ^= mixed >>> 31;
        return (((mixed >>> 13) & 0x3FF) / 1023.0F - 0.5F) * 0.7F; // up to 0.35 block either way: still in the square
    }

    // ---- The errand's timing (both sides) ----

    @Override
    public int workTicks() {
        // A bite does not come by the clock: from seven tenths of the usual wait to nearly half as long again
        // and a bait shortens it
        return action() == FISH ? Math.max(40, Math.round(Config.pondCatchSeconds() * 20 * (castWait / 100.0F) * (0.7F + 0.7F * whim()))) : super.workTicks();
    }

    @Override
    protected float firstAt() {
        return action() == FISH ? CAST_MOMENT : super.firstAt();
    }

    @Override
    protected float secondAt() {
        return action() == FISH ? CATCH_MOMENT : super.secondAt();
    }

    // ---- Work ----

    /** Nothing to plant or cut: it fishes from its post, if there is water enough and room in a basket. */
    @Override
    protected void idle(ServerLevel server, long now) {
        List<BlockPos> water = new ArrayList<>();
        for (BlockPos square : field()) {
            if (isWater(square)) water.add(square);
        }
        if (water.size() >= Config.pondMinWater() && basketHasRoom(server)) {
            // It keeps to one spot for a few casts, as an angler does, then tries its luck elsewhere
            if (spot == null || castsLeft <= 0 || !water.contains(spot)) {
                BlockPos last = spot;
                for (int tries = 0; tries < 4 && (spot == null || spot.equals(last) || !water.contains(spot)); tries++) {
                    spot = water.get(server.getRandom().nextInt(water.size()));
                }
                castsLeft = 2 + server.getRandom().nextInt(4);
            }
            castsLeft--;
            bait(server);
            go(byThePost(), spot, FISH, now, true, true);
        } else {
            super.idle(server, now);
        }
    }

    /** Where the water's surface is over this square of the pond (the water is under the square, or on it). */
    private static double surface(ServerLevel server, BlockPos at) {
        return server.getFluidState(at).is(FluidTags.WATER) ? at.getY() + 0.9 : at.getY() - 0.1;
    }

    // Before the bite: a wake and bubbles coming across the water to the float, as when a player fishes
    @Override
    protected void everyTick(ServerLevel server) {
        super.everyTick(server);
        if (action() != FISH) return;
        long since = server.getGameTime() - taskStart() - travelTicks();
        int bite = Math.round(errandTicks() * CATCH_MOMENT);
        if (since < bite - WAKE_TICKS || since >= bite || since % 2 != 0) return;
        double far = (bite - since) / (double) WAKE_TICKS * 1.8, angle = Math.floorMod(taskStart() * 37L, 360L) * Math.PI / 180.0;
        BlockPos at = world(soulTarget());
        double x = at.getX() + 0.5 + castX() + Math.cos(angle) * far, z = at.getZ() + 0.5 + castZ() + Math.sin(angle) * far, y = surface(server, at);
        server.sendParticles(ParticleTypes.BUBBLE, x, y - 0.12, z, 2, 0.06, 0.0, 0.06, 0.0);
        server.sendParticles(ParticleTypes.FISHING, x, y + 0.02, z, 1, 0.04, 0.0, 0.04, 0.0);
        if (!maxed()) return;
        // At the top one cast fishes many squares: a wake comes to each of them (7 more at most, the same ones all
        // through the cast: picked from the cast's own start)
        List<BlockPos> water = new ArrayList<>();
        for (BlockPos square : field()) {
            if (isWater(square) && !square.equals(soulTarget())) water.add(square);
        }
        int spots = Math.min(Math.min(water.size(), Config.pondSpotsAtTop() - 1), 7);
        for (int i = 0; i < spots; i++) {
            int pick = (int) Math.floorMod(taskStart() * 31L + i * 17L, (long) water.size());
            BlockPos other = world(water.remove(pick));
            double turn = angle + (i + 1) * 2.1;
            double ox = other.getX() + 0.5 + Math.cos(turn) * far, oz = other.getZ() + 0.5 + Math.sin(turn) * far, oy = surface(server, other);
            server.sendParticles(ParticleTypes.BUBBLE, ox, oy - 0.12, oz, 2, 0.06, 0.0, 0.06, 0.0);
            server.sendParticles(ParticleTypes.FISHING, ox, oy + 0.02, oz, 1, 0.04, 0.0, 0.04, 0.0);
        }
    }

    /** The cast: the float lands on the water. */
    @Override
    protected void first(ServerLevel server) {
        if (action() != FISH) {
            super.first(server);
            return;
        }
        BlockPos at = world(soulTarget());
        playSound(worldPosition.above(), SoundEvents.FISHING_BOBBER_THROW, 0.6f, 0.6f);
        playSound(at, SoundEvents.FISHING_BOBBER_SPLASH, 0.25f, 1.2f);
        server.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5 + castX(), surface(server, at) + 0.05, at.getZ() + 0.5 + castZ(), 6, 0.1, 0.0, 0.1, 0.0);
    }

    /** The catch, into the basket, the first of it shown in its hands. */
    @Override
    protected void second(ServerLevel server) {
        if (action() != FISH) {
            super.second(server);
            return;
        }
        BlockPos at = world(soulTarget());
        playSound(at, SoundEvents.FISHING_BOBBER_SPLASH, 0.4f, 1.0f);
        playSound(worldPosition.above(), SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8f, 1.0f);
        server.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5 + castX(), surface(server, at) + 0.05, at.getZ() + 0.5 + castZ(), 14, 0.2, 0.0, 0.2, 0.0);
        server.sendParticles(ParticleTypes.BUBBLE, at.getX() + 0.5 + castX(), surface(server, at) - 0.2, at.getZ() + 0.5 + castZ(), 8, 0.2, 0.1, 0.2, 0.0);
        // What it keeps: one catch for the cast, and what the player has it throw back is lost with it (no second
        // try, or keeping only a rare thing would bring it up every time)
        List<ItemStack> kept = new ArrayList<>(2);
        // At the top one cast fishes every square of the pond's water at once (Config pond.spotsAtTop at most), and
        // never comes back with what is refused: each catch is rolled until something is kept
        int spots = 1;
        if (maxed()) {
            List<BlockPos> water = new ArrayList<>();
            for (BlockPos square : field()) {
                if (isWater(square)) water.add(square);
            }
            spots = Math.max(1, Math.min(water.size(), Config.pondSpotsAtTop()));
            for (int i = 0; i < Math.min(spots, 8); i++) { // the water stirs where the others come up
                BlockPos other = world(water.get(server.getRandom().nextInt(water.size())));
                server.sendParticles(ParticleTypes.SPLASH, other.getX() + 0.5, surface(server, other) + 0.05, other.getZ() + 0.5, 6, 0.25, 0.0, 0.25, 0.0);
            }
        }
        for (int spot = 0; spot < spots; spot++) {
            int before = kept.size();
            for (int tries = sureLoot() ? 64 : 1; tries > 0 && kept.size() == before; tries--) {
                for (ItemStack caught : rollCatch(server, at)) {
                    if (caught.isEmpty()) continue;
                    learn(caught.getItem()); // (something its list did not know yet: it is on the screen from now on)
                    if (!refused.contains(caught.getItem())) kept.add(caught);
                }
            }
        }
        // By magic, now and then, the water gives up a bottle of something no one could brew
        if (magic() && !refused.contains(Items.POTION) && server.getRandom().nextInt(100) < riftLevel() * Config.pondPotionPercent()) {
            ItemStack potion = Rifted.potion(server, riftLevel());
            if (!potion.isEmpty()) kept.add(potion);
        }
        Item shown = null;
        for (ItemStack caught : kept) {
            if (shown == null) shown = caught.getItem();
            toBasket(server, caught);
        }
        hold(shown);
    }

    // ---- Bait ----

    /** The quickest bait in the stock goes on the hook for the cast about to start (one piece), or none. */
    private void bait(ServerLevel server) {
        Config.Bait best = null;
        Container from = null;
        int at = -1;
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                Config.Bait bait = Config.baitOf(stock.getItem(slot));
                if (bait == null || (best != null && bait.time() >= best.time())) continue;
                best = bait;
                from = stock;
                at = slot;
            }
        }
        // The Quality Work stamp: never short of bait. The best bait there is (the quickest of the config's) is on the
        // hook at every cast, and none is taken from the stock
        if (endless()) {
            from = null;
            for (Config.Bait bait : Config.pondBaits()) {
                if (best == null || bait.time() < best.time()) best = bait;
            }
        }
        castBaited = best != null;
        castWait = best == null ? 100 : best.time();
        castTreasure = best == null ? 0 : best.treasure();
        if (from != null && !spared(server)) from.removeItem(at, 1);
    }

    private static ResourceKey<LootTable> ownTable() {
        Identifier id = Identifier.tryParse(Config.pondLootTable());
        return id == null ? BuiltInLootTables.FISHING : ResourceKey.create(Registries.LOOT_TABLE, id);
    }

    private static List<ItemStack> roll(ServerLevel server, ResourceKey<LootTable> table, BlockPos at) {
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .create(LootContextParamSets.FISHING);
        return server.getServer().reloadableRegistries().getLootTable(table).getRandomItems(params);
    }

    /**
     * One catch: the config's own list when it has one (one of them, by weight); else its loot table, or with a
     * bait on the hook the game's fish table, or its treasure table as often as the bait says.
     */
    private List<ItemStack> rollCatch(ServerLevel server, BlockPos at) {
        List<Config.Drop> catches = Config.pondCatches();
        if (!catches.isEmpty()) {
            ItemStack caught = Config.pick(catches, server.getRandom());
            return caught.isEmpty() ? List.of() : List.of(caught);
        }
        if (!castBaited) return roll(server, ownTable(), at);
        return roll(server, server.getRandom().nextInt(100) < castTreasure ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING_FISH, at);
    }

    // ---- Its screen: what it keeps of its catch (sneak and right-click: the banks' map, the crop plot's) ----

    @Override
    public void openGui(Player player) {
        if (player.isShiftKeyDown() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
            super.openGui(player);
            return;
        }
        refreshOffered(server);
        serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, opener) -> new ChoiceMenu(ModRegistries.CHOICE_MENU.get(), containerId, inventory, this),
                Component.translatable("container.alchyfarm.pond_catch")), buffer -> buffer.writeBlockPos(worldPosition));
    }

    /** Reads again what it can catch: the config's list, or its loot tables rolled a few hundred times (and the baits' tables). */
    private void refreshOffered(ServerLevel server) {
        Set<Item> kinds = new LinkedHashSet<>();
        List<Config.Drop> catches = Config.pondCatches();
        boolean listed = !catches.isEmpty();
        if (listed) {
            for (Config.Drop drop : catches) kinds.add(drop.item());
        } else {
            // The loot tables are only known by rolling them, and a rare catch does not come up in every sample:
            // what was ever seen STAYS on the list (or a rare thing turned off would drop off it and be kept again)
            kinds.addAll(offered);
            // (1,200 loot rolls: measured again only now and then, not each time someone opens the screen)
            boolean measure = odds.isEmpty() || server.getGameTime() - measuredAt >= REMEASURE || server.getGameTime() < measuredAt;
            if (measure) measuredAt = server.getGameTime();
            java.util.Map<Item, Integer> came = new java.util.HashMap<>();
            if (measure) {
            for (int i = 0; i < SAMPLE; i++) {
                for (ItemStack caught : roll(server, ownTable(), worldPosition)) {
                    if (caught.isEmpty()) continue;
                    kinds.add(caught.getItem());
                    came.merge(caught.getItem(), 1, Integer::sum);
                }
            }
            odds.clear();
            came.forEach((item, times) -> odds.put(item, Math.max(1, times * 1000 / SAMPLE)));
            // With a bait on the hook the catch is the game's fish table, or its treasure table as often as the bait
            // says: measured the same way, for the bait that brings most treasure
            baitOdds.clear();
            Config.Bait richest = null;
            for (Config.Bait bait : Config.pondBaits()) {
                if (richest == null || bait.treasure() > richest.treasure()) richest = bait;
            }
            bestBait = richest == null ? null : richest.item();
            if (richest != null) {
                java.util.Map<Item, Integer> fish = new java.util.HashMap<>(), treasure = new java.util.HashMap<>();
                for (int i = 0; i < BAIT_SAMPLE; i++) {
                    for (ItemStack caught : roll(server, BuiltInLootTables.FISHING_FISH, worldPosition)) {
                        if (!caught.isEmpty()) fish.merge(caught.getItem(), 1, Integer::sum);
                    }
                    if (richest.treasure() <= 0) continue;
                    for (ItemStack caught : roll(server, BuiltInLootTables.FISHING_TREASURE, worldPosition)) {
                        if (!caught.isEmpty()) treasure.merge(caught.getItem(), 1, Integer::sum);
                    }
                }
                int share = richest.treasure();
                fish.forEach((item, times) -> baitOdds.merge(item, times * 10 * (100 - share) / BAIT_SAMPLE, Integer::sum));
                treasure.forEach((item, times) -> baitOdds.merge(item, times * 10 * share / BAIT_SAMPLE, Integer::sum));
                baitOdds.replaceAll((item, thousandths) -> Math.max(1, thousandths));
                kinds.addAll(baitOdds.keySet());
            }
            }
        }
        if (!Config.riftPotions().isEmpty()) kinds.add(Items.POTION); // the rift's potions (and the table's bottle of water)
        // What its banks grow (sugar cane; a pack's additions to the tag), so the whole pond is set on one screen
        for (net.minecraft.core.Holder<Item> plant : BuiltInRegistries.ITEM.getTagOrEmpty(PLANTS_TAG)) kinds.add(plant.value());
        List<Item> list = List.copyOf(kinds);
        if (!list.equals(offered)) {
            offered = list;
            if (listed) refused.retainAll(kinds);
        }
        setChanged();
        syncToClients(); // (the chances were measured again: the screen gets them too)
    }

    /** How often this comes up without bait (measured), a rift potion's own chance, and what only a bait brings. */
    @Override
    public List<String> odds(Item kind) {
        List<String> told = new ArrayList<>(2);
        Integer thousandths = odds.get(kind);
        Integer baited = baitOdds.get(kind);
        if (thousandths != null) told.add("No bait: " + percent(thousandths) + " of casts");
        else if (stackIsPlant(kind)) told.add("Grown on the banks");
        else if (baited != null) told.add("No bait: never");
        if (baited != null && bestBait != null) told.add("With " + new ItemStack(bestBait).getHoverName().getString() + ": " + percent(baited) + " of casts");
        if (kind == Items.POTION && magic()) told.add("Rift potion: " + Math.min(100, riftLevel() * Config.pondPotionPercent()) + "% of casts");
        return told;
    }

    private static String percent(int thousandths) {
        return thousandths >= 100 ? thousandths / 10 + "%" : thousandths / 10 + "." + thousandths % 10 + "%";
    }

    private static boolean stackIsPlant(Item kind) {
        return new ItemStack(kind).is(PLANTS_TAG);
    }

    /** Something came up that the list does not hold: it joins it (on, until the player turns it off). */
    private void learn(Item caught) {
        if (offered.contains(caught)) return;
        List<Item> more = new ArrayList<>(offered);
        more.add(caught);
        offered = List.copyOf(more);
        setChanged();
        syncToClients();
    }

    private void sample(ServerLevel server, ResourceKey<LootTable> table, int rolls, Set<Item> into) {
        for (int i = 0; i < rolls; i++) {
            for (ItemStack caught : roll(server, table, worldPosition)) {
                if (!caught.isEmpty()) into.add(caught.getItem());
            }
        }
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

    @Override
    public String chosenLabel() {
        return "Kept";
    }

    @Override
    public List<String> notes() {
        return List.of("Off: that catch is thrown back.", "Bait in the stock: faster, no junk.", "Sneak + right-click: the banks map.");
    }

    // ---- Saving (also what clients get) ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("castWait", castWait);
        output.putBoolean("castBaited", castBaited);
        output.putInt("castTreasure", castTreasure);
        output.store("offered", BuiltInRegistries.ITEM.byNameCodec().listOf(), offered);
        int[] chances = new int[offered.size()];
        for (int i = 0; i < chances.length; i++) chances[i] = odds.getOrDefault(offered.get(i), 0);
        output.putIntArray("offeredOdds", chances);
        int[] baited = new int[offered.size()];
        for (int i = 0; i < baited.length; i++) baited[i] = baitOdds.getOrDefault(offered.get(i), 0);
        output.putIntArray("offeredBaitOdds", baited);
        if (bestBait != null) output.store("bestBait", BuiltInRegistries.ITEM.byNameCodec(), bestBait);
        if (!refused.isEmpty()) output.store("refused", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(refused));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        castWait = Math.clamp(input.getIntOr("castWait", 100), 5, 400);
        castBaited = input.getBooleanOr("castBaited", false);
        castTreasure = input.getIntOr("castTreasure", 0);
        offered = List.copyOf(input.read("offered", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
        int[] chances = input.getIntArray("offeredOdds").orElse(new int[0]);
        odds.clear();
        for (int i = 0; i < Math.min(chances.length, offered.size()); i++) {
            if (chances[i] > 0) odds.put(offered.get(i), chances[i]);
        }
        int[] baited = input.getIntArray("offeredBaitOdds").orElse(new int[0]);
        baitOdds.clear();
        for (int i = 0; i < Math.min(baited.length, offered.size()); i++) {
            if (baited[i] > 0) baitOdds.put(offered.get(i), baited[i]);
        }
        bestBait = input.read("bestBait", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        refused.clear();
        refused.addAll(input.read("refused", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
    }
}
