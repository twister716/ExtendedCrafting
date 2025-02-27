package com.blakebr0.extendedcrafting.compat.crafttweaker;

import com.blakebr0.extendedcrafting.api.crafting.IFluxCrafterRecipe;
import com.blakebr0.extendedcrafting.config.ModConfigs;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedFluxCrafterRecipe;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapelessFluxCrafterRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.CraftTweakerConstants;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@ZenCodeType.Name("mods.extendedcrafting.FluxCrafting")
@ZenRegister
public final class FluxCrafting implements IRecipeManager<IFluxCrafterRecipe> {
    @Override
    public RecipeType<IFluxCrafterRecipe> getRecipeType() {
        return ModRecipeTypes.FLUX_CRAFTER.get();
    }

    @ZenCodeType.Method
    public void addShaped(String name, IItemStack output, IIngredient[][] inputs, int powerRequired) {
        addShaped(name, output, inputs, powerRequired, ModConfigs.FLUX_CRAFTER_POWER_RATE.get());
    }

    @ZenCodeType.Method
    public void addShaped(String name, IItemStack output, IIngredient[][] inputs, int powerRequired, int powerRate) {
        var id = CraftTweakerConstants.rl(this.fixRecipeName(name));

        int height = inputs.length;
        int width = 0;
        for (var row : inputs) {
            if (width < row.length) {
                width = row.length;
            }
        }

        var ingredients = NonNullList.withSize(height * width, Ingredient.EMPTY);

        for (int a = 0; a < height; a++) {
            for (int b = 0; b < inputs[a].length; b++) {
                var ing = inputs[a][b].asVanillaIngredient();
                int i = a * width + b;
                ingredients.set(i, ing);
            }
        }

        var pattern = new ShapedRecipePattern(width, height, ingredients, Optional.empty());
        var recipe = new ShapedFluxCrafterRecipe(pattern, output.getInternal(), powerRequired, powerRate);

        CraftTweakerAPI.apply(new ActionAddRecipe<>(this, new RecipeHolder<>(id, recipe)));
    }

    @ZenCodeType.Method
    public void addShapeless(String name, IItemStack output, IIngredient[] inputs, int powerRequired) {
        addShapeless(name, output, inputs, powerRequired, ModConfigs.FLUX_CRAFTER_POWER_RATE.get());
    }

    @ZenCodeType.Method
    public void addShapeless(String name, IItemStack output, IIngredient[] inputs, int powerRequired, int powerRate) {
        var id = CraftTweakerConstants.rl(this.fixRecipeName(name));
        var recipe = new ShapelessFluxCrafterRecipe(toIngredientsList(inputs), output.getInternal(), powerRequired, powerRate);

        CraftTweakerAPI.apply(new ActionAddRecipe<>(this, new RecipeHolder<>(id, recipe)));
    }

    private static NonNullList<Ingredient> toIngredientsList(IIngredient... ingredients) {
        return Arrays.stream(ingredients)
                .map(IIngredient::asVanillaIngredient)
                .collect(Collectors.toCollection(NonNullList::create));
    }
}