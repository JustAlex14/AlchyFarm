package com.lealex.alchyfarm.menu;

import com.lealex.alchyfarm.block.entity.CropPlotBlockEntity;
import com.lealex.alchyfarm.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The carved stake's menu. It holds no items of its own: only the player's inventory, to pick a seed up with, and
 * the buttons the screen sends through clickMenuButton: a square of the field map (its index in the plot's field
 * list: the seed in hand is chosen for it, an empty hand clears it), KEEP + a percent (the share of seeds kept), and
 * LOCK + a square's index (what grows there becomes its seed).
 * The screen reads everything else from the plot's block entity, which the server keeps in step.
 */
public class CropPlotMenu extends AbstractContainerMenu {
    public static final int KEEP = 1000, LOCK = 2000;
    /** Layout shared with the screen: the window, the field map's square area, the player's inventory. */
    public static final int WIDTH = 176, HEIGHT = 268, MAP_TOP = 18, MAP_SIZE = 126, INVENTORY_Y = 186;

    private final @Nullable CropPlotBlockEntity plot; // on the client: the client's copy (null if it isn't there)
    private final boolean server;

    /** Client side. */
    public CropPlotMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof CropPlotBlockEntity found ? found : null, false);
    }

    /** Server side. */
    public CropPlotMenu(int containerId, Inventory playerInventory, CropPlotBlockEntity plot) {
        this(containerId, playerInventory, plot, true);
    }

    private CropPlotMenu(int containerId, Inventory playerInventory, @Nullable CropPlotBlockEntity plot, boolean server) {
        super(ModRegistries.CROP_PLOT_MENU.get(), containerId);
        this.plot = plot;
        this.server = server;
        addStandardInventorySlots(playerInventory, 8, INVENTORY_Y);
    }

    public @Nullable CropPlotBlockEntity plot() {
        return plot;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!server || plot == null) return false;
        if (buttonId >= CropPlotBlockEntity.PRESS) plot.stampButton(player, buttonId, getCarried()); // a stamp's place
        else if (buttonId >= LOCK) plot.chooseGrowing(buttonId - LOCK);
        else if (buttonId >= KEEP) plot.setKeep(buttonId - KEEP);
        else plot.choose(buttonId, getCarried());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return !server || (plot != null && !plot.isRemoved() && plot.withinReach(player));
    }

    // No slots of its own: shift-click has nowhere to send anything
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
