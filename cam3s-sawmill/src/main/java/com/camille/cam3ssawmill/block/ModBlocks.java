package com.camille.cam3ssawmill.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import com.camille.cam3ssawmill.Cam3sSawmill;

public final class ModBlocks {
	public static final ResourceKey<Block> SAWMILL_KEY = ResourceKey.create(Registries.BLOCK, Cam3sSawmill.id("sawmill"));

	/** Same strength and sound as a crafting table, mined faster with an axe. */
	public static final Block SAWMILL = Registry.register(BuiltInRegistries.BLOCK, SAWMILL_KEY,
			new SawmillBlock(BlockBehaviour.Properties.of()
					.setId(SAWMILL_KEY)
					.mapColor(MapColor.WOOD)
					.instrument(NoteBlockInstrument.BASS)
					.strength(2.5F)
					.sound(SoundType.WOOD)
					.ignitedByLava()));

	public static final ResourceKey<Item> SAWMILL_ITEM_KEY = ResourceKey.create(Registries.ITEM, Cam3sSawmill.id("sawmill"));

	public static final Item SAWMILL_ITEM = Registry.register(BuiltInRegistries.ITEM, SAWMILL_ITEM_KEY,
			new BlockItem(SAWMILL, new Item.Properties().setId(SAWMILL_ITEM_KEY).useBlockDescriptionPrefix()));

	private ModBlocks() {
	}

	public static void initialize() {
		// Right after the stonecutter in the "Functional Blocks" creative tab.
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(entries -> entries.addAfter(Items.STONECUTTER, SAWMILL_ITEM));
	}
}
