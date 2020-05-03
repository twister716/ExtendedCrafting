package com.blakebr0.extendedcrafting.tileentity;

import com.blakebr0.cucumber.energy.CustomEnergyStorage;
import com.blakebr0.cucumber.helper.StackHelper;
import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.cucumber.lib.Localizable;
import com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity;
import com.blakebr0.extendedcrafting.api.crafting.ITableRecipe;
import com.blakebr0.extendedcrafting.api.crafting.RecipeTypes;
import com.blakebr0.extendedcrafting.config.ModConfigs;
import com.blakebr0.extendedcrafting.container.AdvancedAutoTableContainer;
import com.blakebr0.extendedcrafting.container.BasicAutoTableContainer;
import com.blakebr0.extendedcrafting.container.EliteAutoTableContainer;
import com.blakebr0.extendedcrafting.container.UltimateAutoTableContainer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.IIntArray;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;

public abstract class AutoTableTileEntity extends BaseInventoryTileEntity implements ITickableTileEntity, INamedContainerProvider {
    private ITableRecipe recipe;
    private int progress;
    private boolean running = true;
    private int oldEnergy;
    protected final IIntArray data = new IIntArray() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0:
                    return AutoTableTileEntity.this.getEnergy().getEnergyStored();
                case 1:
                    return AutoTableTileEntity.this.getEnergy().getMaxEnergyStored();
                case 2:
                    return AutoTableTileEntity.this.getProgress();
                case 3:
                    return AutoTableTileEntity.this.getProgressRequired();
                case 4:
                    return AutoTableTileEntity.this.isRunning() ? 1 : 0;
                default:
                    return 0;
            }
        }

        @Override
        public void set(int index, int value) {

        }

        @Override
        public int size() {
            return 5;
        }
    };

    public AutoTableTileEntity(TileEntityType<?> type) {
        super(type);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        tag = super.write(tag);
        tag.putInt("Progress", this.progress);
        tag.putBoolean("Running", this.running);
        tag.putInt("Energy", this.getEnergy().getEnergyStored());

        return tag;
    }

    @Override
    public void read(CompoundNBT tag) {
        super.read(tag);
        this.progress = tag.getInt("Progress");
        this.running = tag.getBoolean("Running");
        this.getEnergy().setEnergy(tag.getInt("Energy"));
    }

    @Override
    public void tick() {
        boolean mark = false;

        World world = this.getWorld();
        CustomEnergyStorage energy = this.getEnergy();
        if (world != null) {
            if (this.running) {
                this.updateRecipeInventory();
                IInventory recipeInventory = this.getRecipeInventory().toIInventory();
                if (this.recipe == null || !this.recipe.matches(recipeInventory, world)) {
                    this.recipe = world.getRecipeManager().getRecipe(RecipeTypes.TABLE, recipeInventory, world).orElse(null);
                }

                if (!world.isRemote()) {
                    if (this.recipe != null) {
                        BaseItemStackHandler inventory = this.getInventory();
                        ItemStack result = this.recipe.getCraftingResult(recipeInventory);
                        int outputSlot = inventory.getSlots() - 1;
                        ItemStack output = inventory.getStackInSlot(outputSlot);
                        Integer powerRate = ModConfigs.AUTO_TABLE_POWER_RATE.get();

                        if (StackHelper.canCombineStacks(result, output) && energy.getEnergyStored() >= powerRate) {
                            this.progress++;
                            energy.extractEnergy(powerRate, false);

                            if (this.progress >= this.getProgressRequired()) {
                                for (int i = 0; i < recipeInventory.getSizeInventory(); i++) {
                                    inventory.extractItem(i, 1, false);
                                }

                                this.updateResult(result, outputSlot);
                                this.progress = 0;
                            }

                            mark = true;
                        }
                    } else {
                        if (this.progress > 0) {
                            this.progress = 0;
                            mark = true;
                        }
                    }
                }
            } else {
                if (this.progress > 0) {
                    this.progress = 0;
                    mark = true;
                }
            }
        }

        if (this.oldEnergy != energy.getEnergyStored()) {
            this.oldEnergy = energy.getEnergyStored();
            if (!mark)
                mark = true;
        }

        if (mark)
            this.markDirty();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!this.isRemoved() && cap == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.orEmpty(cap, LazyOptional.of(this::getEnergy));
        }

        return super.getCapability(cap, side);
    }

    private void updateRecipeInventory() {
        BaseItemStackHandler inventory = this.getInventory();
        this.getRecipeInventory().setSize(inventory.getSlots() - 1);
        for (int i = 0; i < inventory.getSlots() - 1; i++) {
            this.getRecipeInventory().setStackInSlot(i, inventory.getStackInSlot(i));
        }
    }

    private void updateResult(ItemStack stack, int slot) {
        BaseItemStackHandler inventory = this.getInventory();
        ItemStack result = inventory.getStackInSlot(9);
        if (result.isEmpty()) {
            inventory.setStackInSlot(slot, stack);
        } else {
            inventory.setStackInSlot(slot, StackHelper.increase(result.copy(), 1));
        }
    }

    public int getProgress() {
        return this.progress;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void toggleRunning() {
        this.running = !this.running;
        this.markDirty();
    }

    public abstract int getProgressRequired();

    public abstract BaseItemStackHandler getRecipeInventory();

    public abstract CustomEnergyStorage getEnergy();

    public static class Basic extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory = new BaseItemStackHandler(10);
        private final BaseItemStackHandler recipeInventory = new BaseItemStackHandler(9);
        private final CustomEnergyStorage energy = new CustomEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get());

        public Basic() {
            super(ModTileEntities.BASIC_AUTO_TABLE.get());
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public ITextComponent getDisplayName() {
            return Localizable.of("container.extendedcrafting.basic_table").build();
        }

        @Override
        public Container createMenu(int windowId, PlayerInventory playerInventory, PlayerEntity player) {
            return BasicAutoTableContainer.create(windowId, playerInventory, this::isUsableByPlayer, this.inventory, this.data, this.getPos());
        }

        @Override
        public int getProgressRequired() {
            return 20;
        }

        @Override
        public BaseItemStackHandler getRecipeInventory() {
            return this.recipeInventory;
        }

        @Override
        public CustomEnergyStorage getEnergy() {
            return this.energy;
        }
    }

    public static class Advanced extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory = new BaseItemStackHandler(26);
        private final BaseItemStackHandler recipeInventory = new BaseItemStackHandler(25);
        private final CustomEnergyStorage energy = new CustomEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get() * 2);

        public Advanced() {
            super(ModTileEntities.ADVANCED_AUTO_TABLE.get());
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public ITextComponent getDisplayName() {
            return Localizable.of("container.extendedcrafting.advanced_table").build();
        }

        @Override
        public Container createMenu(int windowId, PlayerInventory playerInventory, PlayerEntity player) {
            return AdvancedAutoTableContainer.create(windowId, playerInventory, this::isUsableByPlayer, this.inventory, this.data, this.getPos());
        }

        @Override
        public int getProgressRequired() {
            return 40;
        }

        @Override
        public BaseItemStackHandler getRecipeInventory() {
            return this.recipeInventory;
        }

        @Override
        public CustomEnergyStorage getEnergy() {
            return this.energy;
        }
    }

    public static class Elite extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory = new BaseItemStackHandler(50);
        private final BaseItemStackHandler recipeInventory = new BaseItemStackHandler(49);
        private final CustomEnergyStorage energy = new CustomEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get() * 4);

        public Elite() {
            super(ModTileEntities.ELITE_AUTO_TABLE.get());
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public ITextComponent getDisplayName() {
            return Localizable.of("container.extendedcrafting.elite_table").build();
        }

        @Override
        public Container createMenu(int windowId, PlayerInventory playerInventory, PlayerEntity player) {
            return EliteAutoTableContainer.create(windowId, playerInventory, this::isUsableByPlayer, this.inventory, this.data, this.getPos());
        }

        @Override
        public int getProgressRequired() {
            return 60;
        }

        @Override
        public BaseItemStackHandler getRecipeInventory() {
            return this.recipeInventory;
        }

        @Override
        public CustomEnergyStorage getEnergy() {
            return this.energy;
        }
    }

    public static class Ultimate extends AutoTableTileEntity {
        private final BaseItemStackHandler inventory = new BaseItemStackHandler(82);
        private final BaseItemStackHandler recipeInventory = new BaseItemStackHandler(81);
        private final CustomEnergyStorage energy = new CustomEnergyStorage(ModConfigs.AUTO_TABLE_POWER_CAPACITY.get() * 8);

        public Ultimate() {
            super(ModTileEntities.ULTIMATE_AUTO_TABLE.get());
        }

        @Override
        public BaseItemStackHandler getInventory() {
            return this.inventory;
        }

        @Override
        public ITextComponent getDisplayName() {
            return Localizable.of("container.extendedcrafting.ultimate_table").build();
        }

        @Override
        public Container createMenu(int windowId, PlayerInventory playerInventory, PlayerEntity player) {
            return UltimateAutoTableContainer.create(windowId, playerInventory, this::isUsableByPlayer, this.inventory, this.data, this.getPos());
        }

        @Override
        public int getProgressRequired() {
            return 80;
        }

        @Override
        public BaseItemStackHandler getRecipeInventory() {
            return this.recipeInventory;
        }

        @Override
        public CustomEnergyStorage getEnergy() {
            return this.energy;
        }
    }
}