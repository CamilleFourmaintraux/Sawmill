package com.camille.cam3ssawmill.menu;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

import com.camille.cam3ssawmill.Cam3sSawmill;

public final class ModMenus {
	// The MenuType constructor is made accessible by Fabric API.
	public static final MenuType<SawmillMenu> SAWMILL = Registry.register(BuiltInRegistries.MENU, Cam3sSawmill.id("sawmill"),
			new MenuType<>(SawmillMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void initialize() {
		// Loads the class, which registers the menu type.
	}
}
