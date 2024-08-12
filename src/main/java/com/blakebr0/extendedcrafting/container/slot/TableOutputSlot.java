package com.blakebr0.extendedcrafting.container.slot;

import com.blakebr0.extendedcrafting.container.BasicAutoTableContainer;
import com.blakebr0.extendedcrafting.container.BasicTableContainer;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.CommonHooks;

public class TableOutputSlot extends Slot {
    private final AbstractContainerMenu container;
    private final CraftingContainer matrix;

    public TableOutputSlot(AbstractContainerMenu container, CraftingContainer matrix, Container inventory, int index, int xPosition, int yPosition) {
        super(inventory, index, xPosition, yPosition);
        this.container = container;
        this.matrix = matrix;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        boolean isVanilla = false;

        if (this.container instanceof BasicTableContainer table) {
            isVanilla = table.isVanillaRecipe();
        } else if (this.container instanceof BasicAutoTableContainer table) {
            isVanilla = table.isVanillaRecipe();
        }

        NonNullList<ItemStack> remaining;

        CommonHooks.setCraftingPlayer(player);

        var level = player.level();

        if (isVanilla) {
            remaining = level.getRecipeManager().getRemainingItemsFor(RecipeType.CRAFTING, this.matrix.asCraftInput(), level);
        } else {
            remaining = level.getRecipeManager().getRemainingItemsFor(ModRecipeTypes.TABLE.get(), this.matrix.asCraftInput(), level);
        }

        CommonHooks.setCraftingPlayer(null);

        for (int i = 0; i < remaining.size(); i++) {
            var slotStack = this.matrix.getItem(i);
            var remainingStack = remaining.get(i);

            if (!slotStack.isEmpty()) {
                this.matrix.removeItem(i, 1);
                slotStack = this.matrix.getItem(i);
            }

            if (!remainingStack.isEmpty()) {
                if (slotStack.isEmpty()) {
                    this.matrix.setItem(i, remainingStack);
                } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack)) {
                    remainingStack.grow(slotStack.getCount());
                    this.matrix.setItem(i, remainingStack);
                } else if (!player.getInventory().add(remainingStack)) {
                    player.drop(remainingStack, false);
                }
            }
        }

        this.container.slotsChanged(this.matrix);
    }
}
