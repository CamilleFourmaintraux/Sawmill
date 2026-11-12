package com.camille.cam3ssawmill.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;

import com.camille.cam3ssawmill.recipe.SawmillRecipeManager;

/**
 * {@code updateStaticRegistryTags} is where the item tags of a data pack load get filled in
 * (Fabric API fires its "tags loaded" event at the same place). It runs before the data is
 * sent to players, both when a world starts and on {@code /reload}.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
	@Shadow
	public abstract RecipeManager getRecipeManager();

	@Inject(method = "updateStaticRegistryTags", at = @At("TAIL"))
	private void cam3sSawmill$generateRecipes(CallbackInfo ci) {
		((SawmillRecipeManager) this.getRecipeManager()).cam3sSawmill$addGeneratedRecipes();
	}
}
