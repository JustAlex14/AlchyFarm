package com.lealex.alchyfarm.client;

import com.lealex.alchyx.client.Lines;
import com.lealex.alchyx.client.WindowTheme;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import com.lealex.alchyfarm.registry.ModRegistries;
import com.lealex.alchyfarm.upgrade.FarmStamps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The stamps of a workstation, shown at the right of its screen as what they are pressed on: a LETTER, a sheet of
 * the nobleman's paper with a few ruled lines and a place for a seal for each kind (haste, bounty,
 * thrift, quality work). An empty place is a faint ring with the kind's letter; a pressed one holds the stamp on a round of its
 * wax (crimson for tiers I and II, violet for III and IV). A left click on a place presses on the stamp held on
 * the mouse, or the best one of that kind in the player's inventory; a right click takes the stamp off. The screen
 * sends click(...)'s number through its menu (SoulPlotBlockEntity.stampButton).
 *
 * The letter hangs outside the window: a screen that draws it implements Host, so JEI keeps its list off it.
 */
public final class StampTab {
    /** A screen with a stamp letter: where the letter is, for JEI (compat/jei/AlchyFarmJeiPlugin). */
    public interface Host {
        Rect2i stampArea();
    }

    private static final int WIDE = 28, SLOT = 18, GAP = 4, TOP = 12, KINDS = FarmStamps.ALL.size(), HIGH = TOP + KINDS * (SLOT + GAP) + 12;
    private static final int PAPER = 0xFFE9DFC3, PAPER_EDGE = 0xFFB9A982, FOLD = 0xFFD6C9A6, INK = 0xFF8C7A5A, FAINT_INK = 0xFFBFAF8C,
            CRIMSON_WAX = 0xFF8E1D22, CRIMSON_RIM = 0xFF5C1016, VIOLET_WAX = 0xFF4C3180, VIOLET_RIM = 0xFF1A112B, VIOLET_INK = 0xFF6B3FB8;
    private static final String[] LETTERS = {"H", "B", "T", "Q", "V"};

    private StampTab() {}

    private static int tier(SoulPlotBlockEntity plot, int kind) {
        return plot.stampTier(kind);
    }

    private static int placeY(int y, int kind) {
        return y + TOP + kind * (SLOT + GAP);
    }

    /** The letter's place on the screen, its left edge at x and its top at y. */
    public static Rect2i area(int x, int y) {
        return new Rect2i(x, y, WIDE, HIGH);
    }

    /** Draws the letter with its left edge at x (the screen's right edge) and its top at y. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, WindowTheme theme, @Nullable SoulPlotBlockEntity plot, int x, int y, int mouseX, int mouseY) {
        if (plot == null) return;
        // The sheet: paper with a darker edge, its lower right corner folded, a heading and ruled lines in ink
        graphics.fill(x, y, x + WIDE, y + HIGH, PAPER_EDGE);
        graphics.fill(x, y + 1, x + WIDE - 1, y + HIGH - 1, PAPER);
        graphics.fill(x + WIDE - 6, y + HIGH - 6, x + WIDE - 1, y + HIGH - 1, FOLD);
        graphics.fill(x + WIDE - 6, y + HIGH - 6, x + WIDE - 1, y + HIGH - 5, PAPER_EDGE);
        graphics.fill(x + WIDE - 6, y + HIGH - 6, x + WIDE - 5, y + HIGH - 1, PAPER_EDGE);
        graphics.fill(x + 4, y + 4, x + WIDE - 8, y + 5, INK);
        graphics.fill(x + 4, y + 7, x + WIDE - 5, y + 8, FAINT_INK);
        int left = x + (WIDE - 1 - SLOT) / 2;
        for (int kind = 0; kind < KINDS; kind++) {
            int top = placeY(y, kind), tier = tier(plot, kind);
            graphics.fill(left - 2, top + SLOT + 1, left + SLOT + 2, top + SLOT + 2, FAINT_INK); // the line it is pressed over
            if (tier > 0) { // a round of wax, and the stamp on it
                boolean violet = tier > 2 || FarmStamps.ALL.get(kind) == FarmStamps.QUALITY || FarmStamps.ALL.get(kind) == FarmStamps.VOID;
                int wax = violet ? VIOLET_WAX : CRIMSON_WAX, rim = violet ? VIOLET_RIM : CRIMSON_RIM;
                graphics.fill(left + 2, top, left + SLOT - 2, top + SLOT, rim);
                graphics.fill(left, top + 2, left + SLOT, top + SLOT - 2, rim);
                graphics.fill(left + 1, top + 1, left + SLOT - 1, top + SLOT - 1, rim);
                graphics.fill(left + 3, top + 1, left + SLOT - 3, top + SLOT - 1, wax);
                graphics.fill(left + 1, top + 3, left + SLOT - 1, top + SLOT - 3, wax);
                graphics.fill(left + 2, top + 2, left + SLOT - 2, top + SLOT - 2, wax);
                graphics.item(ModRegistries.stamp(kind, tier), left + 1, top + 1);
            } else { // where the seal goes: a faint ring, and the kind's letter
                graphics.fill(left + 3, top + 1, left + SLOT - 3, top + 2, FAINT_INK);
                graphics.fill(left + 3, top + SLOT - 2, left + SLOT - 3, top + SLOT - 1, FAINT_INK);
                graphics.fill(left + 1, top + 3, left + 2, top + SLOT - 3, FAINT_INK);
                graphics.fill(left + SLOT - 2, top + 3, left + SLOT - 1, top + SLOT - 3, FAINT_INK);
                graphics.text(font, LETTERS[kind], left + (SLOT - font.width(LETTERS[kind])) / 2 + 1, top + 5, INK, false);
            }
            if (mouseX >= left && mouseX < left + SLOT && mouseY >= top && mouseY < top + SLOT) {
                List<Component> lines = new ArrayList<>(4);
                String kindName = Component.translatable(FarmStamps.ALL.get(kind).translationKey()).getString();
                if (tier > 0) {
                    lines.add(ModRegistries.stamp(kind, tier).getHoverName());
                    FarmStamps.ALL.get(kind).tooltip().accept(tier, lines::add);
                    lines.add(Component.literal("Right click: take it off").withStyle(ChatFormatting.DARK_GRAY));
                    lines.add(Component.literal("Left click: press on a better one").withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    lines.add(Component.literal("No " + kindName));
                    lines.add(Component.literal("Left click: press on the best one you carry").withStyle(ChatFormatting.DARK_GRAY));
                }
                graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
        if (plot.magic()) { // the rift has it: how far, signed in violet at the foot of the letter
            String level = "+" + plot.riftLevel();
            graphics.text(font, level, x + 4, y + HIGH - 11, VIOLET_INK, false);
        }
    }

    /** The menu button for a click here (SoulPlotBlockEntity.PRESS or TAKE + the kind), or -1 if the click is not on a place. */
    public static int click(@Nullable SoulPlotBlockEntity plot, int x, int y, double mouseX, double mouseY, int button) {
        if (plot == null || (button != 0 && button != 1)) return -1;
        int left = x + (WIDE - 1 - SLOT) / 2;
        for (int kind = 0; kind < KINDS; kind++) {
            int top = placeY(y, kind);
            if (mouseX >= left && mouseX < left + SLOT && mouseY >= top && mouseY < top + SLOT) {
                return (button == 1 ? SoulPlotBlockEntity.TAKE : SoulPlotBlockEntity.PRESS) + kind;
            }
        }
        return -1;
    }
}
