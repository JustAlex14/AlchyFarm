package com.lealex.alchyfarm.upgrade;

import com.lealex.alchyx.rift.RiftMark;
import com.lealex.alchyfarm.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.Tags;

/**
 * What the rift does to a harvest. (The mark itself, the recipe ingredient and the furnace hook are AlchyX's:
 * rift/RiftMark, RiftedIngredient, mixin/FurnaceRiftMixin. What a rift makes of each thing is here.)
 * A workstation working by magic (a violet stamp on its post) brings some of what
 * it gets back RIFTED: enhanced. No new item and no new texture: the same item, changed through its data
 * components, so it works for every mod's food.
 *
 * HOW MUCH depends on the rift's hold on the workstation, its rift LEVEL, 1 (one tier III stamp) to 6 (three tier
 * IV): a little at first, overpowering at the top. The config's lists (stamps.riftedFoodEffects, pond.riftPotions)
 * say what a thing gives AT THE TOP; a lower level gives a share of each effect's level and length (scaled()).
 *
 * FOOD (anything with a food component): feeds 2, 3 or 4 times as much, can be eaten when full, and gives its
 * effects. It glints, is named Rifted / Deep-Rifted / Void-Rifted by its level, and its tooltip says what it does.
 * AN ENCHANTED BOOK: 1 to 4 more rolls of enchantments laid over its own. ORE (raw metal, ore blocks: the c: tags):
 * it stays one piece, marked like the food, and SMELTS into 2 to 4 times as much (AlchyX mixin/FurnaceRiftMixin). A GEM: 2
 * to 4 for one. ANYTHING ELSE that stacks: 2 or 3 for one. What does not stack is left.
 */
public final class Rifted {
    private Rifted() {}

    public static final int TOP = RiftMark.TOP;

    /** 2 at levels 1 to 3, 3 at 4 and 5, 4 at the top. */
    private static int times(int level) {
        return RiftMark.times(level);
    }

