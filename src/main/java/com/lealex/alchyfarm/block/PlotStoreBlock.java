package com.lealex.alchyfarm.block;

import com.lealex.alchyfarm.block.entity.PlotStoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A workstation's storage, joined to it by touching its fence (or standing in the fence's line): the stock holds
 * what the soul uses (seeds, bone meal, feed, buckets), the basket takes what it brings back, the trough holds the
 * feed a ranch's animals eat. All are plain 27-slot containers, open to hoppers and pipes. A basket is open at the
 * top and shows what it holds (client/BasketRenderer).
 */
public class PlotStoreBlock extends Block implements EntityBlock {
    public enum Kind { STOCK, BASKET, TROUGH }

    private static final VoxelShape TROUGH_SHAPE = Block.box(0, 0, 0, 16, 10, 16);
    private static final VoxelShape BASKET_SHAPE = Block.box(1, 0, 1, 15, 13, 15);
    private final Kind kind;

    public PlotStoreBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case TROUGH -> TROUGH_SHAPE;
            case BASKET -> BASKET_SHAPE;
            case STOCK -> Shapes.block();
        };
    }

    // Its collision is its shape: it can be jumped on (it used to be as tall as a fence, to keep a pen's animals in,
    // but a ranch takes its animals in as a herd and none is left to walk out). Fences join it through the wooden
    // fences tag.

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlotStoreBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PlotStoreBlockEntity store) player.openMenu(store);
        return InteractionResult.SUCCESS;
    }

    // Comparators next to it follow the change (the contents themselves drop with the block entity)
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }
}
