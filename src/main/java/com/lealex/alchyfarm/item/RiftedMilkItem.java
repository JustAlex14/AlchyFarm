package com.lealex.alchyfarm.item;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * A bucket of rifted milk (a ranch's milk by the rift, from a cask of it). Drunk like milk, but it takes away only
 * the HARMFUL effects: what helps stays. Deep-Rifted milk also heals a little (Regeneration I, 10 s), Void-Rifted
 * milk more (Regeneration II, 10 s) and shields (Absorption I, 2 min). Not a BucketItem (it is drunk, not poured):
 * the cask fills it, nothing pours it back.
 */
public class RiftedMilkItem extends Item {
    private final int tier;

    public RiftedMilkItem(int tier, Properties properties) {
        super(properties.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK));
        this.tier = tier;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            List<Holder<MobEffect>> harmful = entity.getActiveEffects().stream().map(MobEffectInstance::getEffect)
                    .filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL).toList();
            for (Holder<MobEffect> effect : harmful) entity.removeEffect(effect);
            if (tier >= 2) entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, tier >= 3 ? 1 : 0));
            if (tier >= 3) entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        }
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (entity instanceof Player player && player.hasInfiniteMaterials()) return left;
        return left.isEmpty() ? new ItemStack(Items.BUCKET) : left;
    }
}
