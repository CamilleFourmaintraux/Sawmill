package com.camille.cam3ssawmill.config;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * How the sawmill turns planks into other items. Every mode applies the fuel rule, and every mode
 * turns one plank into two sticks.
 *
 * <p>The first three modes work like the stonecutter: one plank in, as many items out as one plank
 * gives by crafting (rounded down, at least one), blocks only. They differ by the highest crafting
 * cost, in planks per item, an item can have to be made from a single plank, by whether items made
 * from any planks are included, and by a few exceptions that ignore the cost limit.
 */
public enum RecipeMode {
	/** Only what crafting already gives one for one (slabs, buttons), plus stairs. Variants of one wood type only. */
	RESTRICTED(1.0, false),

	/** Up to 2 planks per item, items made from any planks (ladders), plus trapdoors and fence gates. */
	VANILLA_LIKE(2.0, true),

	/** Up to 4 planks per item (crafting tables, composters, signs...), plus the same exceptions. */
	EXTENDED(4.0, true),

	/**
	 * Recipes follow crafting costs: several planks can go in, any item can come out (tools, boats,
	 * chests...), never more expensive than crafting, with a discount set by {@code maxDiscount}.
	 */
	REBALANCED(Double.POSITIVE_INFINITY, true);

	private final double maxOneInputCost;
	private final boolean anyPlankItems;

	RecipeMode(double maxOneInputCost, boolean anyPlankItems) {
		this.maxOneInputCost = maxOneInputCost;
		this.anyPlankItems = anyPlankItems;
	}

	/** True for the stonecutter-like modes: one plank in, blocks only. */
	public boolean oneInput() {
		return this != REBALANCED;
	}

	/** One-input modes: highest crafting cost, in planks, an item can have to be made from one plank. */
	public double maxOneInputCost() {
		return this.maxOneInputCost;
	}

	/** Whether items made from any planks (ladders, crafting tables, chests...) can be made. */
	public boolean anyPlankItems() {
		return this.anyPlankItems;
	}

	/**
	 * Items a one-input mode makes from one plank whatever their crafting cost: stairs in every mode,
	 * trapdoors and fence gates from VANILLA_LIKE on.
	 */
	public boolean ignoresCostLimit(Item item) {
		if (!this.oneInput() || !(item instanceof BlockItem blockItem)) {
			return false;
		}

		Block block = blockItem.getBlock();

		if (block instanceof StairBlock) {
			return true;
		}

		return this != RESTRICTED && (block instanceof TrapDoorBlock || block instanceof FenceGateBlock);
	}
}
