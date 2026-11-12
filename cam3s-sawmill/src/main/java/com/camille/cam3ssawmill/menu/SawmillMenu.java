package com.camille.cam3ssawmill.menu;

import java.util.List;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import com.camille.cam3ssawmill.ModSounds;
import com.camille.cam3ssawmill.block.ModBlocks;
import com.camille.cam3ssawmill.recipe.SawmillRecipeList;
import com.camille.cam3ssawmill.recipe.WoodcuttingRecipe;

/**
 * Works like the stonecutter menu, except that a recipe can consume several input items at once.
 *
 * <p>Slots: 0 = input, 1 = result, 2-28 = player inventory, 29-37 = hotbar.
 * A recipe can stay selected while there are not enough input items; the result slot
 * then stays empty until the player adds more.
 */
public class SawmillMenu extends AbstractContainerMenu {
	public static final int INPUT_SLOT = 0;
	public static final int RESULT_SLOT = 1;
	private static final int INVENTORY_START = 2;
	private static final int INVENTORY_END = 29;
	private static final int HOTBAR_END = 38;

	private final ContainerLevelAccess access;
	private final Level level;
	private final DataSlot selectedRecipeIndex = DataSlot.standalone();
	private List<RecipeHolder<WoodcuttingRecipe>> recipesForInput = List.of();
	private ItemStack lastInput = ItemStack.EMPTY;
	private long lastSoundTime;
	private Runnable slotUpdateListener = () -> {
	};

	public final Container input = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			SawmillMenu.this.slotsChanged(this);
			SawmillMenu.this.slotUpdateListener.run();
		}
	};

	private final ResultContainer resultContainer = new ResultContainer();
	public final Slot inputSlot;
	public final Slot resultSlot;

	/** Client-side constructor, used by the menu type. */
	public SawmillMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, ContainerLevelAccess.NULL);
	}

	public SawmillMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
		super(ModMenus.SAWMILL, containerId);
		this.access = access;
		this.level = playerInventory.player.level();

		this.inputSlot = this.addSlot(new Slot(this.input, 0, 20, 33));
		this.resultSlot = this.addSlot(new Slot(this.resultContainer, 1, 143, 33) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				SawmillMenu.this.onResultTaken(player, stack);
				super.onTake(player, stack);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}

		this.addDataSlot(this.selectedRecipeIndex);
	}

	public int getSelectedRecipeIndex() {
		return this.selectedRecipeIndex.get();
	}

	/** The recipes that accept the current input, in display order. */
	public List<RecipeHolder<WoodcuttingRecipe>> getRecipesForInput() {
		return this.recipesForInput;
	}

	public boolean hasRecipes() {
		return this.inputSlot.hasItem() && !this.recipesForInput.isEmpty();
	}

	/** True when the input slot holds enough items for this recipe. */
	public boolean hasEnoughInput(int recipeIndex) {
		return this.isValidRecipeIndex(recipeIndex)
				&& this.inputSlot.getItem().getCount() >= this.recipesForInput.get(recipeIndex).value().inputCount();
	}

	public void registerUpdateListener(Runnable listener) {
		this.slotUpdateListener = listener;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.SAWMILL);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.selectedRecipeIndex.get() == id || !this.isValidRecipeIndex(id)) {
			return false;
		}

		this.selectedRecipeIndex.set(id);
		this.setupResultSlot();
		return true;
	}

	private boolean isValidRecipeIndex(int index) {
		return index >= 0 && index < this.recipesForInput.size();
	}

	@Override
	public void slotsChanged(Container container) {
		ItemStack stack = this.inputSlot.getItem();

		if (!ItemStack.isSameItemSameComponents(stack, this.lastInput)) {
			// Another item: new list of recipes, nothing selected.
			this.lastInput = stack.copy();
			this.updateRecipes(stack);
		} else {
			// Same item, other count: the selected recipe may have become (un)affordable.
			this.lastInput = stack.copy();
			this.setupResultSlot();
		}
	}

	private void updateRecipes(ItemStack stack) {
		this.selectedRecipeIndex.set(-1);
		this.resultSlot.set(ItemStack.EMPTY);
		this.recipesForInput = SawmillRecipeList.forInput(this.level, stack);
	}

	private void setupResultSlot() {
		int index = this.selectedRecipeIndex.get();

		if (this.hasEnoughInput(index)) {
			WoodcuttingRecipe recipe = this.recipesForInput.get(index).value();
			this.resultSlot.set(recipe.assemble(new SingleRecipeInput(this.inputSlot.getItem()), this.level.registryAccess()));
		} else {
			this.resultSlot.set(ItemStack.EMPTY);
		}

		this.broadcastChanges();
	}

	private void onResultTaken(Player player, ItemStack stack) {
		int index = this.selectedRecipeIndex.get();
		int cost = this.isValidRecipeIndex(index) ? this.recipesForInput.get(index).value().inputCount() : 1;

		stack.onCraftedBy(player, stack.getCount());
		ItemStack removed = this.inputSlot.remove(cost);

		if (!removed.isEmpty()) {
			this.setupResultSlot();
		}

		this.access.execute((level, pos) -> {
			long time = level.getGameTime();

			// Only one sound per tick, even when shift-clicking a whole stack.
			if (this.lastSoundTime != time) {
				level.playSound(null, pos, ModSounds.SAWMILL_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
				this.lastSoundTime = time;
			}
		});
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);

		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			moved = stack.copy();

			if (slotIndex == RESULT_SLOT) {
				stack.getItem().onCraftedBy(stack, player);

				if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickCraft(stack, moved);
			} else if (slotIndex == INPUT_SLOT) {
				if (!this.moveItemStackTo(stack, INVENTORY_START, HOTBAR_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (SawmillRecipeList.isInput(this.level, stack)) {
				if (!this.moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (slotIndex < INVENTORY_END) {
				if (!this.moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			}

			slot.setChanged();

			if (stack.getCount() == moved.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(player, stack);

			if (slotIndex == RESULT_SLOT) {
				player.drop(stack, false);
			}

			this.broadcastChanges();
		}

		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.resultContainer.removeItemNoUpdate(1);
		this.access.execute((level, pos) -> this.clearContainer(player, this.input));
	}
}
