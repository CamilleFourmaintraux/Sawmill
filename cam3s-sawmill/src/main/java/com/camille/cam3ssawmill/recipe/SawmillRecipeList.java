package com.camille.cam3ssawmill.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;

/**
 * Every sawmill recipe known on one side (server or client), in display order.
 *
 * <p>The server and the client must list the recipes for one input in the same order,
 * because the client tells the server which recipe was clicked by its index. Both sides
 * read the same recipes (Fabric API syncs them) and sort them with the same keys, which are
 * identical on both sides: the result's numeric item ID (synced by Fabric's registry sync),
 * then the input count, then the recipe ID.
 */
public final class SawmillRecipeList {
	private static final Comparator<RecipeHolder<WoodcuttingRecipe>> ORDER = Comparator
			.<RecipeHolder<WoodcuttingRecipe>>comparingInt(holder -> BuiltInRegistries.ITEM.getId(holder.value().result().getItem()))
			.thenComparingInt(holder -> holder.value().inputCount())
			.thenComparing(holder -> holder.id().toString());

	// Last sorted list for each side, kept until that side's recipe collection changes (reload, new world).
	private static final Cache SERVER_CACHE = new Cache();
	private static final Cache CLIENT_CACHE = new Cache();

	private SawmillRecipeList() {
	}

	/**
	 * All sawmill recipes, sorted.
	 *
	 * <p>Server: read from the recipe manager itself, which holds the generated recipes.
	 * Client: read from the recipes sent by Fabric API's recipe sync.
	 */
	public static List<RecipeHolder<WoodcuttingRecipe>> all(Level level) {
		if (level instanceof ServerLevel serverLevel) {
			RecipeManager manager = serverLevel.getServer().getRecipeManager();
			return SERVER_CACHE.get(manager.getAllOfType(ModRecipes.WOODCUTTING_TYPE));
		}

		SynchronizedRecipes synced = level.recipeAccess().getSynchronizedRecipes();
		return CLIENT_CACHE.get(synced.getAllOfType(ModRecipes.WOODCUTTING_TYPE));
	}

	/** The sawmill recipes that accept this item, sorted. */
	public static List<RecipeHolder<WoodcuttingRecipe>> forInput(Level level, ItemStack input) {
		if (input.isEmpty()) {
			return List.of();
		}

		List<RecipeHolder<WoodcuttingRecipe>> matching = new ArrayList<>();

		for (RecipeHolder<WoodcuttingRecipe> holder : all(level)) {
			if (holder.value().ingredient().test(input)) {
				matching.add(holder);
			}
		}

		return matching;
	}

	/** True when at least one sawmill recipe accepts this item. */
	public static boolean isInput(Level level, ItemStack stack) {
		for (RecipeHolder<WoodcuttingRecipe> holder : all(level)) {
			if (holder.value().ingredient().test(stack)) {
				return true;
			}
		}

		return false;
	}

	/** Remembers the sorted copy of one collection, compared by identity. */
	private static final class Cache {
		private @Nullable Collection<RecipeHolder<WoodcuttingRecipe>> source;
		private List<RecipeHolder<WoodcuttingRecipe>> sorted = List.of();

		synchronized List<RecipeHolder<WoodcuttingRecipe>> get(Collection<RecipeHolder<WoodcuttingRecipe>> recipes) {
			if (recipes != this.source) {
				List<RecipeHolder<WoodcuttingRecipe>> copy = new ArrayList<>(recipes);
				copy.sort(ORDER);
				this.sorted = List.copyOf(copy);
				this.source = recipes;
			}

			return this.sorted;
		}
	}
}
