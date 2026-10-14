package com.lealex.alchyfarm.block;

import com.lealex.alchyfarm.block.entity.MushroomFarmBlockEntity;
import com.lealex.alchyx.block.FacingCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mushroom hollow's core: an old forager's post from the hut. Unlike the other posts it is not part of a fence:
 * the hollow is a fixed structure (data/alchyfarm/multiblock/mushroom_farm.json), built behind the post, which faces
 * the player who places it.
 */
public class ForagerPostBlock extends FacingCoreBlock {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public ForagerPostBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MushroomFarmBlockEntity(pos, state);
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
