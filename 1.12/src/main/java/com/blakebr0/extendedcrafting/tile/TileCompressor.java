package com.blakebr0.extendedcrafting.tile;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.blakebr0.cucumber.helper.StackHelper;
import com.blakebr0.cucumber.lib.CustomEnergyStorage;
import com.blakebr0.cucumber.util.VanillaPacketDispatcher;
import com.blakebr0.extendedcrafting.config.ModConfig;
import com.blakebr0.extendedcrafting.crafting.CompressorRecipe;
import com.blakebr0.extendedcrafting.crafting.CompressorRecipeManager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

public class TileCompressor extends TileEntity implements ISidedInventory, ITickable {

	private NonNullList<ItemStack> inventoryStacks = NonNullList.<ItemStack> withSize(3, ItemStack.EMPTY);
	private final CustomEnergyStorage energy = new CustomEnergyStorage(ModConfig.confCompressorRFCapacity);
	private ItemStack materialStack = ItemStack.EMPTY;
	private int materialCount;
	private int progress;
	private boolean ejecting = false;
	private int oldEnergy;
	
	private static List<CompressorRecipe> getValidRecipes(ItemStack stack) {
		List<CompressorRecipe> valid = new ArrayList<CompressorRecipe>();
		if (!StackHelper.isNull(stack)) {
			for (CompressorRecipe recipe : CompressorRecipeManager.getInstance().getRecipes()) {
				if (!StackHelper.isNull(recipe.getInput()) && recipe.getInput().isItemEqual(stack)) {
					valid.add(recipe);
				}
			}
		}
		return valid;
	}

	@Override
	public void update() {
		boolean mark = false;

		if (!this.getWorld().isRemote) {
			CompressorRecipe recipe = this.getRecipe();
			ItemStack output = this.getStackInSlot(0);
			ItemStack input = this.getStackInSlot(1);

			if (!input.isEmpty()) {
				if (this.materialStack.isEmpty()) {
					this.materialStack = input.copy();
				}
				if (input.isItemEqual(this.materialStack)) {
					StackHelper.decrease(input, 1, false);
					this.materialCount++;
					if (!mark) {
						mark = true;
					}
				}
			}

			if (recipe != null) {
				if (this.getEnergy().getEnergyStored() >= recipe.getPowerCost()) {
					if (this.materialCount >= recipe.getInputCount()) {
						this.process(recipe);
						if (this.progress == recipe.getPowerCost()) {
							if ((output.isEmpty() || output.isItemEqual(recipe.getOutput())) && output.getCount() < recipe.getOutput().getMaxStackSize()) {
								this.addStackToSlot(0, recipe.getOutput());
								if (recipe.consumeCatalyst()) {
									StackHelper.decrease(this.getStackInSlot(2), 1, false);
								}
								this.progress = 0;
								this.materialCount -= recipe.getInputCount();
								if (this.materialCount <= 0) {
									this.materialStack = ItemStack.EMPTY;
								}
							}
						}
					}
				}
			}

			if (this.ejecting) {
				if (this.materialCount > 0 && !this.materialStack.isEmpty()) {
					ItemStack toAdd = this.materialStack.copy();
					toAdd.setCount(1);
					if (this.addStackToSlot(0, toAdd)) {
						--this.materialCount;
						if (this.materialCount < 1) {
							this.materialStack = ItemStack.EMPTY;
							this.ejecting = false;
						}
						if (this.progress > 0) {
							this.progress = 0;
						}
						if (!mark) {
							mark = true;
						}
					}
				}
			}
		}

		if (this.oldEnergy != this.energy.getEnergyStored()) {
			this.oldEnergy = this.energy.getEnergyStored();
			if (!mark) {
				mark = true;
			}
		}

		if (mark) {
			this.markDirty();
		}
	}

	public CompressorRecipe getRecipe() {
		List<CompressorRecipe> recipes = getValidRecipes(getStackForRecipe());
		if (!recipes.isEmpty()) {
			for (CompressorRecipe recipe : recipes) {
				if ((this.materialStack.isItemEqual(recipe.getInput()) || this.getStackInSlot(1).isItemEqual(recipe.getInput())) && this.getStackInSlot(2).isItemEqual(recipe.getCatalyst())) {
					return recipe;
				}
			}
		}
		return null;
	}

	private ItemStack getStackForRecipe() {
		return !this.getStackInSlot(1).isEmpty() ? this.getStackInSlot(1) : this.materialStack;
	}
	
