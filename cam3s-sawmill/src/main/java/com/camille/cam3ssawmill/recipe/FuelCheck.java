package com.camille.cam3ssawmill.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;

/**
 * Furnace burn times, used so the sawmill never turns planks into something that burns longer:
 * a recipe must have {@code inputCount × burn time of the input ≥ output count × burn time of the output}.
 *
 * <p>Planks that do not burn at all (crimson, warped) are not checked: they get the same recipes
 * as the planks that burn, see {@link WoodcuttingRecipeGenerator}.
 */
@FunctionalInterface
public interface FuelCheck {
	/** No burn times available: every plank counts as non-flammable, so nothing is filtered. */
	FuelCheck NONE = stack -> 0;

	int burnTime(ItemStack stack);

	static FuelCheck of(FuelValues fuelValues) {
		return fuelValues::burnDuration;
	}

	default boolean burns(Item item) {
		return this.burnTime(new ItemStack(item)) > 0;
	}

	default boolean burnsNoLonger(Item input, int inputCount, ItemStack output) {
		long inputFuel = (long) inputCount * this.burnTime(new ItemStack(input));
		long outputFuel = (long) output.getCount() * this.burnTime(output.copyWithCount(1));
		return inputFuel >= outputFuel;
	}
}
