package com.lealex.alchyfarm.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crop of the gilded garden (gilded carrots, the gilded apple tree): a real crop on farmland, growing by the
 * game's rules in four stages, that NO item places. Only the garden's soul sows it, from its seed (a golden carrot, a golden
 * apple) and whatever else the config asks of the stock (GildedGardenBlockEntity, Config gildedGarden.seeds). Broken by hand before it is
 * grown it gives its seed back; grown, what its loot table says.
 *
 * The apple tree is the same block with another look: a small tree that stands in its one block, with two big
 * golden apples hanging under its crown at the last stage.
 */
public class GildedCropBlock extends CropBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_AGE = 3;
    private static final VoxelShape[] LOW = {Block.box(0, 0, 0, 16, 3, 16), Block.box(0, 0, 0, 16, 6, 16),
            Block.box(0, 0, 0, 16, 9, 16), Block.box(0, 0, 0, 16, 12, 16)};
    private static final VoxelShape[] TREE = {Block.box(6, 0, 6, 10, 6, 10), Block.box(5, 0, 5, 11, 11, 11),
            Block.box(1, 0, 1, 15, 16, 15), Block.box(1, 0, 1, 15, 16, 15)};

    private final Supplier<Item> seed;
    private final boolean tree;
    private final int slow;

    /**
     * @param seed what the soul sows it from (and what it is to the field's map and to pick block)
     * @param tree true for the little tree's shape
     * @param slow it grows on one of this many of the growth ticks a crop gets
     */
    public GildedCropBlock(Supplier<Item> seed, boolean tree, int slow, Properties properties) {
        super(properties);
        this.seed = seed;
        this.tree = tree;
        this.slow = Math.max(1, slow);
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }

    // No item places it: to anything that asks, it is its seed
    @Override
    public Item asItem() {
        return seed.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (tree ? TREE : LOW)[getAge(state)];
    }

    // Gold is slow to grow: with four stages instead of a crop's eight, it would otherwise be twice as quick
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(slow) == 0) super.randomTick(state, level, pos, random);
    }

    // One piece of bone meal, one stage
    @Override
    protected int getBonemealAgeIncrease(Level level) {
        return 1;
    }
}
