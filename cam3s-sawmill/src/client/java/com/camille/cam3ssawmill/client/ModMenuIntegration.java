package com.camille.cam3ssawmill.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Adds the config button to Mod Menu.
 *
 * <p>The screen is built with Cloth Config. When Cloth Config is not installed, no
 * screen is offered and the config file can still be edited by hand.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return SawmillConfigScreen::create;
		}

		return parent -> null;
	}
}
