package com.camille.cam3ssawmill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;

import com.camille.cam3ssawmill.block.ModBlocks;
import com.camille.cam3ssawmill.config.SawmillConfig;
import com.camille.cam3ssawmill.menu.ModMenus;
import com.camille.cam3ssawmill.recipe.ModRecipes;

public class Cam3sSawmill implements ModInitializer {
	public static final String MOD_ID = "cam3s_sawmill";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SawmillConfig.load();

		ModSounds.initialize();
		ModBlocks.initialize();
		ModMenus.initialize();
		ModRecipes.initialize();

		// Since 1.21.2 the server no longer sends recipes to the client.
		// Fabric API sends every recipe that uses this serializer (generated ones included),
		// so the client can show the list of choices in the sawmill screen.
		// Must run on both sides, which is the case in the main initializer.
		RecipeSynchronization.synchronizeRecipeSerializer(ModRecipes.WOODCUTTING_SERIALIZER);
	}
}
