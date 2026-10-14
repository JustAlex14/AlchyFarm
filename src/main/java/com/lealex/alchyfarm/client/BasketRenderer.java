package com.lealex.alchyfarm.client;

import com.lealex.alchyfarm.block.PlotStoreBlock;
import com.lealex.alchyfarm.block.entity.PlotStoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a basket holds, seen in it: the basket's model is open at the top, and this lays a few of the things it holds
 * flat inside, more of them and higher as it fills. The server only tells clients up to four kinds and how full the
 * basket is (PlotStoreBlockEntity.shown / fill), not its whole contents. Stocks and troughs show nothing.
 */
public class BasketRenderer implements BlockEntityRenderer<PlotStoreBlockEntity, BasketRenderer.State> {
    /** What a frame draws: the kinds to show, ready to draw, and how full the basket is (0 to 1). */
    public static class State extends BlockEntityRenderState {
        public List<ItemStackRenderState> items = List.of();
        public float fill;
    }

    // Where the things lie in the basket, from its middle (x, z in blocks, and a turn in degrees): the first ones
    // near the middle, the next around them
    private static final float[][] SPOTS = {
            {0.0F, 0.0F, 20.0F}, {-0.17F, -0.14F, 110.0F}, {0.16F, -0.12F, 250.0F}, {-0.13F, 0.17F, 320.0F},
            {0.15F, 0.16F, 65.0F}, {0.0F, -0.22F, 180.0F}, {-0.22F, 0.02F, 290.0F}, {0.22F, 0.03F, 140.0F}};
    private static final float FLOOR = 2.0F / 16.0F, RISE = 8.0F / 16.0F, SIZE = 0.42F;

    private final ItemModelResolver resolver;

    public BasketRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlotStoreBlockEntity store, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(store, state, partialTicks, cameraPosition, breakProgress);
        state.fill = store.fill();
        state.items = List.of();
        if (!(store.getBlockState().getBlock() instanceof PlotStoreBlock block) || block.kind() != PlotStoreBlock.Kind.BASKET) return;
        List<ItemStack> shown = store.shown();
        if (shown.isEmpty()) return;
        List<ItemStackRenderState> items = new ArrayList<>(shown.size());
        int seed = (int) store.getBlockPos().asLong();
        for (int i = 0; i < shown.size(); i++) {
            ItemStackRenderState item = new ItemStackRenderState();
            resolver.updateForTopItem(item, shown.get(i), ItemDisplayContext.FIXED, store.getLevel(), null, seed + i);
            items.add(item);
        }
        state.items = items;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.items.isEmpty()) return;
        // One thing when it holds a little, the eight spots taken when it is full, each a hair over the one before
        int pieces = Math.clamp(1 + Math.round(state.fill * (SPOTS.length - 1)), state.items.size(), SPOTS.length);
        float level = FLOOR + RISE * Math.clamp(state.fill, 0.0F, 1.0F);
        for (int i = 0; i < pieces; i++) {
            ItemStackRenderState item = state.items.get(i % state.items.size());
            if (item.isEmpty()) continue;
            poseStack.pushPose();
            poseStack.translate(0.5F + SPOTS[i][0], level + 0.012F * i, 0.5F + SPOTS[i][1]);
            poseStack.mulPose(Axis.YP.rotationDegrees(SPOTS[i][2]));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(SIZE, SIZE, SIZE);
            item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
