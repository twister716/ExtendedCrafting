package com.blakebr0.extendedcrafting.crafting.recipe;

import com.blakebr0.cucumber.crafting.ShapedRecipePatternCodecs;
import com.blakebr0.cucumber.util.TriFunction;
import com.blakebr0.extendedcrafting.api.crafting.ITableRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeSerializers;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

public class ShapedTableRecipe implements ITableRecipe {
	private final ShapedRecipePattern pattern;
	private final ItemStack result;
	private final int tier;
	private TriFunction<Integer, Integer, ItemStack, ItemStack> transformer;

	public ShapedTableRecipe(ShapedRecipePattern pattern, ItemStack result, int tier) {
		this.pattern = pattern;
		this.result = result;
		this.tier = tier;
	}

	@Override
	public boolean matches(CraftingInput inventory, Level level) {
		if (this.tier != 0 && this.tier != getTierFromGridSize(inventory))
			return false;

		return this.pattern.matches(inventory);
	}

	@Override
	public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider provider) {
		return this.result.copy();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width >= this.pattern.width() && height >= this.pattern.height();
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider lookup) {
		return this.result;
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return this.pattern.ingredients();
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.SHAPED_TABLE.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ModRecipeTypes.TABLE.get();
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput inventory) {
		var remaining = NonNullList.withSize(inventory.size(), ItemStack.EMPTY);

		if (this.transformer != null) {
			int size = (int) Math.sqrt(inventory.size());

			for (int i = 0; i <= size - this.pattern.width(); i++) {
				for (int j = 0; j <= size - this.pattern.height(); j++) {
					if (this.checkMatch(inventory, i, j, true)) {
						for (int k = 0; k < this.pattern.width(); k++) {
							for (int l = 0; l < this.pattern.width(); l++) {
								int index = (this.pattern.width() - 1 - l) + i + (k + j) * size;
								var stack = inventory.getItem(index);

								remaining.set(index, this.transformer.apply(l, k, stack));
							}
						}

						break;
					}

					if (this.checkMatch(inventory, i, j, false)) {
						for (int k = 0; k < this.pattern.height(); k++) {
							for (int l = 0; l < this.pattern.width(); l++) {
								int index = l + i + (k + j) * size;
								var stack = inventory.getItem(index);

								remaining.set(index, this.transformer.apply(l, k, stack));
							}
						}

						break;
					}
				}
			}

			return remaining;
		}

		for (int i = 0; i < remaining.size(); ++i) {
			var item = inventory.getItem(i);
			if (item.hasCraftingRemainingItem()) {
				remaining.set(i, item.getCraftingRemainingItem());
			}
		}

		return remaining;
	}

	@Override
	public int getTier() {
		if (this.tier > 0) return this.tier;

		return this.pattern.width() < 4 && this.pattern.height() < 4 ? 1
				 : this.pattern.width() < 6 && this.pattern.height() < 6 ? 2
				 : this.pattern.width() < 8 && this.pattern.height() < 8 ? 3
				 : 4;
	}

	@Override
	public boolean hasRequiredTier() {
		return this.tier > 0;
	}

	public int getWidth() {
		return this.pattern.width();
	}

	public int getHeight() {
		return this.pattern.height();
	}

	private boolean checkMatch(CraftingInput inventory, int x, int y, boolean mirror) {
		int size = (int) Math.sqrt(inventory.size());
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int k = i - x;
				int l = j - y;
				var ingredient = Ingredient.EMPTY;

				if (k >= 0 && l >= 0 && k < this.pattern.width() && l < this.pattern.height()) {
					if (mirror) {
						ingredient = this.pattern.ingredients().get(this.pattern.width() - k - 1 + l * this.pattern.width());
					} else {
						ingredient = this.pattern.ingredients().get(k + l * this.pattern.width());
					}
				}

				if (!ingredient.test(inventory.getItem(i + j * size))) {
					return false;
				}
			}
		}

		return true;
	}

	public void setTransformer(TriFunction<Integer, Integer, ItemStack, ItemStack> transformer) {
		this.transformer = transformer;
	}

	private static int getTierFromGridSize(CraftingInput inventory) {
		int size = inventory.size();
		return size < 10 ? 1
				: size < 26 ? 2
				: size < 50 ? 3
				: 4;
	}

	public static class Serializer implements RecipeSerializer<ShapedTableRecipe> {
		public static final MapCodec<ShapedTableRecipe> CODEC = RecordCodecBuilder.mapCodec(builder ->
				builder.group(
						ShapedRecipePatternCodecs.MAP_CODEC.forGetter(recipe -> recipe.pattern),
						ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
						Codec.INT.optionalFieldOf("tier", 0).forGetter(recipe -> recipe.tier)
				).apply(builder, ShapedTableRecipe::new)
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, ShapedTableRecipe> STREAM_CODEC = StreamCodec.of(
				ShapedTableRecipe.Serializer::toNetwork, ShapedTableRecipe.Serializer::fromNetwork
		);

		@Override
		public MapCodec<ShapedTableRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, ShapedTableRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static ShapedTableRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
			var pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
			var result = ItemStack.STREAM_CODEC.decode(buffer);
			int tier = buffer.readVarInt();

			return new ShapedTableRecipe(pattern, result, tier);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buffer, ShapedTableRecipe recipe) {
			ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
			ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
			buffer.writeVarInt(recipe.tier);
		}
	}
}