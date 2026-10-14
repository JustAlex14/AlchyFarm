package com.lealex.alchyfarm;

import com.lealex.alchyfarm.client.BasketRenderer;
import com.lealex.alchyfarm.client.FarmChoiceScreen;
import com.lealex.alchyfarm.client.CropPlotScreen;
import com.lealex.alchyfarm.client.Farmhand;
import com.lealex.alchyfarm.client.Herd;
import com.lealex.alchyfarm.client.Swarm;
import com.lealex.alchyfarm.registry.ModRegistries;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

/** Client-only registrations (never loaded on a dedicated server). */
@Mod(value = AlchyFarm.MODID, dist = Dist.CLIENT)
public class AlchyFarmClient {
    public AlchyFarmClient(IEventBus modEventBus, ModContainer container) {
        // The Estate Ledger's paper (tools/book_textures.py): a tidy ruled account book under the family's crimson
        // seal, then chapter by chapter, from the letter on, his own violet seal and amethyst growing in from the edges
        java.util.List<net.minecraft.resources.Identifier> paper = new java.util.ArrayList<>();
        // (six chapters: the crimson three, the letter with the seal cracked, the violet one, and the most corrupted for the rift)
        for (int sheet : new int[] {0, 1, 2, 3, 4, 6}) {
            paper.add(net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/gui/ledger" + (sheet == 0 ? "" : "_" + sheet) + ".png"));
        }
        com.lealex.alchyx.client.BookPapers.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "guide"),
                java.util.List.of("posting", "furrows", "estate", "letter", "violet", "rift"), paper, null, null,
                // Not every page is marked: a blot on one, a tally or a pressed leaf on another, and most left clean
                new com.lealex.alchyx.client.BookPapers.Marks(java.util.stream.IntStream.range(0, 6).mapToObj(mark ->
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/gui/ledger_marks_" + mark + ".png")).toList(), 0.6F));
        // The figures animation files can name ("figure": "alchyfarm:...")
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            Farmhand.register();
            Herd.register();
            Swarm.register();
        }));
        // A basket shows what it holds
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(ModRegistries.PLOT_STORE_BE.get(), BasketRenderer::new));
        // How honey looks in the world: the game's grey water textures, tinted amber
        modEventBus.addListener((RegisterFluidModelsEvent event) -> event.register(new FluidModel.Unbaked(
                        new Material(Identifier.withDefaultNamespace("block/water_still")),
                        new Material(Identifier.withDefaultNamespace("block/water_flow")),
                        new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                        FluidTintSources.constant(ModRegistries.HONEY_COLOR)),
                ModRegistries.HONEY, ModRegistries.HONEY_FLOWING));
        // the ranch's stew, then every rifted fluid: the water's pictures in their colours
        modEventBus.addListener((RegisterFluidModelsEvent event) -> event.register(new FluidModel.Unbaked(
                        new Material(Identifier.withDefaultNamespace("block/water_still")),
                        new Material(Identifier.withDefaultNamespace("block/water_flow")),
                        new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                        FluidTintSources.constant(ModRegistries.MUSHROOM_STEW_COLOR)),
                ModRegistries.MUSHROOM_STEW, ModRegistries.MUSHROOM_STEW_FLOWING));
        modEventBus.addListener((RegisterFluidModelsEvent event) -> {
            for (ModRegistries.Brew brew : ModRegistries.brews()) {
                event.register(new FluidModel.Unbaked(
                                new Material(Identifier.withDefaultNamespace("block/water_still")),
                                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                                FluidTintSources.constant(brew.color())),
                        brew.source(), brew.flowing());
            }
        });
        // Which screen shows each menu
        modEventBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(ModRegistries.CROP_PLOT_MENU.get(), CropPlotScreen::new);
            event.register(ModRegistries.CHOICE_MENU.get(), FarmChoiceScreen::new);
        });
    }
}
