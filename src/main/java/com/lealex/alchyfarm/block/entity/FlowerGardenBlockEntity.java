package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The flower garden: a gardener's post whose fence closes around a garden (see SoulPlotBlockEntity for the fence, the
 * stores and the soul). It is a crop plot for flowers, and shares the crop plot's map, choices and stock reading by
 * extending it: what differs is here.
 *
 * The flowers are REAL: the soul plants them from the stock on the garden's ground (item tag
 * alchyfarm:garden_flowers: every small flower, and the tall ones, which take the block above too), and they STAY.
 * Each flower gives a bloom now and then (Config flowerGarden.secondsPerBloom for each flower, so a fuller garden
 * gives more): the soul goes to one and cuts it, and a flower of that kind goes to the basket, the plant untouched.
 * Bone meal in the stock is used one piece a bloom, and the next bloom then comes in half the time.
 *
 * A kind can be ground to DYE instead (the post's screen, a list of the kinds growing: KindChoice): the bloom is
 * then given as what crafting makes of that flower alone. The field's map is on sneak + right-click, as a pond's is.
 *
 * Its errands' actions are the soul's gestures: SOW (planting), HARVEST (cutting a bloom).
 */
public class FlowerGardenBlockEntity extends CropPlotBlockEntity implements KindChoice {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "flower_garden");
    /** What a garden plants: items of this tag that place a block. */
    public static final TagKey<Item> FLOWERS_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "garden_flowers"));

    // The kinds growing in the garden (what the screen lists) and those ground to dye (both saved and synced)
    private List<Item> offered = List.of();
    private final Set<Item> dyed = new HashSet<>();

    // Not saved: when the next bloom is ready, and what crafting makes of each flower (looked up once)
    private long nextBloom = 0;
    private final Map<Item, ItemStack> dyes = new HashMap<>();

    public FlowerGardenBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.FLOWER_GARDEN_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Flower garden";
    }

    @Override
    protected String plotName() {
        return "flower garden";
    }

    // "Enhanced shroom / flower is weird": nothing it picks comes back as a rifted thing. The rift only gives more
    @Override
    public boolean riftOnlyMore() {
        return true;
    }

    @Override
    public String soulLook() {
        return "nitwit";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchyfarm.flower_garden");
    }

    @Override
    public TagKey<Item> plantTag() {
        return FLOWERS_TAG;
    }

    @Override
    public boolean plants(ItemStack stack) {
        return stack.is(FLOWERS_TAG) && stack.getItem() instanceof BlockItem;
    }

    // Flowers want no watered farmland, and there is none to trample
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
        if (baskets.isEmpty()) return "No basket: no blooms";
        return null;
    }

    // A tall flower needs the block above too
    @Override
    public boolean suits(Item plant, BlockPos square) {
        if (!super.suits(plant, square)) return false;
        BlockState planted = plantOf(plant);
        return level == null || planted == null || !(planted.getBlock() instanceof DoublePlantBlock) || level.getBlockState(world(square).above()).isAir();
    }

    @Override
    protected void place(ServerLevel server, BlockPos pos, BlockState plant) {
        if (plant.getBlock() instanceof DoublePlantBlock) DoublePlantBlock.placeAt(server, plant, pos, Block.UPDATE_ALL);
        else super.place(server, pos, plant);
    }

    // ---- Work ----

    private boolean isFlower(BlockState state) {
        return !state.isAir() && plants(new ItemStack(state.getBlock().asItem()));
    }

    /**
     * The nearest free square it has a flower for is planted. With none, once a bloom is ready and a basket has
     * room, it goes to cut one from a flower of the garden, any of them.
     */
    @Override
    protected void findWork(ServerLevel server, long now) {
        Survey survey = survey(server);
        refreshBoneMeal(survey);
        BlockPos here = soulTo(), best = null;
        double bestDistance = Double.MAX_VALUE;
        List<BlockPos> flowers = new ArrayList<>();
        List<Item> kinds = new ArrayList<>(4);
        for (BlockPos square : field()) {
            BlockPos pos = world(square);
            BlockState state = server.getBlockState(pos);
            if (isFlower(state)) {
                flowers.add(square);
                Item kind = state.getBlock().asItem();
                if (!kinds.contains(kind)) kinds.add(kind);
            } else if (state.isAir() && !survey.seeds().isEmpty() && !bare(square) && square.distSqr(here) < bestDistance
                    && seedFor(server, survey, square, pos) != null) {
                best = square;
                bestDistance = square.distSqr(here);
            }
        }
        refreshOffered(kinds);
        if (best != null) {
            go(besides(best), best, SOW, now, false, true);
            return;
        }
        if (flowers.isEmpty()) return;
        if (nextBloom == 0) nextBloom = now + wait(flowers.size(), false);
        if (now < nextBloom || !basketHasRoom(server)) return;
        BlockPos bloom = flowers.get(server.getRandom().nextInt(flowers.size()));
        go(besides(bloom), bloom, HARVEST, now, true, false);
    }

    /** Ticks until the next bloom: each flower gives one every Config flowerGarden.secondsPerBloom, half that with bone meal. */
    private long wait(int flowers, boolean mealed) {
        return hasted(Math.max(40, Config.gardenBloomSeconds() * 20L / Math.max(1, flowers) / (mealed ? 2 : 1)));
    }

    /** The cut: a bloom of the flower it stands by, as itself or as its dye, held up, then into the basket. */
    @Override
    protected void first(ServerLevel server) {
        BlockPos pos = world(soulTarget());
        BlockState state = server.getBlockState(pos);
        if (!isFlower(state)) return;
        Item flower = state.getBlock().asItem();
        Survey survey = survey(server);
        boolean mealed = survey.fertilizer() != null || endless();
        if (mealed) {
            if (!endless() && !spared(server)) survey.fertilizer().stock().removeItem(survey.fertilizer().slot(), 1);
            server.levelEvent(1505, pos, 6); // vanilla's bone meal sparkles and sound
            refreshBoneMeal(survey(server));
        }
        int flowers = 0;
        for (BlockPos square : field()) {
            if (isFlower(server.getBlockState(world(square)))) flowers++;
        }
        nextBloom = server.getGameTime() + wait(flowers, mealed);
        playSound(pos, SoundEvents.GRASS_BREAK, 0.6f, 1.5f);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.0);
        List<ItemStack> given;
        List<Config.Drop> set = configured(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (set != null) {
            given = Config.roll(set, server.getRandom());
        } else {
            ItemStack dye = dyed.contains(flower) ? dyeOf(server, flower) : ItemStack.EMPTY;
            given = List.of(dye.isEmpty() ? new ItemStack(flower) : dye.copy());
        }
        hold(given.isEmpty() ? flower : given.getFirst().getItem());
        for (ItemStack bloom : given) toBasket(server, bloom);
    }

    /** What crafting makes of this flower alone (its dye), or nothing. */
    private ItemStack dyeOf(ServerLevel server, Item flower) {
        return dyes.computeIfAbsent(flower, kind -> {
            try {
                CraftingInput alone = CraftingInput.of(1, 1, List.of(new ItemStack(kind)));
                return server.recipeAccess().getRecipeFor(RecipeType.CRAFTING, alone, server)
                        .map(found -> found.value().assemble(alone)).orElse(ItemStack.EMPTY);
            } catch (RuntimeException e) {
                AlchyFarm.LOGGER.warn("AlchyFarm: can't find the dye of {}: {}", BuiltInRegistries.ITEM.getKey(kind), e.toString());
                return ItemStack.EMPTY;
            }
        });
    }

    // What a bloom gives is read from flowerGarden.harvest
    @Override
    protected @Nullable List<Config.Drop> configured(Identifier block) {
        return Config.gardenHarvest(block);
    }

    // ---- Its screen: the kinds, each kept as a flower or ground to dye ----

    private void refreshOffered(List<Item> kinds) {
        if (kinds.equals(offered)) return;
        offered = List.copyOf(kinds);
        setChanged();
        syncToClients();
    }

    // Right-click: the blooms. Sneak + right-click, or a garden with no flower yet: the map, as a crop plot's
    @Override
    public void openGui(Player player) {
        if (!isFormed() || player.isShiftKeyDown() || offered.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            super.openGui(player);
            return;
        }
        serverPlayer.openMenu(new SimpleMenuProvider((containerId, inventory, opener) -> new ChoiceMenu(ModRegistries.CHOICE_MENU.get(), containerId, inventory, this),
                Component.translatable("container.alchyfarm.flower_garden_blooms")), buffer -> buffer.writeBlockPos(worldPosition));
    }

    @Override
    public List<Item> offered() {
        return offered;
    }

    @Override
    public boolean chosen(Item kind) {
        return dyed.contains(kind);
    }

    @Override
    public void toggle(int index) {
        if (index < 0 || index >= offered.size()) return;
        Item kind = offered.get(index);
        if (!dyed.remove(kind)) dyed.add(kind);
        setChanged();
        syncToClients();
    }

    @Override
    public String chosenLabel() {
        return "Dye";
    }

    @Override
    public List<String> notes() {
        return List.of("Planted flowers stay and give blooms.", "Dye: that kind's blooms become dye.", "Bone meal in the stock: twice as fast.",
                "Sneak + right-click: the garden map.");
    }

    // ---- Saving (also what clients get) ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("blooming", BuiltInRegistries.ITEM.byNameCodec().listOf(), offered);
        if (!dyed.isEmpty()) output.store("dyed", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(dyed));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        offered = List.copyOf(input.read("blooming", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
        dyed.clear();
        dyed.addAll(input.read("dyed", BuiltInRegistries.ITEM.byNameCodec().listOf()).orElse(List.of()));
    }
}
