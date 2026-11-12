package com.camille.cam3ssawmill.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;

import com.camille.cam3ssawmill.block.ModBlocks;
import com.camille.cam3ssawmill.menu.ModMenus;

public class Cam3sSawmillClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// MenuScreens.register is made accessible by Fabric API.
		MenuScreens.register(ModMenus.SAWMILL, SawmillScreen::new);

		// The saw blade texture has transparent pixels.
		BlockRenderLayerMap.putBlock(ModBlocks.SAWMILL, ChunkSectionLayer.CUTOUT);
	}
}
