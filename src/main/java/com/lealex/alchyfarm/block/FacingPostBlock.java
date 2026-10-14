package com.lealex.alchyfarm.block;

import com.lealex.alchyx.block.FacingCoreBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The core of a workstation that is a fixed structure (a machine, as AlchyMastery's are): a post that faces the
 * player who places it, with the structure behind it. Not part of a fence (those are CarvedStakeBlock).
 */
public class FacingPostBlock extends FacingCoreBlock {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    private final BiFunction<BlockPos, BlockState, BlockEntity> workstation;

    /** @param workstation makes the block entity of the workstation this post is the core of */
    public FacingPostBlock(BiFunction<BlockPos, BlockState, BlockEntity> workstation, Properties properties) {
        super(properties);
        this.workstation = workstation;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return workstation.apply(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
