package com.lealex.alchyfarm.client;

import com.lealex.alchyx.client.LinkLines;
import com.lealex.alchyx.item.LinkingTool;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import com.lealex.alchyfarm.item.SurveyorCordItem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While the player holds a surveyor's cord, what the post it holds is bound to shows as motes (as AlchyRift's tuner
 * shows a relay's links): the post sparkles, a crimson line of motes runs from it to each store the cord joined to
 * it, the edge of its ground is drawn in pale motes (whatever gave it that ground: the cord, a border, or the
 * squares round the post), and a first corner waiting for its second sparkles. Client only, nothing is sent for it.
 */
@EventBusSubscriber(modid = AlchyFarm.MODID, value = Dist.CLIENT)
public final class CordLinks {
    private CordLinks() {}

    private static final double RANGE = 48.0;
    private static final int EVERY = 4;
    private static final double SPACING = 0.5;
    private static final int MAX_MOTES = 48;
    private static final DustParticleOptions CORD = new DustParticleOptions(0xD9443C, 0.7F);
    private static final DustParticleOptions EDGE = new DustParticleOptions(0xF2DDA6, 0.6F);
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || level.getGameTime() % EVERY != 0) return;
        ItemStack cord = LinkLines.inHand(player, SurveyorCordItem.class);
        if (cord == null) return;
        BlockPos corner = LinkingTool.noted(cord, SurveyorCordItem.CORNER);
        if (corner != null) {
            Vec3 at = Vec3.atCenterOf(corner);
            level.addParticle(ParticleTypes.END_ROD, at.x, at.y + 0.7, at.z, 0, 0.03, 0);
        }
        BlockPos post = LinkingTool.held(cord);
        if (post == null) return;
        Vec3 from = Vec3.atCenterOf(post);
        if (from.distanceToSqr(player.position()) > RANGE * RANGE || !(level.getBlockEntity(post) instanceof SoulPlotBlockEntity plot)) return;

        level.addParticle(ParticleTypes.END_ROD, from.x, from.y + 0.6, from.z,
                (level.getRandom().nextDouble() - 0.5) * 0.05, 0.04, (level.getRandom().nextDouble() - 0.5) * 0.05);
        for (BlockPos store : plot.linkedStores()) {
            // a store broken (or swapped for another block) leaves nothing to draw a line to
            if (com.lealex.alchyx.link.Links.gone(level, store, state -> state.getBlock() instanceof com.lealex.alchyfarm.block.PlotStoreBlock
                    || state.getBlock() instanceof com.lealex.alchyfarm.block.CaskBlock)) continue;
            LinkLines.line(level, from.add(0, 0.3, 0), Vec3.atCenterOf(store).add(0, 0.3, 0), CORD, MAX_MOTES, EVERY);
        }

        // The ground's edge: every side of a square that has no square of the ground beyond it
        List<BlockPos> field = plot.field();
        Set<Long> squares = new HashSet<>(field.size() * 2);
        for (BlockPos square : field) squares.add(BlockPos.asLong(square.getX(), 0, square.getZ()));
        double y = post.getY() + 0.15;
        for (BlockPos square : field) {
            double x = post.getX() + square.getX(), z = post.getZ() + square.getZ();
            for (int[] side : SIDES) {
                if (squares.contains(BlockPos.asLong(square.getX() + side[0], 0, square.getZ() + side[1]))) continue;
                // (the corners of that side of the square, in the block's own 0..1)
                double ax = side[0] == 1 ? 1 : 0, az = side[1] == 1 ? 1 : 0, bx = side[0] == -1 ? 0 : 1, bz = side[1] == -1 ? 0 : 1;
                if (side[0] != 0) bx = ax;
                if (side[1] != 0) bz = az;
                LinkLines.line(level, new Vec3(x + ax, y, z + az), new Vec3(x + bx, y, z + bz), EDGE, 2, EVERY);
            }
        }
    }
}
