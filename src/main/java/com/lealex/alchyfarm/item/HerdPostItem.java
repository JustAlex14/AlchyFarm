package com.lealex.alchyfarm.item;

import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * The hitching post as an item. A post that was the core of a ranch carries the ranch's herd: placed again, the
 * ranch built from it has its animals back. It says how many in its tooltip, and glows.
 */
public class HerdPostItem extends BlockItem {
    public HerdPostItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistries.HERD.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        List<CompoundTag> herd = stack.get(ModRegistries.HERD.get());
        if (herd != null && !herd.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.alchyfarm.hitching_post.herd", herd.size()).withStyle(ChatFormatting.AQUA));
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
