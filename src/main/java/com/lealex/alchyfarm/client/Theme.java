package com.lealex.alchyfarm.client;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.block.entity.SoulPlotBlockEntity;
import com.lealex.alchyx.client.WindowTheme;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * AlchyFarm's looks for AlchyX's WindowTheme (which lays them): one for each workstation's soul (soulLook). The two
 * materials of each are sheets written by tools/gui_textures.py, textures/gui/panel_<look>.png and band_<look>.png:
 * farmer wheat planks, shepherd leather planks, fletcher forest planks, fisherman water tiles, bogged mossy stone
 * with violet veins, toolsmith stone bricks, armorer dark red bricks with embers, cleric violet stone with gold
 * veins, nitwit leaf planks with petals, butcher honey tiles.
 */
public final class Theme {
    private Theme() {}

    private static WindowTheme look(int frame, int panel, int line, int text, int faint, int warn, int accent, String look) {
        return new WindowTheme(frame, panel, line, text, faint, warn, accent,
                Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/gui/panel_" + look + ".png"),
                Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/gui/band_" + look + ".png"));
    }

    private static final WindowTheme
            FARMER = look(0xFF1E1712, 0xFF4A3A22, 0xFF33280F, 0xFFF0E4C4, 0xFFB5A27A, 0xFFE0A070, 0xFFE2C25A, "farmer"),
            SHEPHERD = look(0xFF1F130E, 0xFF4B3024, 0xFF341F16, 0xFFF0DCCB, 0xFFB89A86, 0xFFE8A070, 0xFFD98F5C, "shepherd"),
            FLETCHER = look(0xFF111A10, 0xFF2D3E29, 0xFF1F2C1C, 0xFFDDE8CC, 0xFF96A888, 0xFFE0A070, 0xFF9CCB6B, "fletcher"),
            FISHERMAN = look(0xFF0E1620, 0xFF22384A, 0xFF172838, 0xFFD6E6F0, 0xFF8AA4B8, 0xFFE8A878, 0xFF7FC4E8, "fisherman"),
            BOGGED = look(0xFF12160F, 0xFF2C382A, 0xFF1E271D, 0xFFDCE4D2, 0xFF93A08C, 0xFFE0A070, 0xFFB894DC, "bogged"),
            TOOLSMITH = look(0xFF151517, 0xFF3A3B40, 0xFF27282C, 0xFFE4E4E8, 0xFF9C9DA6, 0xFFE8A070, 0xFFE9A94A, "toolsmith"),
            ARMORER = look(0xFF140B0C, 0xFF3A2023, 0xFF271517, 0xFFF0DAD6, 0xFFB08E8A, 0xFFFFB070, 0xFFFF8A3C, "armorer"),
            CLERIC = look(0xFF160F1C, 0xFF382A46, 0xFF261C30, 0xFFEADCF2, 0xFFA391B0, 0xFFE8A070, 0xFFF5CC27, "cleric"),
            NITWIT = look(0xFF121A11, 0xFF33472E, 0xFF233220, 0xFFE4EED8, 0xFF9AAD8E, 0xFFE8A070, 0xFFF0A0C4, "nitwit"),
            BUTCHER = look(0xFF1C1408, 0xFF4C3915, 0xFF35270C, 0xFFF6E8C0, 0xFFBCA672, 0xFFE89060, 0xFFF4B63C, "butcher");

    public static WindowTheme of(@Nullable SoulPlotBlockEntity plot) {
        if (plot == null) return FARMER;
        return switch (plot.soulLook()) {
            case "shepherd" -> SHEPHERD;
            case "fletcher" -> FLETCHER;
            case "fisherman" -> FISHERMAN;
            case "bogged" -> BOGGED;
            case "toolsmith" -> TOOLSMITH;
            case "armorer" -> ARMORER;
            case "cleric" -> CLERIC;
            case "nitwit" -> NITWIT;
            case "butcher" -> BUTCHER;
            default -> FARMER;
        };
    }

    /** The workstation's own post, for a window's header. */
    public static ItemStack icon(@Nullable SoulPlotBlockEntity plot) {
        return plot == null ? ItemStack.EMPTY : new ItemStack(plot.getBlockState().getBlock());
    }
}
