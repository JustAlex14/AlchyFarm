package com.lealex.alchyfarm.client;

import com.lealex.alchyfarm.block.entity.RanchBlockEntity;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import net.minecraft.nbt.CompoundTag;
import com.lealex.alchyfarm.upgrade.Rifted;
import com.lealex.alchyx.client.ChoiceScreen;
import com.lealex.alchyx.client.WindowTheme;
import com.lealex.alchyx.machine.KindChoice;
import com.lealex.alchyx.menu.ChoiceMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * AlchyX's list screen as AlchyFarm's workstations wear it (the mushroom hollow, the pond, the mines, the flower
 * garden, the apiary): the workstation's own sheets (Theme), its post in the header, what it lacks, the stamps'
 * letter beside the window (StampTab), and under the mouse what the rift does to a kind: marks it (food, raw
 * metal, a book) or only gives more of it.
 */
public class FarmChoiceScreen extends ChoiceScreen implements StampTab.Host {
    public FarmChoiceScreen(ChoiceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private @Nullable SoulPlotBlockEntity plot() {
        return menu.choice() instanceof SoulPlotBlockEntity plot ? plot : null;
    }

    @Override
    public Rect2i stampArea() {
        return StampTab.area(leftPos + imageWidth, topPos + 2);
    }

    @Override
    protected WindowTheme theme() {
        return Theme.of(plot());
    }

    @Override
    protected ItemStack icon() {
        return Theme.icon(plot());
    }

    @Override
    protected String problem() {
        SoulPlotBlockEntity plot = plot();
        return plot == null ? "" : plot.problem();
    }

    @Override
    protected void drawSide(GuiGraphicsExtractor graphics, WindowTheme theme, int mouseX, int mouseY) {
        StampTab.draw(graphics, font, theme, plot(), leftPos + imageWidth, topPos + 2, mouseX, mouseY);
    }

    @Override
    protected int clickSide(MouseButtonEvent event) {
        return StampTab.click(plot(), leftPos + imageWidth, topPos + 2, event.x(), event.y(), event.button());
    }

    // ---- The ranch: a Loot tab (the KindChoice) and a Herd tab (a picture per kind of animal) ----

    private boolean herdTab;

    private @Nullable RanchBlockEntity ranch() {
        return menu.choice() instanceof RanchBlockEntity ranch ? ranch : null;
    }

    @Override
    protected void init() {
        super.init();
        if (ranch() == null) return;
        // (in the header, after the title)
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(herdTab ? "Loot" : "Herd"), button -> {
            herdTab = !herdTab;
            rebuildWidgets();
        }).bounds(leftPos + 58, topPos + 5, 36, 11).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(herdTab
                ? "Back to what the herd gives" : "The animals: breed or free them"))).build());
    }

    /** The Herd tab's pictures: one spawn egg per kind, in the herd's order (looks()). */
    private List<Item> eggs() {
        RanchBlockEntity ranch = ranch();
        if (ranch == null) return List.of();
        if (ranch.looks() != eggsFor) {
            eggsFor = ranch.looks();
            List<Item> made = new java.util.ArrayList<>();
            for (CompoundTag look : eggsFor) made.add(egg(look.getStringOr("id", "")));
            eggs = List.copyOf(made);
        }
        return eggs;
    }

    private @Nullable List<CompoundTag> eggsFor;
    private List<Item> eggs = List.of();

    private static Item egg(String id) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
        net.minecraft.world.entity.EntityType<?> type = key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) return net.minecraft.world.item.Items.BARRIER;
        return net.minecraft.world.item.SpawnEggItem.byId(type).map(holder -> holder.value()).orElse(net.minecraft.world.item.Items.BARRIER);
    }

    private @Nullable CompoundTag lookOf(Item egg) {
        RanchBlockEntity ranch = ranch();
        int index = eggs().indexOf(egg);
        return ranch == null || index < 0 || index >= ranch.looks().size() ? null : ranch.looks().get(index);
    }

    @Override
    protected List<Item> kinds() {
        return herdTab && ranch() != null ? eggs() : super.kinds();
    }

    // On the Herd tab: a tick where a pair can be bred (two grown ones), a cross where it cannot
    @Override
    protected boolean on(Item kind) {
        if (!herdTab || ranch() == null) return super.on(kind);
        CompoundTag look = lookOf(kind);
        return look != null && look.getIntOr("count", 1) - look.getIntOr("young", 0) >= 2;
    }

    @Override
    protected void clickKind(int index, int button) {
        if (!herdTab || ranch() == null) {
            super.clickKind(index, button);
            return;
        }
        press((button == 1 ? RanchBlockEntity.FREE_BUTTON : RanchBlockEntity.BREED_BUTTON) + index);
    }

    @Override
    protected String hint() {
        return herdTab && ranch() != null ? "Left click: breed a pair. Right click: free one." : super.hint();
    }

    @Override
    protected void describe(Item kind, boolean on, List<Component> told) {
        if (!herdTab || ranch() == null) {
            super.describe(kind, on, told);
            return;
        }
        CompoundTag look = lookOf(kind);
        if (look == null) return;
        int count = look.getIntOr("count", 1), young = look.getIntOr("young", 0);
        String name = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.tryParse(look.getStringOr("id", "")))
                .map(type -> type.getDescription().getString()).orElse("?");
        told.add(Component.literal(name + " x" + count));
        if (young > 0) told.add(Component.literal(young + " still young").withStyle(ChatFormatting.GRAY));
        told.add(Component.literal(on ? "Can breed" : "Not enough grown to breed").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        told.add(Component.literal("Left click: breed a pair (" + com.lealex.alchyfarm.Config.feedPerBreeding() + " feed)").withStyle(ChatFormatting.DARK_GRAY));
        told.add(Component.literal("Right click: set one free").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected void details(Item kind, boolean on, List<Component> told) {
        SoulPlotBlockEntity plot = plot();
        KindChoice choice = menu.choice();
        if (!on || plot == null || choice == null || plot.riftedChance() <= 0) return;
        ItemStack given = new ItemStack(choice.given(kind));
        int gain = plot.riftOnlyMore() ? (given.getMaxStackSize() > 1 ? (plot.riftLevel() >= 5 ? 3 : 2) : -1) : Rifted.gain(given, plot.riftLevel());
        if (gain == 0) told.add(Component.literal("Rifted: " + plot.riftedChance() + "% of the time").withStyle(ChatFormatting.LIGHT_PURPLE));
        else if (gain > 1) told.add(Component.literal(plot.riftedChance() + "% of x" + gain + " output").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
