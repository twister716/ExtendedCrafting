package com.blakebr0.extendedcrafting.tileentity;

import com.blakebr0.cucumber.energy.BaseEnergyStorage;
import com.blakebr0.cucumber.helper.StackHelper;
import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.cucumber.inventory.OnContentsChangedFunction;
import com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity;
import com.blakebr0.cucumber.util.Localizable;
import com.blakebr0.extendedcrafting.api.TableCraftingInput;
import com.blakebr0.extendedcrafting.config.ModConfigs;
import com.blakebr0.extendedcrafting.container.AdvancedAutoTableContainer;
import com.blakebr0.extendedcrafting.container.BasicAutoTableContainer;
import com.blakebr0.extendedcrafting.container.EliteAutoTableContainer;
import com.blakebr0.extendedcrafting.container.UltimateAutoTableContainer;
import com.blakebr0.extendedcrafting.crafting.TableRecipeStorage;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.init.ModTileEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

public abstract class AutoTableTileEntity extends BaseInventoryTileEntity implements MenuProvider {
    private Recipe<CraftingInput> recipe;
    private int progress;
    private boolean running = true;
    private boolean isGridChanged = true;

    public AutoTableTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.loadAdditional(tag, lookup);
        this.progress = tag.getInt("Progress");
        this.running = tag.getBoolean("Running");
        this.getEnergy().deserializeNBT(lookup, tag.get("Energy"));
        this.getRecipeStorage().deserializeNBT(lookup, tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
        super.saveAdditional(tag, lookup);
        tag.putInt("Progress", this.progress);
        tag.putBoolean("Running", this.running);
        tag.putInt("Energy", this.getEnergy().getEnergyStored());
        tag.merge(this.getRecipeStorage().serializeNBT(lookup));
    }

    @Override
    public void onLoad() {
        super.onLoad();

        // on load, we will re-validate the recipe outputs to ensure they are still correct
        if (this.level != null && !this.level.isClientSide()) {
            this.getRecipeStorage().validate(inventory -> {
                var tableInventory = TableCraftingInput.of(inventory.width(), inventory.height(), inventory.items(), this.getTier());
                return this.level.getRecipeManager()
                        .getRecipeFor(ModRecipeTypes.TABLE.get(), tableInventory, this.level)
                        .map(r -> r.value().assemble(tableInventory, this.level.registryAccess()))
                        .orElse(ItemStack.EMPTY);
            });
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AutoTableTileEntity tile) {
        var energy = tile.getEnergy();

        if (tile.running) {
            var recipe = tile.getActiveRecipe();

            if (recipe != null && tile.matchesSelectedRecipe(recipe)) {
                var recipeInventory = tile.getRecipeInventory();
                var inventory = tile.getInventory();
                var result = recipe.assemble(recipeInventory, level.registryAccess());
                int outputSlot = inventory.getSlots() - 1;
                var output = inventory.getStackInSlot(outputSlot);
                int powerRate = ModConfigs.AUTO_TABLE_POWER_RATE.get();

                if (StackHelper.canCombineStacks(result, output) && energy.getEnergyStored() >= powerRate) {
                    tile.progress++;
                    energy.extractEnergy(powerRate, false);

                    if (tile.progress >= tile.getProgressRequired()) {
                        var remaining = recipe.getRemainingItems(recipeInventory);

                        for (int k = 0; k < recipeInventory.height(); k++) {
                            for (int l = 0; l < recipeInventory.width(); l++) {
                                var size = (recipeInventory.tier() * 2) + 1;
                                var index = l + recipeInventory.left() + (k + recipeInventory.top()) * size;
                                var remainingStack = remaining.get(l + k * recipeInventory.width());
                                if (!remainingStack.isEmpty()) {
                                    inventory.setStackInSlot(index, remainingStack);
                                } else {
                                    inventory.setStackInSlot(index, StackHelper.shrink(inventory.getStackInSlot(index), 1, false));
                                }
                            }
                        }

                        tile.updateResult(result, outputSlot);
                        tile.progress = 0;
                        tile.isGridChanged = true;
                    }

                    tile.setChangedFast();
                }
            } else {
                if (tile.progress > 0) {
                    tile.progress = 0;
                    tile.setChangedFast();
                }
            }
        } else {
            if (tile.progress > 0) {
                tile.progress = 0;
                tile.setChangedFast();
            }
        }

        int insertPowerRate = ModConfigs.AUTO_TABLE_INSERT_POWER_RATE.get();

        if (tile.getEnergy().getEnergyStored() >= insertPowerRate) {
            int selected = tile.getRecipeStorage().getSelected();
            if (selected != -1) {
                tile.getAboveInventory().ifPresent(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        var stack = handler.getStackInSlot(i);

                        if (!stack.isEmpty() && !handler.extractItem(i, 1, true).isEmpty()) {
                            var inserted = tile.tryInsertItemIntoGrid(stack);

                            if (inserted) {
                                handler.extractItem(i, 1, false);
                                break;
                            }
                        }
                    }
                });
            }
        }

