package com.camille.cam3ssawmill.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.level.Level;

import com.camille.cam3ssawmill.block.ModBlocks;

/**
 * A sawmill recipe: {@code inputCount} items matching {@code ingredient} give {@code result}.
 *
 * <p>Most of them are generated at every data pack reload (see {@link WoodcuttingRecipeGenerator}),
 * but data packs can also add their own:
 * <pre>{
 *   "type": "cam3s_sawmill:woodcutting",
 *   "ingredient": "minecraft:oak_planks",
 *   "input_count": 2,
 *   "result": { "id": "minecraft:oak_door", "count": 1 }
 * }</pre>
 */
public class WoodcuttingRecipe implements Recipe<SingleRecipeInput> {
	public static final MapCodec<WoodcuttingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.optionalFieldOf("group", "").forGetter(WoodcuttingRecipe::group),
			Ingredient.CODEC.fieldOf("ingredient").forGetter(WoodcuttingRecipe::ingredient),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("input_count", 1).forGetter(WoodcuttingRecipe::inputCount),
			ItemStack.STRICT_CODEC.fieldOf("result").forGetter(WoodcuttingRecipe::result)
	).apply(instance, WoodcuttingRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, WoodcuttingRecipe> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, WoodcuttingRecipe::group,
			Ingredient.CONTENTS_STREAM_CODEC, WoodcuttingRecipe::ingredient,
			ByteBufCodecs.VAR_INT, WoodcuttingRecipe::inputCount,
			ItemStack.STREAM_CODEC, WoodcuttingRecipe::result,
			WoodcuttingRecipe::new);

	private final String group;
	private final Ingredient ingredient;
	private final int inputCount;
	private final ItemStack result;
	private @Nullable PlacementInfo placementInfo;

	public WoodcuttingRecipe(String group, Ingredient ingredient, int inputCount, ItemStack result) {
		this.group = group;
		this.ingredient = ingredient;
		this.inputCount = inputCount;
		this.result = result;
	}

	public Ingredient ingredient() {
		return this.ingredient;
	}

	/** How many input items one craft consumes. */
	public int inputCount() {
		return this.inputCount;
	}

	/** The result, not to be modified. Use {@link #assemble} to get a copy. */
	public ItemStack result() {
		return this.result;
	}

	@Override
	public String group() {
		return this.group;
	}

	/** Only checks the item; the menu checks that there are at least {@link #inputCount()} of them. */
	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return this.ingredient.test(input.item());
	}

	@Override
	public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
		return this.result.copy();
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public RecipeSerializer<WoodcuttingRecipe> getSerializer() {
		return ModRecipes.WOODCUTTING_SERIALIZER;
	}

	@Override
	public RecipeType<WoodcuttingRecipe> getType() {
		return ModRecipes.WOODCUTTING_TYPE;
	}

	@Override
	public PlacementInfo placementInfo() {
		if (this.placementInfo == null) {
			this.placementInfo = PlacementInfo.create(this.ingredient);
		}

		return this.placementInfo;
	}

	/** Shown like a stonecutter recipe in the recipe book, which keeps it fully vanilla on the client. */
	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.STONECUTTER;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new StonecutterRecipeDisplay(
				this.ingredient.display(),
				new SlotDisplay.ItemStackSlotDisplay(this.result),
				new SlotDisplay.ItemSlotDisplay(ModBlocks.SAWMILL_ITEM)));
	}
}
