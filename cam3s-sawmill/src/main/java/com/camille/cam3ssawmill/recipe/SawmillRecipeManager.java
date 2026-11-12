package com.camille.cam3ssawmill.recipe;

/** Added to the server's RecipeManager by a mixin. */
public interface SawmillRecipeManager {
	/** Generates the sawmill recipes from the loaded ones and adds them. Needs item tags to be bound. */
	void cam3sSawmill$addGeneratedRecipes();
}
