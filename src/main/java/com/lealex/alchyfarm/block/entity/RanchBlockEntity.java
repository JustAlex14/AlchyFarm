package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The ranch: a hitching post whose fence closes around a pen (see SoulPlotBlockEntity for the fence, the stores and
 * the soul).
 *
 * A grown animal that walks into the pen is TAKEN IN: the real animal leaves the world and the ranch keeps it as
 * data (its kind and everything the game knows about it), so a full pen costs the server nothing: no entity, no
 * thinking, no pathfinding. The client draws ONE ghost for each kind of animal in the herd, roaming the pen
 * (client/Herd). A broken hitching post keeps its herd: the item carries it (data component alchyfarm:herd) and the
 * ranch built with it again has it back. Babies are taken in as young ones (they count, and give once grown).
 * Pets, leashed and ridden animals are left alone, and so is anything beyond the ranch's limit.
 *
 * The soul keeps the trough filled from the stock, each kind of feed for itself. An animal that has not given for a
 * while, and finds its feed in the trough, gives what it would drop (its own loot table, rolled without hurting
 * it; or what the config file's ranch.drops says for its kind, which always wins) into the basket: the feed is the
 * price. With bowls in the stock a mooshroom gives mushroom stew. With empty buckets in the stock, animals that can be milked are
 * milked, each in its own time.
 *
 * Its screen (AlchyX ChoiceMenu, client/FarmChoiceScreen): the Loot tab, what the herd gives, each kept or not;
 * the Herd tab, a picture per kind: left click breeds a pair (for
 * feed, the young one counts in the herd and gives once grown) and free one (a real animal again, beside the player).
 */
public class RanchBlockEntity extends SoulPlotBlockEntity implements KindChoice {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "ranch");

    /** What an errand is for: take an animal's drops, milk it, or carry feed from the stock to the trough. */
    public static final int COLLECT = 1, MILK = 2, RESTOCK = 3;
    private static final int RESTOCK_BELOW = 8, RESTOCK_UP_TO = 32;
    private static final String FREE_UNTIL = "alchyfarm_free_until"; // on a freed animal: not taken in again before
    // What of an animal's data clients get, to draw it (its colour, its breed): the rest stays on the server
    private static final String[] LOOK_KEYS = {"Color", "Sheared", "sheared", "variant", "Variant", "Type", "MainGene", "HiddenGene", "sound_variant"};

    /** One animal of the herd. */
    private static final class Kept {
        final EntityType<?> type;
        final CompoundTag data;      // what Entity.saveWithoutId wrote when it was taken in
        long nextDrop, nextMilk;     // game times
        long nextBreed;              // game time it can breed again
        long grownAt;                // game time a young one born here is grown (it gives nothing before)
        @Nullable Animal body;       // made from the data when needed, never in the world: what it eats, what it drops
        boolean broken;              // its data no longer makes an animal (a mod removed?): kept, never worked

        Kept(EntityType<?> type, CompoundTag data) {
            this.type = type;
            this.data = data;
        }
    }

    private final List<Kept> herd = new ArrayList<>();
    // Clients: one entry for each KIND of animal in the herd, in the herd's order: its kind under "id", how many
    // under "count", and the LOOK_KEYS of the first one's data
    private List<CompoundTag> looks = List.of();
    private int errandAnimal = -1;      // the errand's animal (server only: an errand cut by a reload is just dropped)
    private String errandKind = "";     // its kind's id (synced: that kind's ghost comes to the soul), "" for none
    private String lastPetKind = "";    // the kind it petted last, and where (not saved): the next is another, elsewhere
    private @Nullable BlockPos lastPetSquare;
    private int youngSeen = -1;         // how many young the clients were last told of
    private boolean savingForClients;   // saveAdditional writes the looks instead of the whole herd

    public RanchBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.RANCH_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Ranch";
    }

    @Override
    protected String plotName() {
        return "ranch";
    }

    @Override
    protected int maxSize() {
        return Config.maxPenSize();
    }

    @Override
    public String soulLook() {
        return "shepherd";
    }

    // ---- The errand's timing (both sides) ----

    @Override
    public int workTicks() {
        int base = Config.soulWorkTicks();
        return action() == RESTOCK ? base : base * 9 / 5;
    }

    // Taking and milking: bent to the animal, then upright with what it gave in both hands. Feeding: bent to the trough.
    @Override
    public int gesture() {
        return action() == RESTOCK ? SOW : action() == 0 ? 0 : HARVEST;
    }

    @Override
    protected float firstAt() {
        return action() == RESTOCK ? 0.6F : HARVEST_MOMENT;
    }

    @Override
    protected float secondAt() {
        return 1.0F;
    }

    @Override
    protected @Nullable String lacks() {
        if (troughs.isEmpty()) return "No trough: animals not fed";
        if (baskets.isEmpty()) return "No basket: no drops";
        return null;
    }

    // ---- The herd (both sides) ----

    /** Clients: one entry for each kind of animal in the herd ("id", "count", and a few fields of its look). */
    public List<CompoundTag> looks() {
        return looks;
    }

    /** The id of the kind of animal the current errand is for (its ghost comes to soulTarget()), or "". */
    public String errandKind() {
        return errandKind;
    }

    public int herdSize() {
        return herd.size();
    }

    /** The herd's kinds, in the herd's order (the order of looks()). */
    private List<EntityType<?>> kinds() {
        List<EntityType<?>> kinds = new ArrayList<>(4);
        for (Kept kept : herd) {
            if (!kinds.contains(kept.type)) kinds.add(kept.type);
        }
        return kinds;
    }

    private static void tell(Player player, String says) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal("Ranch: " + says), true);
    }

    // ---- The screen's buttons (server) ----

    /**
     * Two grown animals of this kind have a young one, for twice what breeding them by hand costs: four pieces of
     * their feed, from the stocks or the troughs. The young one is of the herd at once, and gives nothing until it
     * is grown. The parents rest before they breed again.
     */
    public void breed(Player player, int kind) {
        if (!(level instanceof ServerLevel server)) return;
        List<EntityType<?>> kinds = kinds();
        if (kind < 0 || kind >= kinds.size()) return;
        EntityType<?> type = kinds.get(kind);
        long now = server.getGameTime();
        if (herd.size() >= Config.maxRanchAnimals()) {
            tell(player, "it is full (" + herd.size() + " animals)");
            return;
        }
        Kept mother = null, father = null;
        int grown = 0;
        for (Kept kept : herd) {
            if (kept.type != type || now < kept.grownAt || body(server, kept) == null) continue;
            grown++;
            if (now < kept.nextBreed) continue;
            if (mother == null) mother = kept;
            else if (father == null) father = kept;
        }
        if (grown < 2) {
            tell(player, "it takes two grown ones");
            return;
        }
        if (mother == null || father == null) {
            tell(player, "they are not ready to breed again yet");
            return;
        }
        Animal first = body(server, mother), second = body(server, father);
        int price = Config.feedPerBreeding();
        Predicate<ItemStack> feed = first::isFood;
        if (count(server, stocks, feed) + count(server, troughs, feed) < price) {
            tell(player, "breeding them takes " + price + " of their feed, in the stock or the trough");
            return;
        }
        AgeableMob young = first.getBreedOffspring(server, second);
        if (young == null) {
            tell(player, "these two have no young together");
            return;
        }
        for (int paid = 0; paid < price; paid++) {
            Slot slot = find(server, stocks, feed);
            if (slot == null) slot = find(server, troughs, feed);
            if (slot == null) break;
            slot.store().removeItem(slot.slot(), 1);
        }
        young.setAge(0); // kept as the grown animal it will be: grownAt says when
        TagValueOutput written = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        young.saveWithoutId(written);
        Kept born = new Kept(young.getType(), written.buildResult());
        born.grownAt = now + Config.youngGrowSeconds() * 20L;
        herd.add(born);
        mother.nextBreed = father.nextBreed = now + Config.breedRestSeconds() * 20L;
        BlockPos where = world(home(kind));
        server.sendParticles(ParticleTypes.HEART, where.getX() + 0.5, where.getY() + 1.2, where.getZ() + 0.5, 7, 0.5, 0.4, 0.5, 0.0);
        tell(player, "a young " + type.getDescription().getString() + " is born");
        setChanged();
        syncToClients();
    }

    /**
     * One animal of this kind leaves the herd and is a real animal again, beside the player. It is not taken in
     * again for a while, so it can be led out of the pen.
     */
    public void free(Player player, int kind) {
        if (!(level instanceof ServerLevel server)) return;
        List<EntityType<?>> kinds = kinds();
        if (kind < 0 || kind >= kinds.size()) return;
        EntityType<?> type = kinds.get(kind);
        Kept leaving = null;
        for (Kept kept : herd) { // a grown one rather than a young one, the last that came
            if (kept.type == type && (leaving == null || server.getGameTime() >= kept.grownAt)) leaving = kept;
        }
        if (leaving == null) return;
        Entity back = make(server, leaving);
        if (back == null) {
            tell(player, "this one can't be brought back");
            return;
        }
        if (server.getGameTime() < leaving.grownAt && back instanceof AgeableMob young) young.setBaby(true);
        back.setPos(player.getX(), player.getY(), player.getZ());
        back.getPersistentData().putLong(FREE_UNTIL, server.getGameTime() + Config.freedSeconds() * 20L);
        server.addFreshEntity(back);
        herd.remove(leaving);
        errandAnimal = -1; // the herd's numbers moved
        setChanged();
        syncToClients();
    }

    /** The number of this kind among the herd's kinds, in the herd's order (the order of looks()). */
    private int kindIndex(EntityType<?> type) {
        List<EntityType<?>> kinds = new ArrayList<>(4);
        for (Kept kept : herd) {
            if (kept.type == type) return kinds.size();
            if (!kinds.contains(kept.type)) kinds.add(kept.type);
        }
        return kinds.size();
    }

    /** The pen square where the ghost of the kind of this number starts, and where the soul meets it (both sides). */
    public BlockPos home(int index) {
        List<BlockPos> field = field();
        return field.isEmpty() ? BlockPos.ZERO : field.get(Math.floorMod(index * 7 + 3, field.size()));
    }

    // ---- Taking animals in, giving them back ----

    private static boolean canBeTakenIn(Animal animal, long now) {
        return now >= animal.getPersistentData().getLongOr(FREE_UNTIL, 0L) && animal.isAlive() && !animal.isLeashed() && !animal.isPassenger() && !animal.isVehicle()
                && !(animal instanceof TamableAnimal pet && pet.isTame());
    }

    /** Real grown animals standing in the pen leave the world and join the herd, as far as the ranch has room. */
    private void takeIn(ServerLevel server) {
        List<BlockPos> field = field();
        int room = Config.maxRanchAnimals() - herd.size();
        if (room <= 0 || field.isEmpty()) return;
        BlockPos first = field.getFirst();
        int minX = first.getX(), maxX = minX, minZ = first.getZ(), maxZ = minZ;
        for (BlockPos square : field) {
            minX = Math.min(minX, square.getX());
            maxX = Math.max(maxX, square.getX());
            minZ = Math.min(minZ, square.getZ());
            maxZ = Math.max(maxZ, square.getZ());
        }
        BlockPos at = worldPosition;
        AABB box = new AABB(at.getX() + minX, at.getY() - 1, at.getZ() + minZ, at.getX() + maxX + 1, at.getY() + 3, at.getZ() + maxZ + 1);
        List<Animal> arrived = server.getEntitiesOfClass(Animal.class, box,
                animal -> canBeTakenIn(animal, server.getGameTime()) && field.contains(square(animal.blockPosition())));
        if (arrived.isEmpty()) return;
        for (Animal animal : arrived) {
            if (room-- <= 0) break;
            // A baby is kept as the grown animal it will be, with the time it still has to grow
            int stillGrowing = animal.isBaby() ? Math.max(0, -animal.getAge()) : 0;
            if (animal.isBaby()) animal.setAge(0);
            TagValueOutput written = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            animal.saveWithoutId(written);
            Kept taken = new Kept(animal.getType(), written.buildResult());
            taken.grownAt = stillGrowing > 0 ? server.getGameTime() + stillGrowing : 0L;
            herd.add(taken);
            server.sendParticles(ParticleTypes.SOUL, animal.getX(), animal.getY() + animal.getBbHeight() * 0.5, animal.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            animal.discard();
        }
        playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 0.7f, 1.3f);
        setChanged();
        syncToClients();
    }

    /** The animal this data makes, held outside the world. Null (and marked) if it no longer makes one. */
    private @Nullable Animal body(ServerLevel server, Kept kept) {
        if (kept.body != null || kept.broken) return kept.body;
        if (make(server, kept) instanceof Animal made) kept.body = made;
        else kept.broken = true;
        return kept.body;
    }

    private static @Nullable Entity make(ServerLevel server, Kept kept) {
        try {
            Entity made = kept.type.create(server, EntitySpawnReason.LOAD);
            if (made != null) made.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), kept.data));
            return made;
        } catch (RuntimeException e) {
            AlchyFarm.LOGGER.warn("AlchyFarm: a ranch animal ({}) could not be made again: {}", EntityType.getKey(kept.type), e.toString());
            return null;
        }
    }

    // ---- A broken post keeps its herd: block entity to item and back (vanilla's implicit components) ----

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        List<CompoundTag> carried = components.get(ModRegistries.HERD.get());
        if (carried != null) readHerd(carried);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!herd.isEmpty()) components.set(ModRegistries.HERD.get(), writeHerd(false));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("herd");
    }

    // ---- The stores ----

    private boolean herdEats(ServerLevel server, ItemStack stack) {
        for (Kept kept : herd) {
            Animal body = body(server, kept);
            if (body != null && body.isFood(stack)) return true;
        }
        return false;
    }

    private record Slot(Container store, int slot) {}

    /** The first slot of these stores whose stack passes, or null. */
    private static @Nullable Slot find(ServerLevel server, List<BlockPos> stores, Predicate<ItemStack> wanted) {
        for (BlockPos pos : stores) {
            if (!(server.getBlockEntity(pos) instanceof Container store)) continue;
            for (int slot = 0; slot < store.getContainerSize(); slot++) {
                ItemStack stack = store.getItem(slot);
                if (!stack.isEmpty() && wanted.test(stack)) return new Slot(store, slot);
            }
        }
        return null;
    }

    private static int count(ServerLevel server, List<BlockPos> stores, Predicate<ItemStack> wanted) {
        int count = 0;
        for (BlockPos pos : stores) {
            if (!(server.getBlockEntity(pos) instanceof Container store)) continue;
            for (int slot = 0; slot < store.getContainerSize(); slot++) {
                ItemStack stack = store.getItem(slot);
                if (!stack.isEmpty() && wanted.test(stack)) count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * A stock slot of feed the trough is short of: something the herd eats, of which the troughs hold fewer than
     * RESTOCK_BELOW. Each kind is counted for itself: carrots in the trough do not stand for the cows' wheat.
     */
    private @Nullable Slot feedToCarry(ServerLevel server) {
        if (troughs.isEmpty()) return null;
        return find(server, stocks, stack -> herdEats(server, stack)
                && count(server, troughs, inTrough -> inTrough.is(stack.getItem())) < RESTOCK_BELOW);
    }

    // ---- Work ----

    @Override
    protected void findWork(ServerLevel server, long now) {
        takeIn(server);
        if (herd.isEmpty()) return;
        int young = 0;
        for (Kept kept : herd) {
            if (now < kept.grownAt) young++;
        }
        if (young != youngSeen) { // one has grown: the screen's numbers follow
            youngSeen = young;
            syncToClients();
        }

        // The trough first
        if (feedToCarry(server) != null) {
            BlockPos trough = square(troughs.getFirst());
            errandAnimal = -1;
            errandKind = "";
            go(nearest(trough.getX(), trough.getZ()), trough, RESTOCK, now, true, false);
            return;
        }

        // A full basket stops the drops, the buckets and the bowls, but not the pouring into casks
        boolean room = basketHasRoom(server);
        boolean buckets = room && find(server, stocks, stack -> stack.is(Items.BUCKET)) != null;
        boolean bowls = room && find(server, stocks, stack -> stack.is(Items.BOWL)) != null;
        // A cask joined to the pen takes milk and stew with no bucket or bowl at all
        boolean milkCask = !casks.isEmpty() && caskRoom(server, net.neoforged.neoforge.common.NeoForgeMod.MILK.get()) >= MILKING;
        boolean stewCask = !casks.isEmpty() && caskRoom(server, ModRegistries.MUSHROOM_STEW.get()) >= STEWING;
        int perDrop = Config.feedPerDrop();
        BlockPos here = soulTo();
        int best = -1, bestAction = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < herd.size(); i++) {
            Kept kept = herd.get(i);
            if (now < kept.grownAt) continue; // still young
            boolean dropDue = room && now >= kept.nextDrop, milkDue = (buckets || bowls || milkCask || stewCask) && now >= kept.nextMilk;
            if (!dropDue && !milkDue) continue;
            double distance = home(kindIndex(kept.type)).distSqr(here);
            if (distance >= bestDistance) continue;
            Animal body = body(server, kept);
            if (body == null) continue;
            int needed = 0;
            if (dropDue && (endless() || count(server, troughs, body::isFood) >= perDrop)) needed = COLLECT; // (Quality Work: never short of feed)
            else if (milkDue && (((buckets || milkCask) && canBeMilked(body)) || ((bowls || stewCask) && body instanceof MushroomCow))) needed = MILK;
            if (needed == 0) continue;
            best = i;
            bestAction = needed;
            bestDistance = distance;
        }
        if (best < 0) return;
        errandAnimal = best;
        errandKind = EntityType.getKey(herd.get(best).type).toString();
        BlockPos where = home(kindIndex(herd.get(best).type)); // its kind's ghost walks there to meet the soul
        go(besides(where), where, bestAction, now, true, false);
    }

    // With nothing to do it goes to one of its animals, whose ghost comes to meet it
    @Override
    protected @Nullable BlockPos strollTo(ServerLevel server) {
        if (herd.isEmpty()) return super.strollTo(server);
        // One of its kinds, not the one it petted last when it has another, met on some square of the pen, not the
        // last one either (the kind's ghost walks to wherever the soul goes)
        List<EntityType<?>> kinds = kinds();
        EntityType<?> kind = kinds.get(server.getRandom().nextInt(kinds.size()));
        for (int tries = 0; tries < 4 && kinds.size() > 1 && EntityType.getKey(kind).toString().equals(lastPetKind); tries++) {
            kind = kinds.get(server.getRandom().nextInt(kinds.size()));
        }
        List<BlockPos> pen = field();
        BlockPos where = pen.get(server.getRandom().nextInt(pen.size()));
        for (int tries = 0; tries < 6 && pen.size() > 1 && (where.equals(lastPetSquare) || where.distSqr(soulTo()) > 36); tries++) {
            where = pen.get(server.getRandom().nextInt(pen.size()));
        }
        errandAnimal = -1;
        errandKind = lastPetKind = EntityType.getKey(kind).toString();
        lastPetSquare = where;
        return where;
    }

    // and pets it: hearts, and its voice
    @Override
    protected void tended(ServerLevel server, BlockPos square) {
        BlockPos where = world(square);
        for (Kept kept : herd) {
            if (!EntityType.getKey(kept.type).toString().equals(errandKind)) continue;
            Animal body = body(server, kept);
            server.sendParticles(ParticleTypes.HEART, where.getX() + 0.5, where.getY() + (body == null ? 1.0 : body.getBbHeight() + 0.2),
                    where.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
            if (body != null) body.playAmbientSound();
            return;
        }
    }

    private static boolean canBeMilked(Animal animal) {
        return animal instanceof AbstractCow || animal instanceof Goat;
    }

    @Override
    protected void first(ServerLevel server) {
        if (action() == RESTOCK) {
            restock(server);
            return;
        }
        if (errandAnimal < 0 || errandAnimal >= herd.size()) return;
        Kept kept = herd.get(errandAnimal);
        Animal body = body(server, kept);
        if (body == null) return;
        BlockPos where = world(soulTarget());
        body.setPos(where.getX() + 0.5, where.getY(), where.getZ() + 0.5); // where its voice and its drops come from
        long now = server.getGameTime();
        if (action() == MILK) {
            // Into a cask first (no vessel needed): a mooshroom's stew, else milk. By magic, with the rift's chance,
            // rifted stew or enchanted milk, each into a cask of its own
            if (pour(server, body, where, kept, now)) return;
            if (!basketHasRoom(server)) return;
            // A mooshroom with a bowl at hand gives mushroom stew. Otherwise, and for the others, a bucket of milk.
            Slot bowl = body instanceof MushroomCow ? find(server, stocks, stack -> stack.is(Items.BOWL)) : null;
            Slot bucket = bowl != null ? bowl : find(server, stocks, stack -> stack.is(Items.BUCKET));
            if (bucket == null) return;
            Item given = bowl != null ? Items.MUSHROOM_STEW : Items.MILK_BUCKET;
            if (refused.contains(given)) return; // (turned off on its screen)
            bucket.store().removeItem(bucket.slot(), 1);
            toBasket(server, new ItemStack(given));
            hold(given);
            playSound(where, body instanceof Goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK, 1.0f, 1.0f);
            kept.nextMilk = now + hasted(Config.secondsPerMilk() * 20L);
            return;
        }
        // Its feed is eaten from the trough, and it gives what it would drop, unharmed
        // (the Quality Work stamp: it gives as if its trough were always full, and none is eaten)
        int perDrop = endless() ? 0 : Config.feedPerDrop();
        if (count(server, troughs, body::isFood) < perDrop) return;
        for (int eaten = 0; eaten < perDrop; eaten++) {
            Slot feed = find(server, troughs, body::isFood);
            if (feed == null) break;
            if (!spared(server)) feed.store().removeItem(feed.slot(), 1);
        }
        kept.nextDrop = now + hasted(Config.secondsPerDrop() * 20L); // (the haste stamp: sooner ready again)
        Optional<ResourceKey<LootTable>> table = body.getLootTable();
        Item shown = null;
        List<Config.Drop> configured = Config.ranchDrops(EntityType.getKey(kept.type));
        if (configured != null) { // the config file says what this kind gives: it wins over the loot table
            for (Config.Drop drop : configured) {
                if (drop.chance() < 100 && server.getRandom().nextInt(100) >= drop.chance()) continue;
                int count = drop.least() + server.getRandom().nextInt(drop.most() - drop.least() + 1);
                if (count <= 0 || refused.contains(drop.item())) continue;
                if (shown == null) shown = drop.item();
                while (count > 0) { // more than a stack is several stacks
                    ItemStack stack = new ItemStack(drop.item(), Math.min(count, drop.item().getDefaultMaxStackSize()));
                    count -= stack.getCount();
                    toBasket(server, stack);
                }
            }
        } else if (table.isPresent()) {
            LootParams params = new LootParams.Builder(server)
                    .withParameter(LootContextParams.THIS_ENTITY, body)
                    .withParameter(LootContextParams.ORIGIN, body.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().generic())
                    .create(LootContextParamSets.ENTITY);
            for (ItemStack drop : server.getServer().reloadableRegistries().getLootTable(table.get()).getRandomItems(params)) {
                if (drop.isEmpty() || refused.contains(drop.getItem())) continue;
                if (shown == null) shown = drop.getItem();
                toBasket(server, drop);
            }
        }
        hold(shown);
        server.sendParticles(ParticleTypes.HEART, where.getX() + 0.5, where.getY() + body.getBbHeight() + 0.2, where.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
        body.playAmbientSound();
    }

    @Override
    protected void second(ServerLevel server) {}

    /** Feed from the stocks into the troughs: every kind the herd eats and the trough is short of, up to a fill. */
    private void restock(ServerLevel server) {
        boolean moved = false;
        for (int kinds = 0; kinds < 16; kinds++) {
            Slot feed = feedToCarry(server);
            if (feed == null) break;
            Item item = feed.store().getItem(feed.slot()).getItem();
            int have = count(server, troughs, inTrough -> inTrough.is(item));
            ItemStack taken = feed.store().removeItem(feed.slot(), RESTOCK_UP_TO - have);
            if (taken.isEmpty()) break;
            int carried = taken.getCount();
            ItemStack left = put(server, troughs, taken);
            moved |= carried > left.getCount();
            if (!left.isEmpty()) { // the troughs are full: back where it came from
                ItemStack lost = put(server, stocks, left);
                if (!lost.isEmpty()) toBasket(server, lost);
                break;
            }
        }
        if (moved && !troughs.isEmpty()) playSound(troughs.getFirst(), SoundEvents.COMPOSTER_FILL, 1.0f, 0.8f);
    }

    // ---- The player ----

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) return;
        refreshLoot(server);
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((containerId, inventory, opener) ->
                new ChoiceMenu(ModRegistries.CHOICE_MENU.get(), containerId, inventory, this), Component.translatable("container.alchyfarm.ranch")),
                buffer -> buffer.writeBlockPos(worldPosition));
    }

    // ---- Its screen: the Loot tab (KindChoice: what the herd gives, each kept or not) and the Herd tab ----

    /** The Herd tab's buttons: breed a pair of the kind of this number (its place in looks()), or set one free. */
    public static final int BREED_BUTTON = 20000, FREE_BUTTON = 21000;
    private static final int LOOT_ROLLS = 120;

    private List<Item> loot = List.of();                                  // what the herd can give (synced)
    private final java.util.Map<Item, List<String>> lootLines = new java.util.HashMap<>(); // per kind: "Cow x5: 66% a drop"
    private final java.util.Set<Item> refused = new java.util.HashSet<>(); // turned off: never put in the basket

    /** What each kind of the herd gives, its chance a drop, and how many of that kind are in the pen. */
    private void refreshLoot(ServerLevel server) {
        java.util.LinkedHashMap<Item, List<String>> found = new java.util.LinkedHashMap<>();
        for (EntityType<?> type : kinds()) {
            int count = 0;
            Kept sample = null;
            for (Kept kept : herd) {
                if (kept.type != type) continue;
                count++;
                if (sample == null) sample = kept;
            }
            Animal body = sample == null ? null : body(server, sample);
            if (body == null) continue;
            String who = body.getType().getDescription().getString() + " x" + count + ": ";
            java.util.Map<Item, Integer> times = new java.util.LinkedHashMap<>();
            List<Config.Drop> configured = Config.ranchDrops(EntityType.getKey(type));
            if (configured != null) {
                for (Config.Drop drop : configured) times.merge(drop.item(), drop.chance() * LOOT_ROLLS / 100, Integer::sum);
            } else {
                for (int i = 0; i < LOOT_ROLLS; i++) {
                    java.util.Set<Item> once = new java.util.HashSet<>();
                    for (ItemStack drop : rollDrops(server, body)) {
                        if (!drop.isEmpty()) once.add(drop.getItem());
                    }
                    for (Item item : once) times.merge(item, 1, Integer::sum);
                }
            }
            times.forEach((item, hits) -> found.computeIfAbsent(item, key -> new ArrayList<>())
                    .add(who + Math.max(1, Math.round(hits * 100.0F / LOOT_ROLLS)) + "% a drop"));
            if (canBeMilked(body)) found.computeIfAbsent(Items.MILK_BUCKET, key -> new ArrayList<>()).add(who + "with buckets in the Stock, or a Cask");
            if (body instanceof MushroomCow) found.computeIfAbsent(Items.MUSHROOM_STEW, key -> new ArrayList<>()).add(who + "with bowls in the Stock, or a Cask");
        }
        loot = List.copyOf(found.keySet());
        lootLines.clear();
        lootLines.putAll(found);
        setChanged();
        syncToClients();
    }

    private static final int MILKING = 1000, STEWING = 250; // mB a milking pours: a bucket of milk, a bowl of stew

    /** Pours this animal's milk or stew into a cask, if one can take it. True if it did. */
    private boolean pour(ServerLevel server, Animal body, BlockPos where, Kept kept, long now) {
        if (casks.isEmpty()) return false;
        boolean rift = riftedChance() > 0 && server.getRandom().nextInt(100) < riftedChance();
        int tier = ModRegistries.brewTier(riftLevel()) - 1; // (each tier into a cask of its own)
        net.minecraft.world.level.material.Fluid poured = null;
        int amount = 0;
        if (body instanceof MushroomCow && !refused.contains(Items.MUSHROOM_STEW)) {
            for (net.minecraft.world.level.material.Fluid stew : rift ? List.of(ModRegistries.RIFTED_STEW.get(tier).fluid(), ModRegistries.MUSHROOM_STEW.get())
                    : List.<net.minecraft.world.level.material.Fluid>of(ModRegistries.MUSHROOM_STEW.get())) {
                if (caskRoom(server, stew) >= STEWING) {
                    poured = stew;
                    amount = STEWING;
                    break;
                }
            }
        }
        if (poured == null && canBeMilked(body) && !refused.contains(Items.MILK_BUCKET)) {
            for (net.minecraft.world.level.material.Fluid milk : rift ? List.of(ModRegistries.RIFTED_MILK.get(tier).fluid(), net.neoforged.neoforge.common.NeoForgeMod.MILK.get())
                    : List.<net.minecraft.world.level.material.Fluid>of(net.neoforged.neoforge.common.NeoForgeMod.MILK.get())) {
                if (caskRoom(server, milk) >= MILKING) {
                    poured = milk;
                    amount = MILKING;
                    break;
                }
            }
        }
        if (poured == null) return false;
        toCasks(server, poured, amount);
        hold(poured == ModRegistries.MUSHROOM_STEW.get() || poured == ModRegistries.RIFTED_STEW.get(tier).fluid() ? Items.MUSHROOM_STEW : Items.MILK_BUCKET);
        playSound(where, body instanceof Goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK, 1.0f, 1.0f);
        kept.nextMilk = now + hasted(Config.secondsPerMilk() * 20L);
        return true;
    }

    /** One roll of this animal's own loot table, nobody hurt. */
    private static List<ItemStack> rollDrops(ServerLevel server, Animal body) {
        Optional<ResourceKey<LootTable>> table = body.getLootTable();
        if (table.isEmpty()) return List.of();
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.THIS_ENTITY, body)
                .withParameter(LootContextParams.ORIGIN, body.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        return server.getServer().reloadableRegistries().getLootTable(table.get()).getRandomItems(params);
    }

    @Override
    public List<Item> offered() {
        return loot;
    }

    @Override
    public boolean chosen(Item kind) {
        return !refused.contains(kind);
    }

    @Override
    public void toggle(int index) {
        if (index < 0 || index >= loot.size()) return;
        if (!refused.remove(loot.get(index))) refused.add(loot.get(index));
        setChanged();
        syncToClients();
    }

    @Override
    public List<String> odds(Item kind) {
        return lootLines.getOrDefault(kind, List.of());
    }

    @Override
    public String chosenLabel() {
        return "Kept";
    }

    @Override
    public List<String> notes() {
        int young = 0;
        for (CompoundTag look : looks) young += look.getIntOr("young", 0);
        return List.of(herdSize() + " animals of " + Config.maxRanchAnimals() + (young > 0 ? ", " + young + " still young" : "") + ".",
                "Herd tab: breed or free them.");
    }

    // The Herd tab's buttons, and the stamps' letter
    @Override
    public boolean button(Player player, int id, ItemStack carried) {
        if (id >= FREE_BUTTON && id < FREE_BUTTON + 1000) {
            free(player, id - FREE_BUTTON);
            return true;
        }
        if (id >= BREED_BUTTON && id < BREED_BUTTON + 1000) {
            breed(player, id - BREED_BUTTON);
            return true;
        }
        return super.button(player, id, carried);
    }

    // ---- Saving. Clients only get how the herd looks, never the animals' whole data ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        savingForClients = true;
        try {
            return super.getUpdateTag(registries);
        } finally {
            savingForClients = false;
        }
    }

    /** The herd as tags: every animal with its data (to save, to carry in the item), or one look for each kind. */
    private List<CompoundTag> writeHerd(boolean looksOnly) {
        List<CompoundTag> written = new ArrayList<>(looksOnly ? 4 : herd.size());
        for (Kept kept : herd) {
            String id = EntityType.getKey(kept.type).toString();
            CompoundTag tag = null;
            if (looksOnly) {
                for (CompoundTag earlier : written) {
                    if (earlier.getStringOr("id", "").equals(id)) tag = earlier;
                }
                if (tag != null) { // one more of a kind already there
                    tag.putInt("count", tag.getIntOr("count", 1) + 1);
                    if (young(kept)) tag.putInt("young", tag.getIntOr("young", 0) + 1);
                    continue;
                }
            }
            tag = new CompoundTag();
            tag.putString("id", id);
            if (looksOnly) {
                tag.putInt("count", 1);
                if (young(kept)) tag.putInt("young", 1);
                for (String key : LOOK_KEYS) {
                    Tag look = kept.data.get(key);
                    if (look != null) tag.put(key, look.copy());
                }
            } else {
                tag.put("data", kept.data);
                tag.putLong("nextDrop", kept.nextDrop);
                tag.putLong("nextMilk", kept.nextMilk);
                tag.putLong("nextBreed", kept.nextBreed);
                tag.putLong("grownAt", kept.grownAt);
            }
            written.add(tag);
        }
        return written;
    }

    private boolean young(Kept kept) {
        return level != null && level.getGameTime() < kept.grownAt;
    }

    private void readHerd(List<CompoundTag> saved) {
        herd.clear();
        for (CompoundTag tag : saved) {
            Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) continue; // its mod is gone
            Kept kept = new Kept(type, tag.getCompoundOrEmpty("data"));
            kept.nextDrop = tag.getLongOr("nextDrop", 0L);
            kept.nextMilk = tag.getLongOr("nextMilk", 0L);
            kept.nextBreed = tag.getLongOr("nextBreed", 0L);
            kept.grownAt = tag.getLongOr("grownAt", 0L);
            herd.add(kept);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(savingForClients ? "looks" : "herd", CompoundTag.CODEC.listOf(), writeHerd(savingForClients));
        output.putString("errandKind", errandKind);
        output.store("loot", BuiltInRegistries.ITEM.byNameCodec().listOf(), loot);
        List<String> lines = new ArrayList<>(loot.size());
        for (Item item : loot) lines.add(String.join("\n", lootLines.getOrDefault(item, List.of())));
        output.store("lootLines", com.mojang.serialization.Codec.STRING.listOf(), lines);
        if (!refused.isEmpty()) output.store("refused", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(refused));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        errandKind = input.getStringOr("errandKind", "");
        loot = List.copyOf(input.read("loot", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
        List<String> lines = input.read("lootLines", com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of());
        lootLines.clear();
        for (int i = 0; i < Math.min(lines.size(), loot.size()); i++) {
            if (!lines.get(i).isEmpty()) lootLines.put(loot.get(i), List.of(lines.get(i).split("\n")));
        }
        refused.clear();
        refused.addAll(input.read("refused", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
        // The same herd keeps the same list: the client's stand-ins are only made again when it changes
        List<CompoundTag> seen = input.read("looks", CompoundTag.CODEC.listOf()).orElse(List.of());
        if (!seen.equals(looks)) looks = List.copyOf(seen);
        input.read("herd", CompoundTag.CODEC.listOf()).ifPresent(this::readHerd); // absent in a client's update
    }
}
