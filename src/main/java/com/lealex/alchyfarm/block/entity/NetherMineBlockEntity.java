package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The nether mine: the mine (MineBlockEntity: the pickaxes, the veins that show on the walls, the screen), built of
 * the Nether's stone instead (data/alchyfarm/multiblock/nether_mine.json) and bringing up the Nether's rock and ores
 * (Config mine.netherVeins: netherrack, basalt, quartz, nether gold, glowstone, ancient debris...). It works in any
 * dimension: what it digs is the config's list, not the world. Its dial is the Nether's own heights, 0 to 127,
 * wherever it is built. Its walls are blackstone where no vein shows.
 */
public class NetherMineBlockEntity extends MineBlockEntity {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "nether_mine");

    public NetherMineBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.NETHER_MINE_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Nether mine";
    }

    @Override
    protected String plotName() {
        return "nether mine";
    }

    @Override
    public String soulLook() {
        return "armorer";
    }

    @Override
    protected String arrivedWords() {
        return "The nether mine is whole: someone has come through to work its rock.";
    }

    @Override
    protected String goneWords() {
        return "The nether mine is broken: its miner is gone.";
    }

    @Override
    protected List<Config.Vein> veins() {
        return Config.netherMineVeins();
    }

    @Override
    protected List<Config.Spring> springs() {
        return Config.netherMineSprings();
    }

    @Override
    protected String screenTitle() {
        return "container.alchyfarm.nether_mine";
    }

    @Override
    protected int startDepth() {
        return 32;
    }

    @Override
    protected int lowest() {
        return 0;
    }

    @Override
    protected int highest() {
        return 127;
    }

    @Override
    protected BlockState rock() {
        return Blocks.BLACKSTONE.defaultBlockState();
    }

    // Blackstone would not show on a wall of blackstone: it shows cracked
    @Override
    protected BlockState lookOf(BlockState vein) {
        return vein.is(Blocks.BLACKSTONE) ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : vein;
    }
}