    /**
     * What a rift of this level makes of this thing, for the screens: 0 = it comes back RIFTED (food, raw metal and
     * ore blocks, an enchanted book), a number above 1 = so many times as much of it, -1 = nothing (what does not
     * stack and is none of those).
     */
    public static int gain(ItemStack stack, int level) {
        if (stack.is(Items.ENCHANTED_BOOK) || stack.get(DataComponents.FOOD) != null) return 0;
        if (stack.getMaxStackSize() <= 1) return -1;
        if (stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.ORES)) return 0;
        return stack.is(Tags.Items.GEMS) ? times(level) : level >= 5 ? 3 : 2;
    }

    private static final String BLIGHT = "alchyfarm_blight";

    /**
     * The blight brew (upgrade/BlightBrewing, hidden brewing recipes): what a rifted poisonous potato makes of a
     * bottle of water on a brewing stand. Wither, poison, slowness and blindness, worse with the potato's rift
     * level (1 to 6), kept in the bottle's custom data so it can be told again. As a potion, a splash potion or a
     * lingering one (`form`).
     */
    public static ItemStack blight(net.minecraft.world.item.Item form, int level) {
        int rift = Math.clamp(level, 1, TOP), ticks = (8 + rift * 4) * 20;
        List<MobEffectInstance> effects = List.of(
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, ticks, (rift - 1) / 2),
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, ticks, (rift + 1) / 2),
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ticks * 2, rift / 2),
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, ticks, 0));
        ItemStack bottle = new ItemStack(form);
        bottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0x3B4A1E), effects, Optional.empty()));
        String grade = rift >= TOP ? "Void-Blight Brew" : rift >= 3 ? "Deep-Blight Brew" : "Blight Brew";
        bottle.set(DataComponents.CUSTOM_NAME, Component.literal(grade).withStyle(style -> style.withColor(ChatFormatting.DARK_GREEN).withItalic(false)));
        bottle.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("It was a potato once")
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(true)))));
        bottle.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CustomData.update(DataComponents.CUSTOM_DATA, bottle, tag -> tag.putInt(BLIGHT, rift));
        return bottle;
    }

    /** The rift level a blight brew was made at, or 0 if this is none. */
    public static int blightOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getIntOr(BLIGHT, 0);
    }

    /** What recipe viewers show for "anything rifted to this level": a few things as a workstation brings them back. */
    public static List<ItemStack> samples(int level) {
        List<ItemStack> shown = new ArrayList<>();
        for (net.minecraft.world.item.Item item : List.of(Items.CARROT, Items.POTATO, Items.BEETROOT, Items.APPLE, Items.MELON_SLICE, Items.SWEET_BERRIES,
                Items.COD, Items.SALMON, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.HONEY_BOTTLE, Items.RAW_IRON, Items.RAW_COPPER,
                Items.RAW_GOLD)) {
            shown.add(sample(item, level));
        }
        return shown;
    }

    /** One of this item as a rift of this level would bring it back: for showing (AlchyX RiftedIngredient.samples). Not for books. */
    public static ItemStack sample(net.minecraft.world.item.Item item, int level) {
        ItemStack stack = new ItemStack(item);
        if (stack.is(Items.ENCHANTED_BOOK)) return stack;
        ItemStack made = touch(null, stack, level); // (only a book asks the world anything)
        made.setCount(1);
        return made;
    }

    public static ItemStack touch(ServerLevel server, ItemStack stack, int level) {
        level = Math.clamp(level, 1, TOP);
        if (stack.isEmpty()) return stack;
        if (stack.is(Items.ENCHANTED_BOOK)) return book(server, stack, level);
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) return food(stack, food, List.of(), level);
        if (stack.getMaxStackSize() <= 1) return stack;
        // What a furnace takes (raw metal, an ore block): it stays one, marked and glinting, and SMELTS into more
        if (stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.ORES)) return ore(stack, level);
        // What is already the thing itself (a gem, a crop that is no food, a log...): more of it at once
        stack.setCount(stack.getCount() * (stack.is(Tags.Items.GEMS) ? times(level) : level >= 5 ? 3 : 2)); // (the basket takes it in pieces of a stack)
        return stack;
    }

    private static ItemStack ore(ItemStack stack, int level) {
        int times = times(level);
        String grade = level >= TOP ? "Void-Rifted " : level >= 3 ? "Deep-Rifted " : "Rifted ";
        ChatFormatting color = level >= TOP ? ChatFormatting.DARK_PURPLE : ChatFormatting.LIGHT_PURPLE;
        Component name = Component.literal(grade).append(stack.getHoverName()).withStyle(style -> style.withColor(color));
        RiftMark.mark(stack, level);
        stack.set(DataComponents.ITEM_NAME, name);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Smelts into " + times + " times as much")
                .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** The rift's hold on this thing when it was made (1 to 6), or 0 if it is not rifted (AlchyX RiftMark keeps it). */
    public static int levelOf(ItemStack stack) {
        return RiftMark.levelOf(stack);
    }

    public static boolean isRifted(ItemStack stack) {
        return levelOf(stack) > 0;
    }

    /**
     * What a furnace or a smoker gives for this input (AlchyX RiftMark.cooked asks, through AlchyFarm's RiftMark.onCooked): the recipe's result, and if the
     * input was rifted food and the result is food too, the result rifted to the same level, with its own effects
     * of the config or else the raw food's. Anything else: the result as it is.
     */
    public static ItemStack cooked(ItemStack input, ItemStack result) {
        int level = result.isEmpty() ? 0 : levelOf(input);
        if (level <= 0 || isRifted(result)) return result;
        FoodProperties food = result.get(DataComponents.FOOD);
        if (food != null) return food(result.copy(), food, Config.riftedEffects(input.getItem()), level);
        if (input.get(DataComponents.FOOD) != null) return result; // rifted food cooked into something that is not food
        // A rifted ore: so many times what the recipe gives (as far as a stack goes)
        return result.copyWithCount(Math.min(result.getMaxStackSize(), result.getCount() * times(level)));
    }

    /** A bottle of rifted honey (a glass bottle filled at a cask of it): a honey bottle rifted to this level. */
    public static ItemStack honeyBottle(int level) {
        ItemStack bottle = new ItemStack(Items.HONEY_BOTTLE);
        FoodProperties food = bottle.get(DataComponents.FOOD);
        return food == null ? bottle : food(bottle, food, List.of(), Math.clamp(level, 1, TOP));
    }

    /** One of Config pond.riftPotions, picked at random, at this level's share of its strength. Empty if the list is. */
    public static ItemStack potion(ServerLevel server, int level) {
        List<Config.RiftPotion> all = Config.riftPotions();
        if (all.isEmpty()) return ItemStack.EMPTY;
        Config.RiftPotion picked = all.get(server.getRandom().nextInt(all.size()));
        List<MobEffectInstance> effects = new ArrayList<>(picked.effects().size());
        for (MobEffectInstance effect : picked.effects()) {
            effects.add(new MobEffectInstance(effect.getEffect(), length(effect.getDuration(), level), scaled(effect.getAmplifier() + 1, level) - 1));
        }
        ItemStack bottle = new ItemStack(Items.POTION);
        bottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0x9B6FE0), effects, Optional.empty()));
        bottle.set(DataComponents.CUSTOM_NAME, Component.literal(picked.name()).withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
        bottle.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        bottle.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Drawn from the rift")
                .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)))));
        int mark = Math.clamp(level, 1, TOP);
        RiftMark.mark(bottle, mark); // a rifted thing, as rifted food is
        return bottle;
    }

    /** An effect's level at this rift level: the config's is the top's; below, its share, rounded up, 1 at least. */
    private static int scaled(int topLevel, int level) {
        return Math.max(1, (topLevel * Math.clamp(level, 1, TOP) + TOP - 1) / TOP);
    }

    /** An effect's length at this rift level: three eighths of the top's at level 1, all of it at the top. */
    private static int length(int topTicks, int level) {
        return Math.max(20, topTicks * (Math.clamp(level, 1, TOP) + 2) / (TOP + 2));
    }

    private static ItemStack food(ItemStack stack, FoodProperties food, List<Config.RiftEffect> otherwise, int level) {
        int times = times(level);
        List<Component> lore = new ArrayList<>(2);
        lore.add(Component.literal("Feeds " + times + " times as much").withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
        String grade = level >= TOP ? "Void-Rifted " : level >= 3 ? "Deep-Rifted " : "Rifted ";
        ChatFormatting color = level >= TOP ? ChatFormatting.DARK_PURPLE : ChatFormatting.LIGHT_PURPLE;
        Component name = Component.literal(grade).append(stack.getHoverName()).withStyle(style -> style.withColor(color));
        stack.set(DataComponents.FOOD, new FoodProperties(food.nutrition() * times, food.saturation() * times, true));
        List<Config.RiftEffect> gifts = Config.riftedEffects(stack.getItem());
        if (gifts.isEmpty()) gifts = otherwise;
        RiftMark.mark(stack, level);
        Consumable eaten = stack.get(DataComponents.CONSUMABLE);
        if (!gifts.isEmpty() && eaten != null) {
            List<ConsumeEffect> effects = new ArrayList<>(eaten.onConsumeEffects());
            List<MobEffectInstance> given = new ArrayList<>(gifts.size());
            for (Config.RiftEffect gift : gifts) {
                int strength = scaled(gift.level(), level), ticks = length(gift.seconds() * 20, level);
                given.add(new MobEffectInstance(gift.effect(), ticks, strength - 1));
                if (lore.size() < 9) {
                    lore.add(Component.empty().append(gift.effect().value().getDisplayName())
                            .append(strength > 1 ? " " + roman(strength) : "").append(" (" + time(ticks / 20) + ")")
                            .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));
                }
            }
            effects.add(new ApplyStatusEffectsConsumeEffect(given));
            stack.set(DataComponents.CONSUMABLE, new Consumable(eaten.consumeSeconds(), eaten.animation(), eaten.sound(), eaten.hasConsumeParticles(), effects));
        }
        stack.set(DataComponents.ITEM_NAME, name);
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII",
            "XVIII", "XIX", "XX"};

    private static String roman(int level) {
        return level < ROMAN.length ? ROMAN[level] : Integer.toString(level);
    }

    private static String time(int seconds) {
        return seconds >= 60 ? seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60 : seconds + " s";
    }

    private static ItemStack book(ServerLevel server, ItemStack stack, int level) {
        for (int round = 0; round < 1 + level / 2; round++) {
            ItemStack more = EnchantmentHelper.enchantItem(server.getRandom(), new ItemStack(Items.BOOK), 30, server.registryAccess(), Optional.empty());
            ItemEnchantments added = more.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            EnchantmentHelper.updateEnchantments(stack, mine -> added.entrySet().forEach(entry -> mine.upgrade(entry.getKey(), entry.getIntValue())));
        }
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Rifted: more is written in it than was")
                .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)))));
        return stack;
    }
}
