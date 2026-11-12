package com.camille.cam3ssawmill.client;

import java.util.Locale;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.camille.cam3ssawmill.config.RecipeMode;
import com.camille.cam3ssawmill.config.SawmillConfig;

/**
 * Config screen opened from Mod Menu, built with Cloth Config.
 * Saving writes the config file; the recipes are rebuilt on the next {@code /reload} or world load.
 */
public final class SawmillConfigScreen {
	private SawmillConfigScreen() {
	}

	public static Screen create(Screen parent) {
		SawmillConfig.Values current = SawmillConfig.get();
		SawmillConfig.Values defaults = SawmillConfig.Values.DEFAULT;

		// Edited values, written back when the player clicks "Save".
		RecipeMode[] recipeMode = {current.recipeMode()};
		int[] discountPercent = {(int) Math.round(current.maxDiscount() * 100)};
		boolean[] strictDiscount = {current.strictDiscount()};
		boolean[] logRecipes = {current.logRecipes()};

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("config.cam3s_sawmill.title"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.cam3s_sawmill.category.recipes"));

		general.addEntry(entries.startTextDescription(
				Component.translatable("config.cam3s_sawmill.intro", SawmillConfig.FILE_NAME).withStyle(ChatFormatting.GRAY)).build());

		EnumListEntry<RecipeMode> modeEntry = entries
				.startEnumSelector(Component.translatable("config.cam3s_sawmill.recipe_mode"), RecipeMode.class, recipeMode[0])
				.setDefaultValue(defaults.recipeMode())
				.setEnumNameProvider(mode -> Component.translatable("config.cam3s_sawmill.recipe_mode." + mode.name().toLowerCase(Locale.ROOT)))
				.setTooltip(
						Component.translatable("config.cam3s_sawmill.recipe_mode.tooltip.restricted"),
						Component.translatable("config.cam3s_sawmill.recipe_mode.tooltip.vanilla_like"),
						Component.translatable("config.cam3s_sawmill.recipe_mode.tooltip.extended"),
						Component.translatable("config.cam3s_sawmill.recipe_mode.tooltip.rebalanced"))
				.setSaveConsumer(value -> recipeMode[0] = value)
				.build();
		general.addEntry(modeEntry);

		// The two discount options are greyed out unless the mode selected on the screen is REBALANCED.
		general.addEntry(entries.startIntSlider(Component.translatable("config.cam3s_sawmill.max_discount"),
						discountPercent[0], 0, (int) Math.round(SawmillConfig.MAX_ALLOWED_DISCOUNT * 100))
				.setDefaultValue((int) Math.round(defaults.maxDiscount() * 100))
				.setTextGetter(value -> Component.literal(value + " %"))
				.setTooltip(Component.translatable("config.cam3s_sawmill.max_discount.tooltip"))
				.setRequirement(Requirement.isValue(modeEntry, RecipeMode.REBALANCED))
				.setSaveConsumer(value -> discountPercent[0] = value)
				.build());

		general.addEntry(entries.startBooleanToggle(Component.translatable("config.cam3s_sawmill.strict_discount"), strictDiscount[0])
				.setDefaultValue(defaults.strictDiscount())
				.setTooltip(Component.translatable("config.cam3s_sawmill.strict_discount.tooltip"))
				.setRequirement(Requirement.isValue(modeEntry, RecipeMode.REBALANCED))
				.setSaveConsumer(value -> strictDiscount[0] = value)
				.build());

		general.addEntry(entries.startBooleanToggle(Component.translatable("config.cam3s_sawmill.log_recipes"), logRecipes[0])
				.setDefaultValue(defaults.logRecipes())
				.setTooltip(Component.translatable("config.cam3s_sawmill.log_recipes.tooltip"))
				.setSaveConsumer(value -> logRecipes[0] = value)
				.build());

		builder.setSavingRunnable(() -> {
			SawmillConfig.set(new SawmillConfig.Values(recipeMode[0], discountPercent[0] / 100.0, strictDiscount[0], logRecipes[0]));
			SawmillConfig.save();
		});

		return builder.build();
	}
}
