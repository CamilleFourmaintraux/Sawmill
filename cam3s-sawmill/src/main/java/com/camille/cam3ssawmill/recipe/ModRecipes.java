package com.camille.cam3ssawmill.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import com.camille.cam3ssawmill.Cam3sSawmill;

public final class ModRecipes {
	public static final RecipeType<WoodcuttingRecipe> WOODCUTTING_TYPE = Registry.register(
			BuiltInRegistries.RECIPE_TYPE, Cam3sSawmill.id("woodcutting"), new RecipeType<WoodcuttingRecipe>() {
				@Override
				public String toString() {
					return Cam3sSawmill.MOD_ID + ":woodcutting";
				}
			});

	public static final RecipeSerializer<WoodcuttingRecipe> WOODCUTTING_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER, Cam3sSawmill.id("woodcutting"), new RecipeSerializer<WoodcuttingRecipe>() {
				@Override
				public MapCodec<WoodcuttingRecipe> codec() {
					return WoodcuttingRecipe.CODEC;
				}

				@Override
				public StreamCodec<RegistryFriendlyByteBuf, WoodcuttingRecipe> streamCodec() {
					return WoodcuttingRecipe.STREAM_CODEC;
				}
			});

	private ModRecipes() {
	}

	public static void initialize() {
		// Loads the class, which registers the recipe type and serializer.
	}
}
