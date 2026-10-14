package com.lealex.alchyfarm.upgrade;

import com.lealex.alchyfarm.AlchyFarm;
import com.lealex.alchyfarm.Config;
import com.lealex.alchyx.upgrade.UpgradeType;
import com.lealex.alchyx.upgrade.UpgradeTypes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The workstations' upgrades: the nobleman's STAMPS, his orders pressed on a post (AlchyX's upgrade types: a post
 * holds one stamp of each kind). Tiers I and II are stamped in his family's crimson wax, III and IV in his own violet
 * (the recipes say so), and every tier counts toward the workstation's corruption, which AlchyX shows as rifts.
 *
 * HASTE: the soul works and walks quicker, and what comes by itself (veins, mushrooms, honey, blooms) comes sooner.
 * BOUNTY: more of what it brings back. THRIFT: less of what it uses is used up. How much a tier does: Config stamps.
 * What they do is in SoulPlotBlockEntity (hasted, toBasket, spared) and in each workstation where it uses something.
 */
public final class FarmStamps {
    private FarmStamps() {}

    private static final Identifier EMBLEMS = Identifier.fromNamespaceAndPath(AlchyFarm.MODID, "textures/gui/stamp_emblems.png");

    public static final UpgradeType HASTE = UpgradeTypes.register(new UpgradeType(id("haste"), 4, 0xF2C14E, new UpgradeType.Emblem(EMBLEMS, 0, 0),
            (tier, lines) -> lines.accept(Component.literal("Works " + Config.hasteOf(tier) + "% quicker").withStyle(ChatFormatting.GRAY))));
    public static final UpgradeType BOUNTY = UpgradeTypes.register(new UpgradeType(id("bounty"), 4, 0x8BD35A, new UpgradeType.Emblem(EMBLEMS, 16, 0),
            (tier, lines) -> lines.accept(Component.literal("Brings back " + Config.bountyOf(tier) + "% more").withStyle(ChatFormatting.GRAY))));
    public static final UpgradeType THRIFT = UpgradeTypes.register(new UpgradeType(id("thrift"), 4, 0x7FB8E8, new UpgradeType.Emblem(EMBLEMS, 32, 0),
            (tier, lines) -> lines.accept(Component.literal("Uses up " + Config.thriftOf(tier) + "% less").withStyle(ChatFormatting.GRAY))));

    /**
     * QUALITY WORK (one tier): the workstation never runs out of what quickens it. It works as if its stock always
     * held bone meal and torches (casts over the crops and saplings, the hollow's bed, the garden's blooms, the
     * mine's light), the pond's best bait and the ranch's feed (a drop needs none in the trough), and uses none. It does not count toward the rift.
     */
    public static final UpgradeType QUALITY = UpgradeTypes.register(new UpgradeType(id("quality"), 1, 0xF4F0E0, new UpgradeType.Emblem(EMBLEMS, 48, 0),
            (tier, lines) -> lines.accept(Component.literal("Never short of bone meal, torches, bait or feed").withStyle(ChatFormatting.GRAY))));

    /**
     * VOID (two tiers): what is not wanted never reaches the basket. Tier I: the seeds and saplings a harvest gives
     * beyond the share kept for sowing are destroyed (no more stacks of wheat seeds). Tier II: on a workstation
     * working by magic, ONLY what comes back rifted is kept, everything else is destroyed. It does not count toward
     * the rift.
     */
    public static final UpgradeType VOID = UpgradeTypes.register(new UpgradeType(id("void"), 2, 0x8A55C8, new UpgradeType.Emblem(EMBLEMS, 64, 0),
            (tier, lines) -> lines.accept(Component.literal(tier >= 2 ? "Keeps only what comes back rifted" : "Destroys leftover seeds and saplings")
                    .withStyle(ChatFormatting.GRAY))));

    /** What every workstation's post takes, in slot order. */
    public static final List<UpgradeType> ALL = List.of(HASTE, BOUNTY, THRIFT, QUALITY, VOID);

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AlchyFarm.MODID, path);
    }
}
