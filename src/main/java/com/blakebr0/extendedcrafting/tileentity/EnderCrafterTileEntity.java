package com.blakebr0.extendedcrafting.tileentity;

import com.blakebr0.cucumber.helper.StackHelper;
import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.cucumber.inventory.CachedRecipe;
import com.blakebr0.cucumber.inventory.OnContentsChangedFunction;
import com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity;
import com.blakebr0.cucumber.util.Localizable;
import com.blakebr0.extendedcrafting.api.crafting.IEnderCrafterRecipe;
import com.blakebr0.extendedcrafting.block.EnderAlternatorBlock;
import com.blakebr0.extendedcrafting.config.ModConfigs;
import com.blakebr0.extendedcrafting.container.EnderCrafterContainer;
import com.blakebr0.extendedcrafting.crafting.TableRecipeStorage;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.init.ModTileEntities;
import com.blakebr0.extendedcrafting.util.AlternatorParticleOffsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class EnderCrafterTileEntity extends BaseInventoryTileEntity implements MenuProvider {
	private final BaseItemStackHandler inventory;
	private final CachedRecipe<CraftingInput, IEnderCrafterRecipe> recipe;
	private int progress;
	private int progressReq;
	protected boolean isGridChanged = true;

	public EnderCrafterTileEntity(BlockPos pos, BlockState state) {
		this(ModTileEntities.ENDER_CRAFTER.get(), pos, state);
	}

	public EnderCrafterTileEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.inventory = createInventoryHandler((slot) -> this.onContentsChanged());
		this.recipe = new CachedRecipe<>(ModRecipeTypes.ENDER_CRAFTER.get());
	}

    @Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	@Override
	public void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
		super.loadAdditional(tag, lookup);
		this.progress = tag.getInt("Progress");
		this.progressReq = tag.getInt("ProgressReq");
	}

	@Override
	public void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) {
		super.saveAdditional(tag, lookup);
		tag.putInt("Progress", this.progress);
		tag.putInt("ProgressReq", this.progressReq);
	}

	@Override
	public Component getDisplayName() {
		return Localizable.of("container.extendedcrafting.ender_crafter").build();
	}

	@Override
	public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
		return EnderCrafterContainer.create(windowId, playerInventory, this.inventory, this.getBlockPos());
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EnderCrafterTileEntity tile) {
		var recipe = tile.getActiveRecipe();
		var selectedRecipe = tile.getSelectedRecipeGrid();

		if (recipe != null && (selectedRecipe == null || recipe.matches(selectedRecipe, level))) {
			var result = recipe.assemble(tile.inventory.toCraftingInput(3, 3, 0, 9), level.registryAccess());
			var output = tile.inventory.getStackInSlot(9);

			if (StackHelper.canCombineStacks(result, output)) {
				var alternators = tile.getAlternatorPositions();
				int alternatorCount = alternators.size();

				if (alternatorCount > 0) {
					tile.progress(alternatorCount, recipe.getCraftingTime());

					for (var alternatorPos : alternators) {
						var alternator = level.getBlockState(alternatorPos);
						var direction = alternator.getValue(EnderAlternatorBlock.FACING);

						if (level.isEmptyBlock(alternatorPos.relative(direction))) {
							tile.sendAlternatorParticles(alternatorPos, direction);
						}
					}

					if (tile.progress >= tile.progressReq) {
						for (int i = 0; i < tile.inventory.getSlots() - 1; i++) {
							tile.inventory.setStackInSlot(i, StackHelper.shrink(tile.inventory.getStackInSlot(i), 1, false));
						}

						tile.updateResult(result);
						tile.progress = 0;
					}

					tile.setChangedFast();
				}
			} else {
				if (tile.progress > 0 || tile.progressReq > 0) {
					tile.progress = 0;
					tile.progressReq = 0;

					tile.setChangedFast();
				}
			}
		} else {
			if (tile.progress > 0 || tile.progressReq > 0) {
				tile.progress = 0;
				tile.progressReq = 0;

				tile.setChangedFast();
			}
		}

		tile.dispatchIfChanged();
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

	private void updateResult(ItemStack stack) {
		var result = this.inventory.getStackInSlot(9);

		if (result.isEmpty()) {
			this.inventory.setStackInSlot(9, stack);
		} else {
			this.inventory.setStackInSlot(9, StackHelper.grow(result, stack.getCount()));
		}
	}

	private List<BlockPos> getAlternatorPositions() {
		List<BlockPos> alternators = new ArrayList<>();
		var level = this.getLevel();

		if (level != null) {
			var pos = this.getBlockPos();

			BlockPos.betweenClosedStream(pos.offset(-3, -3, -3), pos.offset(3, 3, 3)).forEach(aoePos -> {
				var block = level.getBlockState(aoePos).getBlock();
				if (block instanceof EnderAlternatorBlock)
					alternators.add(aoePos.immutable());
			});
		}

		return alternators;
	}

	private void progress(int alternators, int timeRequired) {
		this.progress++;

		int timeReq = 20 * timeRequired;
		double effectiveness = ModConfigs.ENDER_CRAFTER_ALTERNATOR_EFFECTIVENESS.get();
		this.progressReq = (int) Math.max(timeReq - (timeReq * (effectiveness * alternators)), 20);
	}

	private void sendAlternatorParticles(BlockPos pos, Direction direction) {
		if (this.getLevel() == null || this.getLevel().isClientSide())
			return;

		var level = (ServerLevel) this.getLevel();
		var offsets = AlternatorParticleOffsets.fromDirection(direction);

		double x = pos.getX() + offsets.x;
		double y = pos.getY() + offsets.y;
		double z = pos.getZ() + offsets.z;

		level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0.1D);
	}

	private void onContentsChanged() {
		this.isGridChanged = true;
		this.setChangedFast();
	}

	public int getProgress() {
		return this.progress;
	}

	public int getProgressRequired() {
		return this.progressReq;
	}

	// to be overridden by the auto version
	public TableRecipeStorage getRecipeStorage() {
		return null;
	}

	private CraftingInput getSelectedRecipeGrid() {
		var storage = this.getRecipeStorage();
		if (storage != null) {
			var grid = storage.getSelectedRecipeGrid();
			if (grid != null) {
				return grid.toCraftingInput(3, 3);
			}
		}

		return null;
	}

	public IEnderCrafterRecipe getActiveRecipe() {
		if (this.level == null)
			return null;

		if (!this.isGridChanged) {
			return this.recipe.get();
		}

		return this.recipe.checkAndGet(this.inventory.toCraftingInput(3, 3, 0, 9), this.level);
	}
}
