package com.camille.cam3ssawmill.mixin;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.entity.FuelValues;

import com.camille.cam3ssawmill.Cam3sSawmill;
import com.camille.cam3ssawmill.config.SawmillConfig;
import com.camille.cam3ssawmill.recipe.FuelCheck;
import com.camille.cam3ssawmill.recipe.SawmillRecipeManager;
import com.camille.cam3ssawmill.recipe.WoodcuttingRecipeGenerator;

/**
 * Adds the generated sawmill recipes to the recipes loaded from data packs.
 *
 * <p>Generation needs item tags ({@code #minecraft:planks}, {@code #minecraft:logs}), which are only
 * filled at the very end of a data pack load, after the recipes are read. So it is triggered from
 * {@link ReloadableServerResourcesMixin} once the tags are bound, then the recipe manager is
 * finalized again so that everything built from the recipe list (recipe book data, Fabric API's
 * recipe sync) includes the generated recipes. This runs at every world load and {@code /reload}.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements SawmillRecipeManager {
	/** Smelting time of a furnace, the base burn times are expressed for. */
	@Unique
	private static final int FURNACE_SMELT_TIME = 200;

	@Shadow
	private RecipeMap recipes;

	@Shadow
	public abstract void finalizeRecipeLoading(FeatureFlagSet enabledFeatures);

	@Unique
	private @Nullable FeatureFlagSet cam3sSawmill$enabledFeatures;

	@Unique
	private HolderLookup.Provider cam3sSawmill$registries; // null until the constructor ran

	/** The recipe list this manager already added generated recipes to, so they are never added twice. */
	@Unique
	private @Nullable RecipeMap cam3sSawmill$generatedMap;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void cam3sSawmill$rememberRegistries(HolderLookup.Provider registries, CallbackInfo ci) {
		this.cam3sSawmill$registries = registries;
	}

	@Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
	private void cam3sSawmill$rememberFeatures(FeatureFlagSet enabledFeatures, CallbackInfo ci) {
		this.cam3sSawmill$enabledFeatures = enabledFeatures;
	}

	/**
	 * Furnace burn times for this data pack load, built the same way the server builds them
	 * (vanilla fuels, then fuels added by mods through Fabric API's fuel events).
	 */
	@Unique
	private FuelCheck cam3sSawmill$fuelCheck() {
		if (this.cam3sSawmill$registries == null) {
			Cam3sSawmill.LOGGER.warn("Burn times unavailable, sawmill recipes are generated without the fuel check");
			return FuelCheck.NONE;
		}

		FeatureFlagSet features = this.cam3sSawmill$enabledFeatures != null ? this.cam3sSawmill$enabledFeatures : FeatureFlags.DEFAULT_FLAGS;

		try {
			return FuelCheck.of(FuelValues.vanillaBurnTimes(this.cam3sSawmill$registries, features, FURNACE_SMELT_TIME));
		} catch (RuntimeException e) {
			Cam3sSawmill.LOGGER.warn("Could not read burn times, sawmill recipes are generated without the fuel check", e);
			return FuelCheck.NONE;
		}
	}

	@Override
	public void cam3sSawmill$addGeneratedRecipes() {
		if (this.recipes == this.cam3sSawmill$generatedMap) {
			return;
		}

		try {
			RecipeMap loaded = this.recipes;
			List<RecipeHolder<?>> generated = WoodcuttingRecipeGenerator.generate(loaded.values(), SawmillConfig.get(), this.cam3sSawmill$fuelCheck());

			if (generated.isEmpty()) {
				return;
			}

			List<RecipeHolder<?>> all = new ArrayList<>(loaded.values().size() + generated.size());
			all.addAll(loaded.values());
			all.addAll(generated);

			this.recipes = RecipeMap.create(all);
			this.cam3sSawmill$generatedMap = this.recipes;

			// Rebuilds what vanilla derives from the recipe list. If the manager was not finalized yet,
			// its own finalization will use the new list anyway.
			if (this.cam3sSawmill$enabledFeatures != null) {
				this.finalizeRecipeLoading(this.cam3sSawmill$enabledFeatures);
			}
		} catch (RuntimeException e) {
			// Never break a data pack load: without generated recipes the sawmill still works with data pack recipes.
			Cam3sSawmill.LOGGER.error("Could not generate the sawmill recipes", e);
		}
	}
}
