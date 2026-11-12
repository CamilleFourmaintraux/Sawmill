package com.camille.cam3ssawmill;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	/** Played when an item is taken out of the sawmill. */
	public static final SoundEvent SAWMILL_TAKE_RESULT = register("ui.sawmill.take_result");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Cam3sSawmill.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void initialize() {
		// Loads the class, which registers the sounds.
	}
}
