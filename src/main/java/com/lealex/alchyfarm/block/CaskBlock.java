package com.lealex.alchyfarm.block;

import com.lealex.alchyfarm.block.entity.CaskBlockEntity;
import com.lealex.alchyfarm.registry.ModRegistries;
import com.lealex.alchyfarm.upgrade.Rifted;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * A workstation's storage for what it gives as a FLUID (a mine's lava, an apiary's honey), joined to it as a basket
 * is: in its fence's line or against it, or for a mine on top of its stock or its basket. One tank
 * (block/entity/CaskBlockEntity), which pipes and pumps can draw from but not fill. A bucket (or any other fluid
 * container) used on it is filled from it, a glass bottle on a cask of honey becomes a honey bottle, and an empty
 * hand reads what it holds.
 */
public class CaskBlock extends Block implements EntityBlock {
    public CaskBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CaskBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // A glass bottle on a cask of honey: a honey bottle
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS; // (the client can't see the tank)
            ModRegistries.Brew honey = level.getBlockEntity(pos) instanceof CaskBlockEntity cask ? held(cask, ModRegistries.RIFTED_HONEY) : null;
            if (honey != null && level.getBlockEntity(pos) instanceof CaskBlockEntity cask) {
                // rifted honey: a bottle of it, rifted as its tier is (2, 4 or 6)
                cask.take(CaskBlockEntity.BOTTLE);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, Rifted.honeyBottle(honey.itemLevel())));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
            } else if (level.getBlockEntity(pos) instanceof CaskBlockEntity cask && cask.holdsHoney(CaskBlockEntity.BOTTLE)) {
                cask.take(CaskBlockEntity.BOTTLE);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.HONEY_BOTTLE)));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        // A bowl on a cask of stew: a bowl of mushroom stew (of rifted stew: rifted as its tier is)
        if (stack.is(Items.BOWL)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof CaskBlockEntity cask) {
                ModRegistries.Brew rifted = held(cask, ModRegistries.RIFTED_STEW);
                if (rifted != null || cask.holds(ModRegistries.MUSHROOM_STEW.get(), CaskBlockEntity.BOTTLE)) {
                    cask.take(CaskBlockEntity.BOTTLE);
                    ItemStack stew = new ItemStack(Items.MUSHROOM_STEW);
                    if (rifted != null) stew = Rifted.touch(null, stew, rifted.itemLevel());
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, stew));
                    level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6F, rifted != null ? 0.8F : 1.2F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CaskBlockEntity cask
                && ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null) {
            // The client can't see the tank: it just says "handled" so it doesn't pour the bucket on the ground
            if (level.isClientSide() || FluidUtil.interactWithFluidHandler(player, hand, pos, cask.tank(), null)) return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Which tier of these the cask holds, with a bottle's worth in it; null if none. */
    private static ModRegistries.@org.jspecify.annotations.Nullable Brew held(CaskBlockEntity cask, java.util.List<ModRegistries.Brew> tiers) {
        for (ModRegistries.Brew brew : tiers) if (cask.holds(brew.fluid(), CaskBlockEntity.BOTTLE)) return brew;
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CaskBlockEntity cask) {
            serverPlayer.sendSystemMessage(Component.literal(cask.says()), true);
        }
        return InteractionResult.SUCCESS;
    }
}
