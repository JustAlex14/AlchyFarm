package com.lealex.alchyfarm.block;

import com.lealex.alchyfarm.block.entity.MineBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A mine's pump, stood in the middle of its floor (MineBlockEntity.pump gives the lava to the casks: this block does
 * nothing by itself). PUMPING is its look: the rod works and the sight glass shows lava only while the mine is
 * really pouring something into a cask. The mine turns it on and off; while on, the pump also asks now and then
 * whether a mine still drives it, so one left behind by a broken mine comes to rest.
 */
public class PumpBlock extends Block {
    public static final BooleanProperty PUMPING = BooleanProperty.create("pumping");
    /** Ticks between two checks that a mine still drives it. */
    public static final int CHECK = 100;

    public PumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PUMPING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PUMPING);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PUMPING)) return;
        for (BlockPos core : CoreTracker.matricesNear(level, pos)) {
            if (level.getBlockEntity(core) instanceof MineBlockEntity mine && mine.drives(pos)) {
                level.scheduleTick(pos, this, CHECK);
                return;
            }
        }
        level.setBlock(pos, state.setValue(PUMPING, false), Block.UPDATE_CLIENTS);
    }
}
