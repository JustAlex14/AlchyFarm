package com.lealex.alchyfarm.item;

import com.lealex.alchyx.item.LinkingTool;
import com.lealex.alchyfarm.block.CaskBlock;
import com.lealex.alchyfarm.block.PlotStoreBlock;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The surveyor's cord: with it a fenced workstation's post can stand away from its ground (at a building's door) and
 * from its stores. All by sneak + right-click: on a post, the cord takes it (on the same post again: the post
 * forgets everything the cord gave it); then on a stock, basket, trough or cask: joined to that post, or let go; on
 * two blocks of ground: the corners of that post's field, a rectangle on the post's level. The post it holds and a
 * first corner are kept in the cord's custom data (AlchyX LinkingTool, which also gives it its glow, its tooltip's
 * place and its letting go). Nothing is used up. See SoulPlotBlockEntity.linkStore / linkField.
 */
public class SurveyorCordItem extends LinkingTool {
    public static final String CORNER = "corner";

    public SurveyorCordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos(), post = held(stack), corner = noted(stack, CORNER);

        if (level.getBlockEntity(pos) instanceof SoulPlotBlockEntity plot) {
            if (!plot.linkable()) {
                tell(player, "The " + plot.linkName() + " is one structure: nothing to link.");
            } else if (pos.equals(post)) {
                plot.unlink();
                letGo(stack);
                note(stack, CORNER, null);
                tell(player, "The " + plot.linkName() + " forgot its linked field and stores.");
            } else {
                hold(stack, pos);
                note(stack, CORNER, null);
                tell(player, "Holding the " + plot.linkName() + ": now its stores, or two corners of its field.");
            }
            return InteractionResult.SUCCESS;
        }

        if (post == null || !(level.getBlockEntity(post) instanceof SoulPlotBlockEntity plot)) {
            tell(player, "Sneak + right-click a post first.");
            return InteractionResult.SUCCESS;
        }
        Block clicked = level.getBlockState(pos).getBlock();
        if (clicked instanceof PlotStoreBlock || clicked instanceof CaskBlock) {
            tell(player, plot.linkStore(pos));
        } else if (corner == null) {
            String wrong = plot.cornerProblem(pos);
            if (wrong == null) note(stack, CORNER, pos);
            tell(player, wrong != null ? wrong : "First corner set: now the opposite one.");
        } else {
            tell(player, plot.linkField(corner, pos));
            note(stack, CORNER, null);
        }
        return InteractionResult.SUCCESS;
    }

    // Sneak + use in the air (LinkingTool): the cord lets go of its post, which keeps what was linked to it
    @Override
    protected void lostHold(ItemStack stack, Player player) {
        note(stack, CORNER, null);
        tell(player, "The cord let go of its post.");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        heldLines(stack, tooltip, "post");
        BlockPos corner = noted(stack, CORNER);
        if (corner != null) {
            tooltip.accept(Component.literal("First corner at " + corner.getX() + " " + corner.getY() + " " + corner.getZ()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.accept(Component.literal("Sneak + right-click:").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("a post, to hold it (again: unlink all)").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("a stock, basket, trough or cask, to join it").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("two corners of ground, for its field").withStyle(ChatFormatting.DARK_GRAY));
    }
}
