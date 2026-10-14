package com.lealex.alchyfarm.block;

import com.lealex.alchyfarm.AlchyFarm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A trellis: a wooden frame stood on a square of a crop plot's field, for what hangs instead of growing from the
 * soil. Glow berries (item tag alchyfarm:trellis_plants) are set on it and a vine hangs inside the frame, which
 * bears berries in its own time (random ticks, or bone meal), lit like a cave vine's. Picking leaves the vine.
 * Trellises stack, up to MAX_STACK high: each has its own vine, and only the highest shows the frame's top (TOP).
 *
 * The crop plot's soul does all three from the stock (CropPlotBlockEntity); a player can too, by hand: right-click
 * with glow berries to set the vine, with an empty hand to pick.
 *
 * AGE: 0 the bare frame, 1 a vine, 2 the vine in berries.
 */
public class TrellisBlock extends Block implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final int VINE = 1, BERRIES = 2;
    /** How many trellises may stand on one another. */
    public static final int MAX_STACK = 3;
    /** What can be set on a trellis: glow berries. */
    public static final TagKey<Item> PLANTS_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "trellis_plants"));
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public TrellisBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(TOP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, TOP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** What picking a trellis in berries gives. */
    public static ItemStack berries(RandomSource random) {
        return new ItemStack(Items.GLOW_BERRIES, 1 + random.nextInt(2));
    }

    // ---- Stacking ----

    private static boolean isTrellis(BlockState state) {
        return state.getBlock() instanceof TrellisBlock;
    }

    // Not on a stack that is already as high as it may be
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (int down = 1; down <= MAX_STACK; down++) {
            if (!isTrellis(level.getBlockState(pos.below(down)))) return true;
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(TOP, !isTrellis(context.getLevel().getBlockState(context.getClickedPos().above())));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    // The frame's top goes when another trellis is stood on this one, and comes back when it is taken off
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbor,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return toNeighbor == Direction.UP ? state.setValue(TOP, !isTrellis(neighborState)) : state;
    }

    // ---- Bearing ----

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) == VINE;
    }

    // About one random tick in three: a few minutes, like a crop's last stage
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) == VINE && random.nextInt(3) == 0) level.setBlock(pos, state.setValue(AGE, BERRIES), Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) == VINE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, BERRIES), Block.UPDATE_CLIENTS);
    }

    // ---- By hand ----

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(AGE) != 0 || !stack.is(PLANTS_TAG)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(AGE, VINE), Block.UPDATE_CLIENTS);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(AGE) != BERRIES) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            Block.popResource(level, pos, berries(level.getRandom()));
            level.setBlock(pos, state.setValue(AGE, VINE), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
