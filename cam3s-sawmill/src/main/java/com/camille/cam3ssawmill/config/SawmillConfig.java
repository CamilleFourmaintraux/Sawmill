package com.camille.cam3ssawmill.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import com.camille.cam3ssawmill.Cam3sSawmill;

/**
 * Recipe generation settings, stored in {@code config/cam3s_sawmill.json}.
 *
 * <p>Recipes are generated when data packs load, so changes apply on {@code /reload}
 * or when the world restarts. On a multiplayer server, the server's file is the one used.
 *
 * <p>The values live in an immutable record swapped as a whole, because the generator runs
 * on a worker thread while the config screen writes from the client thread.
 */
public final class SawmillConfig {
	public static final String FILE_NAME = Cam3sSawmill.MOD_ID + ".json";
	public static final double DEFAULT_MAX_DISCOUNT = 0.35;
	public static final double MAX_ALLOWED_DISCOUNT = 0.9;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * @param recipeMode  which planks recipes are generated, see {@link RecipeMode}
	 * @param strictDiscount REBALANCED mode only: false keeps recipes simple (1 → 1, 1 → many, many → 1), rounding
	 *                    in the player's favor; true follows maxDiscount as closely as possible, even with odd
	 *                    recipes such as 7 planks for 6 ladders
	 * @param maxDiscount REBALANCED mode only: how much cheaper than crafting a recipe can be, from 0 to 0.9
	 *                    (rounding to whole items needs some margin; 0 means never cheaper)
	 * @param logRecipes  also generate logs → planks, log → stripped log and log ↔ wood
	 */
	public record Values(RecipeMode recipeMode, double maxDiscount, boolean strictDiscount, boolean logRecipes) {
		public static final Values DEFAULT = new Values(RecipeMode.VANILLA_LIKE, DEFAULT_MAX_DISCOUNT, false, true);

		public Values {
			if (recipeMode == null) {
				recipeMode = RecipeMode.VANILLA_LIKE;
			}

			if (Double.isNaN(maxDiscount)) {
				maxDiscount = DEFAULT_MAX_DISCOUNT;
			}

			maxDiscount = Math.clamp(maxDiscount, 0.0, MAX_ALLOWED_DISCOUNT);
		}
	}

	private static volatile Values values = Values.DEFAULT;

	private SawmillConfig() {
	}

	public static Values get() {
		return values;
	}

	public static void set(Values newValues) {
		values = newValues;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Reads the file, or creates it with the default values. Missing fields keep their default value. */
	public static synchronized void load() {
		Path file = path();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				FileFormat read = GSON.fromJson(reader, FileFormat.class);
				values = read != null ? read.toValues() : Values.DEFAULT;
			} catch (IOException | JsonParseException e) {
				Cam3sSawmill.LOGGER.error("Could not read {}, using default values", file, e);
				values = Values.DEFAULT;
				return;
			}
		}

		// Writes the file back, so new options appear in it after an update.
		save();
	}

	public static synchronized void save() {
		Path file = path();

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(FileFormat.of(values)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Cam3sSawmill.LOGGER.error("Could not write {}", file, e);
		}
	}

	/** JSON layout. Boxed types so that missing fields can be told apart and replaced by defaults. */
	private static final class FileFormat {
		String _comment = "Changes apply on /reload or when the world restarts. recipeMode: RESTRICTED, VANILLA_LIKE, EXTENDED or REBALANCED. maxDiscount (0.0 to 0.9) and strictDiscount only apply to REBALANCED.";
		RecipeMode recipeMode;
		Double maxDiscount;
		Boolean strictDiscount;
		Boolean logRecipes;

		static FileFormat of(Values values) {
			FileFormat format = new FileFormat();
			format.recipeMode = values.recipeMode();
			format.maxDiscount = values.maxDiscount();
			format.strictDiscount = values.strictDiscount();
			format.logRecipes = values.logRecipes();
			return format;
		}

		Values toValues() {
			Values defaults = Values.DEFAULT;
			return new Values(
					this.recipeMode != null ? this.recipeMode : defaults.recipeMode(),
					this.maxDiscount != null ? this.maxDiscount : defaults.maxDiscount(),
					this.strictDiscount != null ? this.strictDiscount : defaults.strictDiscount(),
					this.logRecipes != null ? this.logRecipes : defaults.logRecipes());
		}
	}
}
