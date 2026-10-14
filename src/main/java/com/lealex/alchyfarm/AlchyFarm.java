package com.lealex.alchyfarm;

import com.lealex.alchyfarm.block.entity.CropPlotBlockEntity;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import com.lealex.alchyx.upgrade.UpgradeItem;
import com.lealex.alchyfarm.registry.ModRegistries;
import com.lealex.alchyx.multiblock.CoreTracker;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.slf4j.Logger;

/** AlchyFarm: an estate of farms worked by lost souls, built on AlchyX. */
@Mod(AlchyFarm.MODID)
public class AlchyFarm {
    public static final String MODID = "alchyfarm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlchyFarm(IEventBus modEventBus, ModContainer modContainer) {
        net.neoforged.neoforge.common.NeoForgeMod.enableMilkFluid(); // (a ranch pours milk into a cask)
        ModRegistries.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(AlchyFarm::keepFieldsUntrampled);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyfarm.upgrade.BlightBrewing::register);
        // Rifted things are AlchyX's (rift/RiftMark): what they cook into, and how they look in recipes, are ours
        com.lealex.alchyx.rift.RiftMark.onCooked(com.lealex.alchyfarm.upgrade.Rifted::cooked);
        com.lealex.alchyx.rift.RiftedIngredient.samples = com.lealex.alchyfarm.upgrade.Rifted::samples;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, AlchyFarm::pressStamp);
    }

    // Farmland inside a crop plot's fence is not trampled, whoever jumps on it. Trampling is rare, and the plots near
    // the block are found through AlchyX's tracker (a few hash lookups), so this costs nothing the rest of the time.
    private static void keepFieldsUntrampled(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        for (BlockPos core : CoreTracker.matricesNear(level, event.getPos())) {
            if (level.getBlockEntity(core) instanceof CropPlotBlockEntity plot && plot.isFieldSoil(event.getPos())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // SNEAK + right-click on a post with a stamp presses it on. Sneaking is the player's own sneak key, whatever
    // they bound it to (isShiftKeyDown is only the method's name). A plain right-click opens the post's screen, where
    // stamps can be pressed on and taken off too. The game itself would skip the block when one sneaks with an item
    // in hand, so this is done here.
    private static void pressStamp(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) return;
        if (!(event.getItemStack().getItem() instanceof UpgradeItem)) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof SoulPlotBlockEntity plot)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!event.getLevel().isClientSide()) plot.useStamp(event.getEntity(), event.getHand(), event.getItemStack());
    }

    // Hoppers and pipes on a stock or a basket
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRegistries.PLOT_STORE_BE.get(), (store, side) -> VanillaContainerWrapper.of(store));
        // Pipes and pumps on a cask (they can draw from it, not fill it)
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModRegistries.CASK_BE.get(), (cask, side) -> cask.tank());
    }
}
