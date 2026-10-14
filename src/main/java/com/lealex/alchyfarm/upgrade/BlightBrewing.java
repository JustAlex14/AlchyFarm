package com.lealex.alchyfarm.upgrade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * The blight brew's brewing recipes, which no recipe viewer lists (they are code, not the game's potion mixes: a
 * viewer has nothing to read). On a brewing stand:
 * a RIFTED poisonous potato over AWKWARD potions = blight brews, as bad as the potato's rift
 * is deep (Rifted.blight); gunpowder over blight brews = splash ones; dragon's breath over splash ones = lingering
 * ones. The game's own splash and lingering mixes only know named potions, so these two are told here as well.
 * To be hinted at in the guide book, never told.
 */
public final class BlightBrewing {
    private BlightBrewing() {}

    public static void register(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(new IBrewingRecipe() { // the potato
            @Override
            public boolean isInput(ItemStack input) {
                if (!input.is(Items.POTION) || Rifted.blightOf(input) > 0) return false;
                PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                return contents.is(Potions.AWKWARD); // (as every potion of the game starts: from awkward ones)
            }

            @Override
            public boolean isIngredient(ItemStack ingredient) {
                return ingredient.is(Items.POISONOUS_POTATO) && Rifted.levelOf(ingredient) > 0;
            }

            @Override
            public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                return isInput(input) && isIngredient(ingredient) ? Rifted.blight(Items.POTION, Rifted.levelOf(ingredient)) : ItemStack.EMPTY;
            }
        });
        event.getBuilder().addRecipe(reshaped(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION));
        event.getBuilder().addRecipe(reshaped(Items.SPLASH_POTION, Items.DRAGON_BREATH, Items.LINGERING_POTION));
    }

    /** A blight brew of one form, with this over it, becomes the same brew in another form. */
    private static IBrewingRecipe reshaped(Item from, Item with, Item to) {
        return new IBrewingRecipe() {
            @Override
            public boolean isInput(ItemStack input) {
                return input.is(from) && Rifted.blightOf(input) > 0;
            }

            @Override
            public boolean isIngredient(ItemStack ingredient) {
                return ingredient.is(with);
            }

            @Override
            public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
                return isInput(input) && isIngredient(ingredient) ? Rifted.blight(to, Rifted.blightOf(input)) : ItemStack.EMPTY;
            }
        };
    }
}
