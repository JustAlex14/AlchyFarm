package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.block.PlotStoreBlock;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The 27 slots of a stock, a basket or a trough, opened with vanilla's three-row chest screen. */
public class PlotStoreBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    // What clients are told of a basket, to draw it: up to SHOWN kinds of what it holds, and how full it is. They
    // never get the contents themselves.
    private static final int SHOWN = 4;
    private List<ItemStack> shown = List.of();
    private float fill = 0.0F;
    private boolean savingForClients;

    public PlotStoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.PLOT_STORE_BE.get(), pos, state);
    }

    /** The kinds a basket shows (both sides). */
    public List<ItemStack> shown() {
        return shown;
    }

    /** How full it is, 0 to 1: the share of its slots that hold something (both sides). */
    public float fill() {
        return fill;
    }

    private static long stockChanges = 0;

    /** A number that moves whenever any stock or trough changes: what was read from the stocks holds while it stands. */
    public static long stockChanges() {
        return stockChanges;
    }

    private boolean isBasket() {
        return getBlockState().getBlock() instanceof PlotStoreBlock store && store.kind() == PlotStoreBlock.Kind.BASKET;
    }

    /** Works out what a basket shows. True if that changed. */
    private boolean summarize() {
        List<ItemStack> kinds = new ArrayList<>(SHOWN);
        int used = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            used++;
            boolean known = false;
            for (ItemStack kind : kinds) known |= kind.is(stack.getItem());
            if (!known && kinds.size() < SHOWN) kinds.add(new ItemStack(stack.getItem()));
        }
        float full = used / (float) items.size();
        boolean same = kinds.size() == shown.size() && Math.abs(full - fill) < 0.005F;
        for (int i = 0; same && i < kinds.size(); i++) same = kinds.get(i).is(shown.get(i).getItem());
        if (same) return false;
        shown = kinds;
        fill = full;
        return true;
    }

    // A basket's contents changed: clients are told only if what it shows did
    @Override
    public void setChanged() {
        super.setChanged();
        if (!isBasket()) stockChanges++; // (a stock or a trough: what was read from the stocks may be stale)
        if (level == null || level.isClientSide() || !isBasket()) return;
        if (summarize()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        savingForClients = true;
        try {
            return saveCustomOnly(registries);
        } finally {
            savingForClients = false;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (savingForClients) {
            output.store("shown", ItemStack.CODEC.listOf(), shown);
            output.putInt("fill", Math.round(fill * 1000.0F));
            return;
        }
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        summarize(); // from the contents (the server's side), or from what a client was told
        input.read("shown", ItemStack.CODEC.listOf()).ifPresent(told -> shown = told);
        fill = input.getIntOr("fill", Math.round(fill * 1000.0F)) / 1000.0F;
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        PlotStoreBlock.Kind kind = getBlockState().getBlock() instanceof PlotStoreBlock store ? store.kind() : PlotStoreBlock.Kind.STOCK;
        return Component.translatable(switch (kind) {
            case STOCK -> "container.alchyfarm.stock";
            case BASKET -> "container.alchyfarm.basket";
            case TROUGH -> "container.alchyfarm.trough";
        });
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }
}
