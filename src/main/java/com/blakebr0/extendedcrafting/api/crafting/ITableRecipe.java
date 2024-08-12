package com.blakebr0.extendedcrafting.api.crafting;

import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Used to represent an Extended Crafting Table recipe for the recipe type
 */
public interface ITableRecipe extends Recipe<CraftingInput> {
    int getTier();
    boolean hasRequiredTier();
}