	private boolean process(CompressorRecipe recipe) {
		int extract = ModConfig.confCompressorRFRate;
		int difference = recipe.getPowerCost() - this.progress;
		if (difference < extract) {
			extract = difference;
		}
		int extracted = this.getEnergy().extractEnergy(extract, false);
		this.progress += extracted;
		if (this.progress >= recipe.getPowerCost()) {
			return true;
		}
		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		this.inventoryStacks = NonNullList.<ItemStack> withSize(3, ItemStack.EMPTY);
		ItemStackHelper.loadAllItems(compound, this.inventoryStacks);
		this.materialCount = compound.getInteger("MaterialCount");
		this.materialStack = new ItemStack(compound.getCompoundTag("MaterialStack"));
		this.progress = compound.getInteger("Progress");
		this.ejecting = compound.getBoolean("Ejecting");
		this.energy.setEnergy(compound.getInteger("Energy"));
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound = super.writeToNBT(compound);
		ItemStackHelper.saveAllItems(compound, this.inventoryStacks);
		compound.setInteger("MaterialCount", this.materialCount);
		compound.setTag("MaterialStack", this.materialStack.writeToNBT(new NBTTagCompound()));
		compound.setInteger("Progress", this.progress);
		compound.setBoolean("Ejecting", this.ejecting);
		compound.setInteger("Energy", this.energy.getEnergyStored());
		return compound;
	}

	public boolean addStackToSlot(int slot, ItemStack stack) {
		ItemStack slotStack = this.getStackInSlot(slot);
		if (slotStack.isEmpty()) {
			this.setInventorySlotContents(slot, stack);
			return true;
		} else {
			if (slotStack.isItemEqual(stack) && slotStack.getCount() + stack.getCount() <= slotStack.getMaxStackSize()) {
				ItemStack newStack = slotStack.copy();
				newStack.grow(stack.getCount());
				this.setInventorySlotContents(slot, newStack);
				return true;
			}
		}
		return false;
	}

	@Override
	public void markDirty() {
		super.markDirty();
		VanillaPacketDispatcher.dispatchTEToNearbyPlayers(this);
	}

	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(pos, -1, this.getUpdateTag());
	}

	@Override
	public void onDataPacket(NetworkManager manager, SPacketUpdateTileEntity packet) {
		this.readFromNBT(packet.getNbtCompound());
	}

	@Override
	public final NBTTagCompound getUpdateTag() {
		return this.writeToNBT(new NBTTagCompound());
	}

	public CustomEnergyStorage getEnergy() {
		return this.energy;
	}

	public ItemStack getMaterialStack() {
		return this.materialStack;
	}

	public int getMaterialCount() {
		return this.materialCount;
	}

	public boolean isEjecting() {
		return this.ejecting;
	}

	public void toggleEjecting() {
		if (this.materialCount > 0) {
			this.ejecting = !this.ejecting;
			this.markDirty();
		}
	}

	public int getProgress() {
		return this.progress;
	}

	@Override
	public int getSizeInventory() {
		return this.inventoryStacks.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemstack : this.inventoryStacks) {
			if (!itemstack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getStackInSlot(int index) {
		return (ItemStack) this.inventoryStacks.get(index);
	}

	@Override
	public ItemStack decrStackSize(int index, int count) {
		return ItemStackHelper.getAndSplit(inventoryStacks, index, count);
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		return ItemStackHelper.getAndRemove(inventoryStacks, index);
	}

	@Override
	public void setInventorySlotContents(int index, ItemStack stack) {
		ItemStack itemstack = (ItemStack) this.inventoryStacks.get(index);
		boolean flag = !stack.isEmpty() && stack.isItemEqual(itemstack) && ItemStack.areItemStackTagsEqual(stack, itemstack);
		this.inventoryStacks.set(index, stack);

		if (stack.getCount() > this.getInventoryStackLimit()) {
			stack.setCount(this.getInventoryStackLimit());
		}

		if (index == 0 && !flag) {
			this.markDirty();
		}
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return this.getWorld().getTileEntity(this.getPos()) == this && player.getDistanceSq(this.getPos().add(0.5, 0.5, 0.5)) <= 64;
	}

	@Override
	public void openInventory(EntityPlayer player) {

	}

	@Override
	public void closeInventory(EntityPlayer player) {

	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		if (index == 2) {
			return false;
		} else if (index != 0) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {

	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {

	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side) {
		return side == EnumFacing.DOWN ? new int[] { 0 } : new int[] { 1 };
	}

	@Override
	public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
		return this.isItemValidForSlot(index, stack);
	}

	@Override
	public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
		return index == 0;
	}

	IItemHandler handlerTop = new SidedInvWrapper(this, EnumFacing.UP);
	IItemHandler handlerBottom = new SidedInvWrapper(this, EnumFacing.DOWN);
	IItemHandler handlerSide = new SidedInvWrapper(this, EnumFacing.WEST);

	@Nonnull
	@Override
	public <T> T getCapability(@Nonnull Capability<T> capability, @Nonnull EnumFacing facing) {
		if (facing != null && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			if (facing == EnumFacing.DOWN) {
				return (T) handlerBottom;
			} else if (facing == EnumFacing.UP) {
				return (T) handlerTop;
			} else {
				return (T) handlerSide;
			}
		} else if (capability == CapabilityEnergy.ENERGY) {
			return CapabilityEnergy.ENERGY.cast(energy);
		}
		return super.getCapability(capability, facing);
	}
}
