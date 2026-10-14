package com.lealex.alchyfarm.block;

import com.lealex.alchyfarm.block.entity.CropPlotBlockEntity;
import com.lealex.alchyfarm.block.entity.RanchBlockEntity;
import com.lealex.alchyx.block.MultiblockCoreBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A workstation's core: an old carved post found in the hut (the crop plot's carved stake, the ranch's hitching
 * post). It is a post of the workstation's fence: it reaches out to the fences, gates, walls and stores beside it,
 * and wooden fences reach back (it is in the wooden fences tag). The fence that starts at it and closes around some
 * ground makes that ground the workstation's.
 */
public class CarvedStakeBlock extends MultiblockCoreBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST,
            SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape COLLISION = Block.box(5, 0, 5, 11, 24, 11); // as tall as a fence: nothing hops in

    private final BiFunction<BlockPos, BlockState, BlockEntity> workstation;

    /** @param workstation makes the block entity of the workstation this post is the core of */
    public CarvedStakeBlock(BiFunction<BlockPos, BlockState, BlockEntity> workstation, Properties properties) {
        super(properties);
        this.workstation = workstation;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return workstation.apply(pos, state);
    }

    // Creative players break blocks without drops: a post that holds a herd drops anyway, or the animals would be lost
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof RanchBlockEntity ranch && ranch.herdSize() > 0) {
            ItemStack stack = new ItemStack(this);
            stack.applyComponents(ranch.collectComponents());
            ItemEntity dropped = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            dropped.setDefaultPickUpDelay();
            level.addFreshEntity(dropped);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    private static BooleanProperty arm(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    /** True if the stake reaches out to this neighbor on that side. */
    private static boolean joins(BlockState neighbor, Direction side) {
        if (neighbor.getBlock() instanceof PlotStoreBlock || neighbor.getBlock() instanceof CaskBlock) return true;
        if (!neighbor.is(CropPlotBlockEntity.FENCES_TAG)) return false;
        return !(neighbor.getBlock() instanceof FenceGateBlock) || FenceGateBlock.connectsToDirection(neighbor, side);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(arm(side), joins(context.getLevel().getBlockState(context.getClickedPos().relative(side)), side));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbor,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (!toNeighbor.getAxis().isHorizontal()) return state;
        return state.setValue(arm(toNeighbor), joins(neighborState, toNeighbor));
    }

    // A stamp in hand: pressed on the post, or taken off it (the workstation's upgrades)
    @Override
    protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level,
                                                             BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                             net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.getItem() instanceof com.lealex.alchyx.upgrade.UpgradeItem
                && level.getBlockEntity(pos) instanceof com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity plot) {
            if (!level.isClientSide()) plot.useStamp(player, hand, stack);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
