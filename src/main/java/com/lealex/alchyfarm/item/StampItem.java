package com.lealex.alchyfarm.item;

import com.lealex.alchyx.upgrade.UpgradeItem;
import com.lealex.alchyx.upgrade.UpgradeType;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * One tier of a stamp (upgrade/FarmStamps): an AlchyX upgrade item whose tooltip says how it is used here. A post
 * has no upgrade tab: the stamp is pressed on it by sneaking and right-clicking it, or on
 * the tab at the right of its screen (SoulPlotBlockEntity.useStamp, stampButton; client/StampTab).
 */
public class StampItem extends UpgradeItem {
    public StampItem(Properties properties, UpgradeType type, int tier) {
        super(properties, type, tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        type().tooltip().accept(tier(), tooltip);
        if (tier() > 2) tooltip.accept(Component.literal("Violet: the soul works by magic").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("Sneak + right-click a post to press it on,").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("or use the stamp tab of its screen").withStyle(ChatFormatting.DARK_GRAY));
    }
}
