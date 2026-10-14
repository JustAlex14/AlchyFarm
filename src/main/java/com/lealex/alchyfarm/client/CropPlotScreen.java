package com.lealex.alchyfarm.client;

import com.lealex.alchyx.client.Lines;
import com.lealex.alchyx.client.WindowTheme;
import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.entity.CropPlotBlockEntity;
import com.lealex.alchyfarm.menu.CropPlotMenu;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The carved stake's screen: a map of the field (north up), one square per square of soil.
 * A square shows the seed chosen for it (with a gold corner), or else, faintly, what grows there now.
 * A square kept bare (never sown: room for a stem's fruit) shows darker, with a cross: left click with an empty hand
 * on a square without a choice keeps it bare, and again sows it again.
 * Left click with a seed in hand chooses that seed, left click with an empty hand clears the choice (the soul then
 * sows whatever the stock holds), right click chooses what grows there now.
 * Under the map: the share of harvested seeds that goes back to the stock (the cycle sign and a number to type), the
 * bone meal the stock holds, and what the plot lacks. Drawn with plain rectangles for now (no texture yet).
 */
public class CropPlotScreen extends AbstractContainerScreen<CropPlotMenu> implements StampTab.Host {
    @Override
    public net.minecraft.client.renderer.Rect2i stampArea() {
        return StampTab.area(leftPos + imageWidth, topPos + 2);
    }

    private static final Identifier KEEP_ICON = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/gui/keep_seeds.png");
    private static final int CELL = 14;
    private static final int FRAME = 0xFF1E1712, PANEL = 0xFF3A2E24, PAPER = 0xFF2B221B, SOIL = 0xFF6B4A2B, SOIL_EDGE = 0xFF4E3620,
            SLOT_EDGE = 0xFF1E1712, SLOT = 0xFF54463A, TEXT = 0xFFE8DCC6, WARN = 0xFFE0A070, HOVER = 0x50FFFFFF,
            GHOST = 0xA86B4A2B, LOCKED = 0xFFE8C55A, BARE = 0xFF4A3A2A, BARE_MARK = 0xFF8A7A66, STONE = 0xFF777777, STONE_EDGE = 0xFF4E4E4E,
            WATER = 0xFF3F76C8, WATER_EDGE = 0xFF2B5596;
    private static final int ROW_Y = CropPlotMenu.MAP_TOP + CropPlotMenu.MAP_SIZE + 4, PROBLEM_Y = ROW_Y + 18;
    private static final int KEEP_X = 8, BOX_X = 28, MEAL_X = 112;
    private static final int ESCAPE = 256;

    // The map's place on the screen and the field's corner, worked out each frame (the field can change while open)
    private int mapX, mapY, minX, minZ;
    private @Nullable EditBox keepBox;
    // Squares whose ground does not take what is to grow there, by their place in the field list (worked out twice a
    // second: each asks the game about the blocks around it)
    private boolean[] unsuited = new boolean[0], wet = new boolean[0]; // wet: a pond's water
    private int sinceChecked = 100;

    /**
     * What a square sown with this gives, as near as the client can tell without rolling loot: the item named as
     * the plant's block (wheat seeds grow the block "wheat": wheat), or that name without its last s (beetroots:
     * beetroot), else the seed itself (carrot, potato, a sapling, a golden apple). Null for null.
     */
    private static @Nullable Item harvestOf(@Nullable Item seed) {
        if (seed == null) return null;
        if (!(seed instanceof net.minecraft.world.item.BlockItem placed)) return seed;
        net.minecraft.resources.Identifier block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placed.getBlock());
        for (String path : new String[] {block.getPath(), block.getPath().endsWith("s") ? block.getPath().substring(0, block.getPath().length() - 1) : block.getPath()}) {
            Item named = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath(block.getNamespace(), path)).orElse(null);
            if (named != null && named != net.minecraft.world.item.Items.AIR && !(named instanceof net.minecraft.world.item.BlockItem)) return named;
        }
        return seed;
    }

    public CropPlotScreen(CropPlotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CropPlotMenu.WIDTH, CropPlotMenu.HEIGHT);
        inventoryLabelY = CropPlotMenu.INVENTORY_Y - 11;
    }

    private WindowTheme theme() {
        return Theme.of(menu.plot());
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 24; // after the post, drawn in the header
        CropPlotBlockEntity plot = menu.plot();
        keepBox = new EditBox(font, leftPos + BOX_X, topPos + ROW_Y, 26, 14, Component.literal("Seeds kept"));
        keepBox.setMaxLength(3);
        keepBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        keepBox.setValue(plot == null ? "50" : Integer.toString(plot.keepSeeds()));
        keepBox.setResponder(text -> {
            if (!text.isEmpty()) press(CropPlotMenu.KEEP + Math.min(100, Integer.parseInt(text)));
        });
        addRenderableWidget(keepBox);
    }

    /**
     * True if the ground of this square is no good: it does not take the seed chosen for it, or, with none chosen,
     * nothing this plot plants. Asked of every square, sown or not (a square where something already grows is fine).
     */
    private static boolean unsuited(CropPlotBlockEntity plot, BlockPos square) {
        Item chosen = plot.wanted(square);
        if (chosen != null) return !plot.suits(chosen, square);
        return growing(plot, square) == null && !plot.suitsAny(square);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        CropPlotBlockEntity plot = menu.plot();
        if (plot != null && ++sinceChecked >= 10) check(plot);
    }

    private void check(CropPlotBlockEntity plot) {
        sinceChecked = 0;
        List<BlockPos> field = plot.field();
        boolean[] checked = new boolean[field.size()], water = new boolean[field.size()];
        for (int i = 0; i < checked.length; i++) {
            water[i] = plot.isWater(field.get(i));
            checked[i] = !water[i] && unsuited(plot, field.get(i));
        }
        unsuited = checked;
        wet = water;
    }

    private void press(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private List<BlockPos> field() {
        CropPlotBlockEntity plot = menu.plot();
        return plot == null ? List.of() : plot.field();
    }

    /** Where the map is drawn: the field's bounding box, centred in the map's area. */
    private void layout() {
        List<BlockPos> field = field();
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        minX = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        for (BlockPos square : field) {
            minX = Math.min(minX, square.getX());
            maxX = Math.max(maxX, square.getX());
            minZ = Math.min(minZ, square.getZ());
            maxZ = Math.max(maxZ, square.getZ());
        }
        int wide = field.isEmpty() ? 0 : maxX - minX + 1, deep = field.isEmpty() ? 0 : maxZ - minZ + 1;
        mapX = leftPos + (imageWidth - wide * CELL) / 2;
        mapY = topPos + CropPlotMenu.MAP_TOP + (CropPlotMenu.MAP_SIZE - deep * CELL) / 2;
    }

    /** The field square under the mouse (its index in the plot's field list), or -1. */
    private int squareAt(double mouseX, double mouseY) {
        List<BlockPos> field = field();
        for (int i = 0; i < field.size(); i++) {
            int x = mapX + (field.get(i).getX() - minX) * CELL, y = mapY + (field.get(i).getZ() - minZ) * CELL;
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) return i;
        }
        return -1;
    }

    /** The seed of what grows on this square now, or null (bare soil, or something a plot does not sow). */
    private static @Nullable Item growing(CropPlotBlockEntity plot, BlockPos square) {
        if (plot.getLevel() == null) return null;
        net.minecraft.world.level.block.Block block = plot.getLevel().getBlockState(plot.getBlockPos().offset(square)).getBlock();
        Item seed = block.asItem();
        return block instanceof com.lealex.alchyfarm.block.TrellisBlock || plot.plants(new ItemStack(seed)) ? seed : null; // a trellis shows as itself
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int wide, int high) {
        return mouseX >= x && mouseX < x + wide && mouseY >= y && mouseY < y + high;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        theme().back(graphics, x, y, imageWidth, imageHeight, 16, Theme.icon(menu.plot()));
        StampTab.draw(graphics, font, theme(), menu.plot(), x + imageWidth, y + 2, mouseX, mouseY);
        int mapLeft = x + (imageWidth - CropPlotMenu.MAP_SIZE) / 2 - 2, mapTop = y + CropPlotMenu.MAP_TOP - 2;
        theme().well(graphics, mapLeft - 1, mapTop - 1, mapLeft + CropPlotMenu.MAP_SIZE + 5, mapTop + CropPlotMenu.MAP_SIZE + 5);
        graphics.fill(mapLeft, mapTop, mapLeft + CropPlotMenu.MAP_SIZE + 4, mapTop + CropPlotMenu.MAP_SIZE + 4, PAPER);
        for (Slot slot : menu.slots) {
            theme().well(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17);
        }

        layout();
        CropPlotBlockEntity plot = menu.plot();
        if (plot == null) return;
        List<BlockPos> field = field();
        if (unsuited.length != field.size()) check(plot); // just opened, or the field changed: at once
        for (int i = 0; i < field.size(); i++) {
            BlockPos square = field.get(i);
            int cx = mapX + (square.getX() - minX) * CELL, cy = mapY + (square.getZ() - minZ) * CELL;
            // Ground that takes the plant is drawn as soil, ground that does not stays grey
            boolean wrong = i < unsuited.length && unsuited[i];
            boolean water = i < wet.length && wet[i];
            graphics.fill(cx, cy, cx + CELL, cy + CELL, water ? WATER_EDGE : wrong ? STONE_EDGE : SOIL_EDGE);
            graphics.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, water ? WATER : wrong ? STONE : SOIL);
        }
        // On top of the soil (icons are 16 wide: they lap over the squares' edges a little): the chosen seed, or a
        // ghost of what grows there (the icon under a veil of the soil's colour)
        for (BlockPos square : field) {
            int cx = mapX + (square.getX() - minX) * CELL, cy = mapY + (square.getZ() - minZ) * CELL;
            Item chosen = plot.wanted(square);
            Item shown = chosen != null ? chosen : growing(plot, square);
            if (plot.bare(square)) { // kept bare: trodden earth and a cross
                graphics.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, BARE);
                for (int i = 4; i < CELL - 4; i++) {
                    graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, BARE_MARK);
                    graphics.fill(cx + CELL - 1 - i, cy + i, cx + CELL - i, cy + i + 1, BARE_MARK);
                }
            }
            if (shown != null) {
                graphics.item(new ItemStack(shown), cx - 1, cy - 1);
                if (chosen == null) graphics.fill(cx, cy, cx + CELL, cy + CELL, GHOST);
                else graphics.fill(cx + CELL - 3, cy + 1, cx + CELL - 1, cy + 3, LOCKED);
            }
        }
        int hovered = squareAt(mouseX, mouseY);
        if (hovered >= 0) {
            BlockPos square = field.get(hovered);
            int cx = mapX + (square.getX() - minX) * CELL, cy = mapY + (square.getZ() - minZ) * CELL;
            graphics.fill(cx, cy, cx + CELL, cy + CELL, HOVER);
            Item chosen = plot.wanted(square), grows = growing(plot, square);
            boolean water = hovered < wet.length && wet[hovered];
            Component first = water ? Component.literal("Water: fished from the post")
                    : chosen != null ? Component.literal("Always sown with: ").append(new ItemStack(chosen).getHoverName())
                    : plot.bare(square) ? Component.literal("Kept bare: never sown (room for what grows beside)")
                    : grows == null && !plot.sownByDefault(square) ? Component.literal("Left free for room, unless you choose something for it")
                    : grows != null ? Component.literal("Growing: ").append(new ItemStack(grows).getHoverName()).append(" (not fixed)")
                    : Component.literal("Sown with whatever the stock holds");
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(first);
            if (hovered < unsuited.length && unsuited[hovered]) {
                Component ground = plot.getLevel() == null ? Component.literal("this ground")
                        : plot.getLevel().getBlockState(plot.getBlockPos().offset(square).below()).getBlock().getName();
                lines.add((chosen != null
                        ? Component.literal("It will not grow here: ").append(ground).append(" does not take it, or it has no room")
                        : Component.literal("Nothing this post plants grows here, on ").append(ground)).withStyle(ChatFormatting.RED));
            }
            if (!water && plot.riftedChance() > 0) {
                // What the rift does to this square's harvest: marks it (food), or only gives more of it
                Item gives = harvestOf(chosen != null ? chosen : grows);
                int gain = plot.riftOnlyMore() ? (plot.riftLevel() >= 5 ? 3 : 2)
                        : gives == null ? -1 : com.lealex.alchyfarm.upgrade.Rifted.gain(new ItemStack(gives), plot.riftLevel());
                if (gain == 0) lines.add(Component.literal("Rifted: " + plot.riftedChance() + "% of the time").withStyle(ChatFormatting.LIGHT_PURPLE));
                else if (gain > 1) lines.add(Component.literal(plot.riftedChance() + "% of x" + gain + " output").withStyle(ChatFormatting.LIGHT_PURPLE));
                else lines.add(Component.literal(plot.riftedChance() + "% of the harvest: rifted (food), or x"
                            + (plot.riftLevel() >= 5 ? 3 : 2) + " output").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (!water) {
                lines.add(Component.literal("Left click with a seed: always sow it here"));
                lines.add(Component.literal("Right click: always sow what grows here now"));
                lines.add(Component.literal(chosen != null ? "Left click with an empty hand: clear"
                        : plot.bare(square) ? "Left click with an empty hand: sow it again"
                        : "Left click with an empty hand: keep it bare"));
            }
            graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
        }

        // Under the map: the seeds kept (sign + the box added in init), and the stock's bone meal
        graphics.blit(RenderPipelines.GUI_TEXTURED, KEEP_ICON, x + KEEP_X, y + ROW_Y - 1, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.item(new ItemStack(Items.BONE_MEAL), x + MEAL_X, y + ROW_Y - 1);
        if (over(mouseX, mouseY, x + KEEP_X, y + ROW_Y - 1, 62, 16)) {
            graphics.setTooltipForNextFrame(font, List.of(
                    Component.literal("Seeds kept"),
                    Component.literal("Of every hundred seeds harvested, this many"),
                    Component.literal("go back to the stock to be sown again."),
                    Component.literal("The rest goes to the basket."),
                    Component.literal("Carrots and potatoes are their own seeds.")), Optional.empty(), mouseX, mouseY);
        } else if (over(mouseX, mouseY, x + MEAL_X, y + ROW_Y - 1, 56, 16)) {
            graphics.setTooltipForNextFrame(font, List.of(
                    Component.literal("Bone meal in the stock"),
                    Component.literal("With nothing to sow or harvest, the farmhand"),
                    Component.literal("casts it over the whole field, one piece"),
                    Component.literal("for each growing crop.")), Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY + 1, theme().accent(), false); // (inside the header well, clear of its top line)
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, theme().text(), false);
        CropPlotBlockEntity plot = menu.plot();
        if (plot == null) return;
        graphics.text(font, "%", BOX_X + 30, ROW_Y + 3, theme().text(), false);
        graphics.text(font, Integer.toString(plot.boneMeal()), MEAL_X + 20, ROW_Y + 3, theme().text(), false);
        graphics.text(font, "N", imageWidth / 2 - 2, CropPlotMenu.MAP_TOP - 1, 0x80E8DCC6, false);
        if (!plot.problem().isEmpty()) graphics.text(font, Lines.clip(font, plot.problem(), imageWidth - 16), 8, PROBLEM_Y, theme().warn(), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int stamp = StampTab.click(menu.plot(), leftPos + imageWidth, topPos + 2, event.x(), event.y(), event.button());
        if (stamp >= 0) { // a stamp's place, on the tab at the right
            press(stamp);
            return true;
        }
        int square = squareAt(event.x(), event.y());
        if (square >= 0 && (event.button() == 0 || event.button() == 1)) {
            if (keepBox != null) keepBox.setFocused(false);
            press(event.button() == 1 ? CropPlotMenu.LOCK + square : square);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // While the number is being typed, keys go to it (the inventory key would close the screen, digits would swap
    // hotbar items); Escape still closes
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (keepBox != null && keepBox.isFocused() && event.key() != ESCAPE) {
            keepBox.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
