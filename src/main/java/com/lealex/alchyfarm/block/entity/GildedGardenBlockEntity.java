package com.lealex.alchyfarm.block.entity;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyfarm.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The gilded garden (the "enchanted farm" for golden carrots and golden apples): a gilder's post whose fence closes
 * around a field. It is a crop plot for crops of its own, and shares the crop plot's field, map, choices, watering
 * and bone meal cast by extending it: what differs is here.
 *
 * Its crops are placed by no item: only this soul sows them, each from the golden thing it gives (Config
 * gildedGarden.seeds: a golden carrot for gilded carrots, a golden apple for the gilded apple tree, both
 * block/GildedCropBlock, and a glistering melon slice for the gilded melon stem, which bears glistering melons
 * beside it as a melon stem does). The config can ask more of the stock for each sowing (gold): a square is then
 * only sown when the stock holds the whole price. Grown, they give what Config gildedGarden.harvest says (or their
 * loot table); the share of "seeds kept" goes back to the stock, the rest to the basket.
 */
public class GildedGardenBlockEntity extends CropPlotBlockEntity {
    public static final Identifier PATTERN = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "gilded_garden");

    public GildedGardenBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.GILDED_GARDEN_BE.get(), pos, state);
    }

    @Override
    public Identifier patternId() {
        return PATTERN;
    }

    @Override
    protected String machineName() {
        return "Gilded garden";
    }

    @Override
    protected String plotName() {
        return "gilded garden";
    }

    // What it plants is what it is for (sugar cane; the golden things): none of it is a leftover
    @Override
    protected boolean surplus(ItemStack stack) {
        return false;
    }

    @Override
    public String soulLook() {
        return "cleric";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alchyfarm.gilded_garden");
    }

    // ---- What it sows: the config's seeds, each as its gilded crop ----

    @Override
    public boolean plants(ItemStack stack) {
        return Config.gilded(stack.getItem()) != null;
    }

    @Override
    protected @Nullable BlockState plantOf(Item seed) {
        Config.Gilded crop = Config.gilded(seed);
        return crop == null ? null : crop.crop().defaultBlockState();
    }

    @Override
    public boolean suitsAny(BlockPos square) {
        for (Config.Gilded crop : Config.gildedCrops()) {
            if (suits(crop.seed(), square)) return true;
        }
        return false;
    }

    // ---- Its price: the gold, from the stock, each time it sows ----

    private int count(ServerLevel server, Item item) {
        int count = 0;
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize(); slot++) {
                if (stock.getItem(slot).is(item)) count += stock.getItem(slot).getCount();
            }
        }
        return count;
    }

    private void take(ServerLevel server, Item item, int count) {
        for (BlockPos stockPos : stocks) {
            if (!(server.getBlockEntity(stockPos) instanceof Container stock)) continue;
            for (int slot = 0; slot < stock.getContainerSize() && count > 0; slot++) {
                if (stock.getItem(slot).is(item)) count -= stock.removeItem(slot, count).getCount();
            }
        }
    }

    @Override
    protected boolean affords(ServerLevel server, Item seed) {
        Config.Gilded crop = Config.gilded(seed);
        if (crop == null) return false;
        for (Config.Drop part : crop.cost()) {
            if (count(server, part.item()) < part.least() + (part.item() == seed ? 1 : 0)) return false;
        }
        return true;
    }

    @Override
    protected boolean pay(ServerLevel server, Item seed) {
        if (!affords(server, seed)) return false;
        Config.Gilded crop = Config.gilded(seed);
        for (Config.Drop part : crop.cost()) take(server, part.item(), unspared(server, part.least()));
        return true;
    }

    // What a grown one gives is read from gildedGarden.harvest
    @Override
    protected @Nullable List<Config.Drop> configured(Identifier block) {
        return Config.gildedHarvest(block);
    }
}
