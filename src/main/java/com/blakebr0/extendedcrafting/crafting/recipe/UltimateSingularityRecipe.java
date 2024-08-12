package com.blakebr0.extendedcrafting.crafting.recipe;

import com.blakebr0.extendedcrafting.init.ModItems;
import com.blakebr0.extendedcrafting.init.ModRecipeSerializers;
import com.blakebr0.extendedcrafting.singularity.SingularityRegistry;
import com.blakebr0.extendedcrafting.singularity.SingularityUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class UltimateSingularityRecipe extends ShapelessTableRecipe {
    private static boolean ingredientsLoaded = false;

    public UltimateSingularityRecipe(ItemStack output) {
        super(NonNullList.create(), output, 4);
    }

    @Override
    public boolean matches(CraftingInput inventory, Level level) {
        // ensure ingredients list is initialized
        var ingredients = this.getIngredients();

        // in the case there are no ingredients, the recipe should never match
        return !ingredients.isEmpty() && super.matches(inventory, level);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        if (!ingredientsLoaded) {
            super.getIngredients().clear();

            SingularityRegistry.getInstance().getSingularities()
                    .stream()
                    .filter(singularity -> singularity.isInUltimateSingularity() && singularity.getIngredient() != Ingredient.EMPTY)
                    .limit(81)
                    .map(SingularityUtils::getItemForSingularity)
                    .map(Ingredient::of)
                    .forEach(super.getIngredients()::add);

            ingredientsLoaded = true;
        }

        return super.getIngredients();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ULTIMATE_SINGULARITY.get();
    }

    public static void invalidate() {
        ingredientsLoaded = false;
    }

    public static class Serializer implements RecipeSerializer<UltimateSingularityRecipe> {
        public static final MapCodec<UltimateSingularityRecipe> CODEC = MapCodec.unit(() -> new UltimateSingularityRecipe(new ItemStack(ModItems.ULTIMATE_SINGULARITY.get())));
        public static final StreamCodec<RegistryFriendlyByteBuf, UltimateSingularityRecipe> STREAM_CODEC = StreamCodec.of(
                UltimateSingularityRecipe.Serializer::toNetwork, UltimateSingularityRecipe.Serializer::fromNetwork
        );

        @Override
        public MapCodec<UltimateSingularityRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, UltimateSingularityRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        public static UltimateSingularityRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            return new UltimateSingularityRecipe(new ItemStack(ModItems.ULTIMATE_SINGULARITY.get()));
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, UltimateSingularityRecipe recipe) { }
    }
}