        tile.dispatchIfChanged();
    }

    public int getProgress() {
        return this.progress;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void toggleRunning() {
        this.running = !this.running;
        this.setChangedAndDispatch();
    }

    public void selectRecipe(int index) {
        this.getRecipeStorage().setSelected(index);
        this.setChangedAndDispatch();
    }

    public void saveRecipe(int index) {
        var level = this.getLevel();
        if (level == null)
            return;

        var inventory = this.getRecipeInventory();
        var recipe = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.TABLE.get(), inventory, level)
                .orElse(null);

        var result = ItemStack.EMPTY;

        if (recipe != null) {
            result = recipe.value().assemble(inventory, level.registryAccess());
        } else {
            var vanilla = level.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, inventory, level)
                    .orElse(null);

            if (vanilla != null) {
                result = vanilla.value().assemble(inventory, level.registryAccess());
            }
        }

        this.getRecipeStorage().setRecipe(index, this.getInventory(), result);
        this.setChangedAndDispatch();
    }

    public void deleteRecipe(int index) {
        this.getRecipeStorage().unsetRecipe(index);
        this.setChangedAndDispatch();
    }

    public TableCraftingInput getRecipeInventory() {
        var inventory = this.getInventory();
        var size = (int) Math.sqrt(inventory.getSlots() - 1);
        return TableCraftingInput.of(size, size, inventory.getStacks().subList(0, inventory.getSlots() - 1), this.getTier());
    }

    public Recipe<CraftingInput> getActiveRecipe() {
        if (this.level == null)
            return null;

        var inventory = this.getRecipeInventory();

        if (this.isGridChanged && (this.recipe == null || !this.recipe.matches(inventory, this.level))) {
            //noinspection unchecked
            this.recipe = (Recipe<CraftingInput>) (Object) this.level.getRecipeManager()
                    .getRecipeFor(ModRecipeTypes.TABLE.get(), inventory, this.level)
                    .map(RecipeHolder::value)
                    .orElse(null);

            if (this.recipe == null && ModConfigs.TABLE_USE_VANILLA_RECIPES.get() && this instanceof Basic) {
                this.recipe = this.level.getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, inventory, this.level)
                        .map(RecipeHolder::value)
                        .orElse(null);
            }

            this.isGridChanged = false;
        }

        return this.recipe;
    }

    public boolean matchesSelectedRecipe(Recipe<CraftingInput> recipe) {
        if (this.level == null)
            return false;

        var selectedRecipe = this.getRecipeStorage().getSelectedRecipe();
        if (selectedRecipe == null)
            return true;

        var size = (int) Math.sqrt(this.getInventory().getSlots() - 1);

        return recipe.matches(selectedRecipe.toCraftingInput(size, size), this.level);
    }

    public abstract int getProgressRequired();

    public abstract TableRecipeStorage getRecipeStorage();

    public abstract BaseEnergyStorage getEnergy();

    public abstract int getTier();

    protected void onContentsChanged(int slot) {
        this.isGridChanged = true;
        this.setChangedFast();
    }

    private void updateResult(ItemStack stack, int slot) {
        var inventory = this.getInventory();
        var result = inventory.getStackInSlot(inventory.getSlots() - 1);

        if (result.isEmpty()) {
            inventory.setStackInSlot(slot, stack);
        } else {
            inventory.setStackInSlot(slot, StackHelper.grow(result, stack.getCount()));
        }
    }

    private void addStackToSlot(ItemStack stack, int slot) {
        var inventory = this.getInventory();
        var stackInSlot = inventory.getStackInSlot(slot);

        if (stackInSlot.isEmpty()) {
            inventory.setStackInSlot(slot, stack);
        } else {
            inventory.setStackInSlot(slot, StackHelper.grow(stackInSlot, stack.getCount()));
        }
    }

    private Optional<IItemHandler> getAboveInventory() {
        var level = this.getLevel();
        var pos = this.getBlockPos().above();

        if (level != null) {
            var capability = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN);
            return Optional.ofNullable(capability);
        }

        return Optional.empty();
    }

    private boolean tryInsertItemIntoGrid(ItemStack input) {
        var inventory = this.getInventory();
        var stackToPut = ItemStack.EMPTY;
        var recipe = this.getRecipeStorage().getSelectedRecipe();
        int slotToPut = -1;
        boolean isGridChanged = false;

        // last slot in the inventory is the output slot
        var slots = inventory.getSlots() - 1;

        for (int i = 0; i < slots; i++) {
            var slot = inventory.getStackInSlot(i);
            var recipeStack = recipe.getStackInSlot(i);

            if (((slot.isEmpty() || StackHelper.areStacksEqual(input, slot)) && StackHelper.areStacksEqual(input, recipeStack))) {
                if (slot.isEmpty() || slot.getCount() < slot.getMaxStackSize()) {
                    if (slot.isEmpty()) {
                        slotToPut = i;
                        isGridChanged = true;
                        break;
                    } else if (stackToPut.isEmpty() || slot.getCount() < stackToPut.getCount()) {
                        slotToPut = i;
                        stackToPut = slot;
                    }
                }
            }
        }

        this.isGridChanged |= isGridChanged;

        if (slotToPut > -1) {
            int insertPowerRate = ModConfigs.AUTO_TABLE_INSERT_POWER_RATE.get();
            var toInsert = StackHelper.withSize(input, 1, false);

            this.addStackToSlot(toInsert, slotToPut);
            this.getEnergy().extractEnergy(insertPowerRate, false);

            return true;
        }

        return false;
    }

    public static class Basic extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory;
        private final BaseEnergyStorage energy;
        private final TableRecipeStorage recipeStorage;

        public Basic(BlockPos pos, BlockState state) {
            super(ModTileEntities.BASIC_AUTO_TABLE.get(), pos, state);
            this.inventory = createInventoryHandler(this::onContentsChanged);
            this.recipeStorage = new TableRecipeStorage(10);
            this.energy = new BaseEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get(), this::setChangedFast);
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public Component getDisplayName() {
            return Localizable.of("container.extendedcrafting.basic_table").build();
        }

        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
            return BasicAutoTableContainer.create(windowId, playerInventory, this.inventory, this.getBlockPos());
        }

        @Override
        public int getProgressRequired() {
            return 20;
        }

        @Override
        public TableRecipeStorage getRecipeStorage() {
            return this.recipeStorage;
        }

        @Override
        public BaseEnergyStorage getEnergy() {
            return this.energy;
        }

        @Override
        public int getTier() {
            return 1;
        }

        public static BaseItemStackHandler createInventoryHandler() {
            return createInventoryHandler(null);
        }

        public static BaseItemStackHandler createInventoryHandler(OnContentsChangedFunction onContentsChanged) {
            return BaseItemStackHandler.create(10, onContentsChanged, builder -> {
                builder.setOutputSlots(9);
                builder.setCanInsert((slot, stack) -> false);
            });
        }
    }

    public static class Advanced extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory;
        private final BaseEnergyStorage energy;
        private final TableRecipeStorage recipeStorage;

        public Advanced(BlockPos pos, BlockState state) {
            super(ModTileEntities.ADVANCED_AUTO_TABLE.get(), pos, state);
            this.inventory = createInventoryHandler(this::onContentsChanged);
            this.recipeStorage = new TableRecipeStorage(26);
            this.energy = new BaseEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get() * 2, this::setChangedFast);
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public Component getDisplayName() {
            return Localizable.of("container.extendedcrafting.advanced_table").build();
        }

        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
            return AdvancedAutoTableContainer.create(windowId, playerInventory, this.inventory, this.getBlockPos());
        }

        @Override
        public int getProgressRequired() {
            return 40;
        }

        @Override
        public TableRecipeStorage getRecipeStorage() {
            return this.recipeStorage;
        }

        @Override
        public BaseEnergyStorage getEnergy() {
            return this.energy;
        }

        @Override
        public int getTier() {
            return 2;
        }

        public static BaseItemStackHandler createInventoryHandler() {
            return createInventoryHandler(null);
        }

        public static BaseItemStackHandler createInventoryHandler(OnContentsChangedFunction onContentsChanged) {
            return BaseItemStackHandler.create(26, onContentsChanged, builder -> {
                builder.setOutputSlots(25);
                builder.setCanInsert((slot, stack) -> false);
            });
        }
    }

    public static class Elite extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory;
        private final BaseEnergyStorage energy;
        private final TableRecipeStorage recipeStorage;

        public Elite(BlockPos pos, BlockState state) {
            super(ModTileEntities.ELITE_AUTO_TABLE.get(), pos, state);
            this.inventory = createInventoryHandler(this::onContentsChanged);
            this.recipeStorage = new TableRecipeStorage(50);
            this.energy = new BaseEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get() * 4, this::setChangedFast);
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public Component getDisplayName() {
            return Localizable.of("container.extendedcrafting.elite_table").build();
        }

        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
            return EliteAutoTableContainer.create(windowId, playerInventory, this.inventory, this.getBlockPos());
        }

        @Override
        public int getProgressRequired() {
            return 60;
        }

        @Override
        public TableRecipeStorage getRecipeStorage() {
            return this.recipeStorage;
        }

        @Override
        public BaseEnergyStorage getEnergy() {
            return this.energy;
        }

        @Override
        public int getTier() {
            return 3;
        }

        public static BaseItemStackHandler createInventoryHandler() {
            return createInventoryHandler(null);
        }

        public static BaseItemStackHandler createInventoryHandler(OnContentsChangedFunction onContentsChanged) {
            return BaseItemStackHandler.create(50, onContentsChanged, builder -> {
                builder.setOutputSlots(49);
                builder.setCanInsert((slot, stack) -> false);
            });
        }
    }

    public static class Ultimate extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory;
        private final BaseEnergyStorage energy;
        private final TableRecipeStorage recipeStorage;

        public Ultimate(BlockPos pos, BlockState state) {
            super(ModTileEntities.ULTIMATE_AUTO_TABLE.get(), pos, state);
            this.inventory = createInventoryHandler(this::onContentsChanged);
            this.recipeStorage = new TableRecipeStorage(82);
            this.energy = new BaseEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get() * 8, this::setChangedFast);
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public Component getDisplayName() {
            return Localizable.of("container.extendedcrafting.ultimate_table").build();
        }

        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
            return UltimateAutoTableContainer.create(windowId, playerInventory, this.inventory, this.getBlockPos());
        }

        @Override
        public int getProgressRequired() {
            return 80;
        }

        @Override
        public TableRecipeStorage getRecipeStorage() {
            return this.recipeStorage;
        }

        @Override
        public BaseEnergyStorage getEnergy() {
            return this.energy;
        }

        @Override
        public int getTier() {
            return 4;
        }

        public static BaseItemStackHandler createInventoryHandler() {
            return createInventoryHandler(null);
        }

        public static BaseItemStackHandler createInventoryHandler(OnContentsChangedFunction onContentsChanged) {
            return BaseItemStackHandler.create(82, onContentsChanged, builder -> {
                builder.setOutputSlots(81);
                builder.setCanInsert((slot, stack) -> false);
            });
        }
    }
}
