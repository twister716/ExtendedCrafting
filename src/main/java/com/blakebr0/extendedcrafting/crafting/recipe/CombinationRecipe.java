package com.blakebr0.extendedcrafting.crafting.recipe;

import com.blakebr0.extendedcrafting.api.crafting.ICombinationRecipe;
import com.blakebr0.extendedcrafting.config.ModConfigs;
import com.blakebr0.extendedcrafting.init.ModRecipeSerializers;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.util.IngredientListCache;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;

import java.util.List;

public class CombinationRecipe implements ICombinationRecipe {
	private final ItemStack result;
	private final Ingredient input;
	private final NonNullList<Ingredient> inputs;
	private final int powerCost;
	private final int powerRate;

	public CombinationRecipe(Ingredient input, NonNullList<Ingredient> inputs, ItemStack result, int powerCost, int powerRate) {
		this.input = input;
		this.inputs = inputs;
		this.result = result;
		this.powerCost = powerCost;
		this.powerRate = powerRate;
	}

	@Override
	public boolean matches(CraftingInput inventory, Level level) {
		// -1 ingredient for the input item
		if (this.inputs.size() != inventory.ingredientCount() - 1)
			return false;

		var input = inventory.getItem(0);
		if (!this.input.test(input))
			return false;

		var inputs = NonNullList.<ItemStack>create();

		for (var i = 1; i < inventory.size(); i++) {
			var item = inventory.getItem(i);
			if (!item.isEmpty()) {
				inputs.add(item);
			}
		}

		return RecipeMatcher.findMatches(inputs, this.inputs) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput inventory, HolderLookup.Provider lookup) {
		return this.result.copy();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider provider) {
		return this.result;
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return this.inputs;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.COMBINATION.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ModRecipeTypes.COMBINATION.get();
	}

	@Override
	public Ingredient getInput() {
		return this.input;
	}

	@Override
	public int getPowerCost() {
		return this.powerCost;
	}

	@Override
	public int getPowerRate() {
		return this.powerRate;
	}

	@Override
	public List<Component> getInputsList() {
		return IngredientListCache.getInstance().getIngredientsList(this);
	}

	public static class Serializer implements RecipeSerializer<CombinationRecipe> {
		public static final MapCodec<CombinationRecipe> CODEC = RecordCodecBuilder.mapCodec(builder ->
				builder.group(
						Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(recipe -> recipe.input),
						Ingredient.CODEC_NONEMPTY
								.listOf()
								.fieldOf("ingredients")
								.flatXmap(
										field -> {
											var max = 48;
											var ingredients = field.toArray(Ingredient[]::new);
											if (ingredients.length == 0) {
												return DataResult.error(() -> "No ingredients for Combination recipe");
											} else {
												return ingredients.length > max
														? DataResult.error(() -> "Too many ingredients for Combination recipe. The maximum is: %s".formatted(max))
														: DataResult.success(NonNullList.of(Ingredient.EMPTY, ingredients));
											}
										},
										DataResult::success
								)
								.forGetter(recipe -> recipe.inputs),
						ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
						Codec.INT.fieldOf("power_cost").forGetter(recipe -> recipe.powerCost),
						Codec.INT.optionalFieldOf("power_rate", ModConfigs.CRAFTING_CORE_POWER_RATE.get()).forGetter(recipe -> recipe.powerRate)
				).apply(builder, CombinationRecipe::new)
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, CombinationRecipe> STREAM_CODEC = StreamCodec.of(
				CombinationRecipe.Serializer::toNetwork, CombinationRecipe.Serializer::fromNetwork
		);

		@Override
		public MapCodec<CombinationRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, CombinationRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static CombinationRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
			var input = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
			int size = buffer.readVarInt();
			var inputs = NonNullList.withSize(size, Ingredient.EMPTY);

			for (int i = 0; i < size; i++) {
				inputs.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
			}

			var result = ItemStack.STREAM_CODEC.decode(buffer);
			int powerCost = buffer.readVarInt();
			int powerRate = buffer.readVarInt();

			return new CombinationRecipe(input, inputs, result, powerCost, powerRate);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buffer, CombinationRecipe recipe) {
			Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.input);
			buffer.writeVarInt(recipe.inputs.size());

			for (var ingredient : recipe.inputs) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
			}

			ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
			buffer.writeVarInt(recipe.powerCost);
			buffer.writeVarInt(recipe.powerRate);
		}
	}

}
