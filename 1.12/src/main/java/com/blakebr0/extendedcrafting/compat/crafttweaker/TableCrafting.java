package com.blakebr0.extendedcrafting.compat.crafttweaker;

import java.util.Arrays;
import java.util.List;

import com.blakebr0.extendedcrafting.compat.jei.CompatJEI;
import com.blakebr0.extendedcrafting.crafting.table.TableRecipeManager;
import com.blakebr0.extendedcrafting.crafting.table.TableRecipeShaped;
import com.blakebr0.extendedcrafting.crafting.table.TableRecipeShapeless;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.oredict.IOreDictEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.oredict.OreDictionary;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.extendedcrafting.TableCrafting")
public class TableCrafting {

	@ZenMethod
	public static void addShaped(IItemStack output, IIngredient[][] ingredients) {
		addShaped(0, output, ingredients);
	}

	@ZenMethod
	public static void addShaped(int tier, IItemStack output, IIngredient[][] ingredients) {
		if (tier > 4 || tier < 0) {
			CraftTweakerAPI.getLogger().logError("Unable to assign a tier to the Table Recipe for stack " + output.getDisplayName() + ". Tier cannot be greater than 4 or less than 0.");
			tier = 0;
		}

		int height = ingredients.length;
		int width = 0;
		for (IIngredient[] row : ingredients) {
			if (width < row.length) {
				width = row.length;
			}
		}
				
		int i = 0;
		NonNullList<Ingredient> input = NonNullList.withSize(height * width, Ingredient.EMPTY);
		for (int a = 0; a < height; a++) {
			for (int b = 0; b < ingredients[a].length; b++) {
				Ingredient ing = CraftingHelper.getIngredient(toActualObject(ingredients[a][b]));
				if (ing == null) {
					ing = Ingredient.EMPTY;
				}
				i = a * width + b;
				input.set(i, ing);
			}
		}

		CraftTweakerAPI.apply(new Add(new TableRecipeShaped(tier, toStack(output), width, height, input)));
	}

	@ZenMethod
	public static void addShapeless(IItemStack output, IIngredient[] ingredients) {
		addShapeless(0, output, ingredients);
	}

	@ZenMethod
	public static void addShapeless(int tier, IItemStack output, IIngredient[] ingredients) {
		if (tier > 4 || tier < 0) {
			CraftTweakerAPI.getLogger().logError("Unable to assign a tier to the Table Recipe for stack " + output.getDisplayName() + ". Tier cannot be greater than 4 or less than 0.");
			tier = 0;
		}

		CraftTweakerAPI.apply(new Add(new TableRecipeShapeless(tier, toStack(output), toObjects(ingredients))));
	}

	@ZenMethod
	public static void remove(IItemStack target) {
		CraftTweakerAPI.apply(new Remove(toStack(target)));
	}

	private static class Add implements IAction {
		IRecipe recipe;

		public Add(IRecipe add) {
			recipe = add;
		}

		@Override
		public void apply() {
			TableRecipeManager.getInstance().getRecipes().add(recipe);
		}

		@Override
		public String describe() {
			return "Adding a Table Crafting recipe for " + recipe.getRecipeOutput().getDisplayName();
		}
	}

	private static class Remove implements IAction {
		IRecipe recipe = null;
		ItemStack remove;

		public Remove(ItemStack remove) {
			this.remove = remove;
		}

		@Override
		public void apply() {
			for (Object obj : TableRecipeManager.getInstance().getRecipes()) {
				if (obj instanceof IRecipe) {
					IRecipe recipe = (IRecipe) obj;
					if (recipe.getRecipeOutput().isItemEqual(remove)) {
						this.recipe = recipe;
						TableRecipeManager.getInstance().getRecipes().remove(recipe);
						break;
					}
				}
			}
		}

		@Override
		public String describe() {
			return "Removing an Advanced Crafting recipe for " + remove.getDisplayName();
		}
	}

	private static ItemStack toStack(IItemStack item) {
		if (item == null) {
			return ItemStack.EMPTY;
		} else {
			Object internal = item.getInternal();
			if (internal == null || !(internal instanceof ItemStack)) {
				CraftTweakerAPI.getLogger().logError("Not a valid item stack: " + item);
			}
			return (ItemStack) internal;
		}
	}

	private static Object toObject(IIngredient ingredient) {
		if (ingredient == null)
			return null;
		else {
			if (ingredient instanceof IOreDictEntry) {
				return toString((IOreDictEntry) ingredient);
			} else if (ingredient instanceof IItemStack) {
				return toStack((IItemStack) ingredient);
			} else
				return null;
		}
	}

	private static Object[] toObjects(IIngredient[] list) {
		if (list == null)
			return null;
		Object[] ingredients = new Object[list.length];
		for (int x = 0; x < list.length; x++) {
			ingredients[x] = toActualObject(list[x]);
		}
		return ingredients;
	}

	private static List toList(IIngredient[] list) {
		return Arrays.asList(toObjects(list));
	}

	private static Object toActualObject(IIngredient ingredient) {
		if (ingredient == null)
			return null;
		else {
			if (ingredient instanceof IOreDictEntry) {
				return OreDictionary.getOres(toString((IOreDictEntry) ingredient));
			} else if (ingredient instanceof IItemStack) {
				return toStack((IItemStack) ingredient);
			} else
				return null;
		}
	}

	private static String toString(IOreDictEntry entry) {
		return ((IOreDictEntry) entry).getName();
	}
}