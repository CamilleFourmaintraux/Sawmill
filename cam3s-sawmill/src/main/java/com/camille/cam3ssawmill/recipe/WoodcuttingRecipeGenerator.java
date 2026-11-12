package com.camille.cam3ssawmill.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.Block;

import com.camille.cam3ssawmill.Cam3sSawmill;
import com.camille.cam3ssawmill.config.RecipeMode;
import com.camille.cam3ssawmill.config.SawmillConfig;
import com.camille.cam3ssawmill.mixin.AxeItemAccessor;

/**
 * Builds the sawmill recipes from the crafting recipes, at every data pack reload.
 *
 * <h2>Planks recipes</h2>
 * Every item of {@code #minecraft:planks} is a wood type, worth 1 plank of its own type.
 * The cost of every other item is computed from the crafting recipes, as long as all their
 * ingredients already have a wood cost:
 * <ul>
 *   <li>an ingredient that only accepts items of one wood type costs that type
 *       (oak planks, oak slabs...);</li>
 *   <li>an ingredient that accepts several wood types costs "any planks"
 *       ({@code #minecraft:planks}, sticks, {@code #minecraft:wooden_slabs}...);</li>
 *   <li>a recipe that mixes two specific wood types, or uses anything that is not
 *       made of planks (iron, string, logs...), is ignored.</li>
 * </ul>
 * This runs until nothing changes, so items made of other wooden items are found too
 * (fences use sticks, chest boats use chests...). When several recipes give the same item,
 * the cheapest one is kept.
 *
 * <p>The cost per item is then turned into a recipe according to the {@link RecipeMode}:
 * <ul>
 *   <li><b>RESTRICTED</b>, <b>VANILLA_LIKE</b> (default) and <b>EXTENDED</b>: like the stonecutter, only
 *       blocks, one plank in, as many items out as one plank gives by crafting (rounded down, at least
 *       one: 1 stair, 1 door, 2 slabs), for items up to 1, 2 and 4 planks of crafting cost, with the
 *       exceptions listed in {@link RecipeMode#ignoresCostLimit}. RESTRICTED only makes variants of one
 *       wood type; the other two also make blocks from any planks.</li>
 *   <li><b>REBALANCED</b>: any item, priced from its crafting cost and {@code maxDiscount}. By default
 *       recipes stay simple (see {@link #simpleRatio}); with {@code strictDiscount}, the sawmill uses the
 *       smallest number of planks that gives a whole number of items within the discount (see
 *       {@link #ratio}). With a 35 % strict discount: 1 plank → 1 stair, 2 planks → 1 door,
 *       3 planks → 2 fences.</li>
 * </ul>
 * One exception in every mode: one plank gives two sticks.
 *
 * <p>In every mode, a recipe is dropped when its output burns longer in a furnace than its input
 * (a cut piece of wood cannot hold more fuel than the wood it came from). The rule is decided with
 * the planks that burn; planks that do not burn (crimson, warped) get the same recipes.
 *
 * <p>Items that only exist as "any planks" (chests, crafting tables, sticks...) are made from
 * every plank type, except the types that already have their own recipe for that item.
 *
 * <h2>Log recipes</h2>
 * Only the conversions the sawmill makes sense for, all without losses:
 * <ul>
 *   <li>logs → planks, with the same count as crafting (from the crafting recipe that
 *       turns one log-like item into planks, which also covers bamboo blocks);</li>
 *   <li>log → stripped log, from the axe stripping table;</li>
 *   <li>log ↔ wood, 1 to 1 both ways, from the crafting recipes that turn logs into wood.</li>
 * </ul>
 */
public final class WoodcuttingRecipeGenerator {
	public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM, Cam3sSawmill.id("blacklist"));

	/** Wood type key used for items that can be made from any planks. */
	private static final Item ANY = Items.AIR;
	private static final double EPSILON = 1.0E-6;
	private static final int MAX_PASSES = 16;
	private static final int MAX_INPUT = 64;

	private WoodcuttingRecipeGenerator() {
	}

	/** A crafting recipe reduced to what the generator needs. */
	private record CraftingInfo(List<Ingredient> ingredients, List<Set<Item>> ingredientItems, ItemStack result) {
		Item resultItem() {
			return this.result.getItem();
		}
	}

	private record Cost(Item type, double amount) {
	}

	/** Must run once item tags are bound (see RecipeManagerMixin). */
	public static List<RecipeHolder<?>> generate(Collection<RecipeHolder<?>> loaded, SawmillConfig.Values config, FuelCheck fuel) {
		Set<Item> planks = tagItems(ItemTags.PLANKS);
		Set<Item> logs = tagItems(ItemTags.LOGS);
		Set<Item> blacklist = tagItems(BLACKLIST);

		if (planks.isEmpty()) {
			Cam3sSawmill.LOGGER.warn("The #minecraft:planks tag is empty, no sawmill recipe generated");
			return List.of();
		}

		List<CraftingInfo> crafting = collectCraftingRecipes(loaded);
		Set<ResourceKey<Recipe<?>>> existingIds = new HashSet<>();

		for (RecipeHolder<?> holder : loaded) {
			existingIds.add(holder.id());
		}

		Output output = new Output(existingIds, blacklist);

		if (config.logRecipes()) {
			addLogRecipes(crafting, planks, logs, output);
		}

		addPlankRecipes(crafting, planks, logs, config, fuel, output);

		Cam3sSawmill.LOGGER.info("Generated {} sawmill recipes", output.recipes.size());
		return output.recipes;
	}

	// ---------------------------------------------------------------- reading the crafting recipes

	private static List<CraftingInfo> collectCraftingRecipes(Collection<RecipeHolder<?>> loaded) {
		List<CraftingInfo> crafting = new ArrayList<>();

		for (RecipeHolder<?> holder : loaded) {
			Recipe<?> recipe = holder.value();

			if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
				continue;
			}

			try {
				PlacementInfo placement = craftingRecipe.placementInfo();

				// Special recipes (dyeing, map cloning...) have no fixed ingredients.
				if (placement.isImpossibleToPlace() || placement.ingredients().isEmpty()) {
					continue;
				}

				ItemStack result = resultOf(craftingRecipe);

				if (result.isEmpty()) {
					continue;
				}

				List<Ingredient> ingredients = placement.ingredients();
				List<Set<Item>> ingredientItems = new ArrayList<>(ingredients.size());
				boolean valid = true;

				for (Ingredient ingredient : ingredients) {
					Set<Item> items = ingredient.items().map(Holder::value).collect(Collectors.toCollection(LinkedHashSet::new));

					if (items.isEmpty()) {
						valid = false;
						break;
					}

					ingredientItems.add(items);
				}

				if (valid) {
					crafting.add(new CraftingInfo(ingredients, ingredientItems, result));
				}
			} catch (RuntimeException e) {
				// A broken modded recipe must not stop the others.
				Cam3sSawmill.LOGGER.debug("Ignored recipe {} for the sawmill", holder.id(), e);
			}
		}

		return crafting;
	}

	/** The result of a crafting recipe, read from its recipe book display (works for any crafting recipe class). */
	private static ItemStack resultOf(CraftingRecipe recipe) {
		for (RecipeDisplay display : recipe.display()) {
			SlotDisplay result = display.result();

			if (result instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
				return stackDisplay.stack();
			}

			if (result instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
				return new ItemStack(itemDisplay.item());
			}
		}

		return ItemStack.EMPTY;
	}

	// ---------------------------------------------------------------- log recipes

	private static void addLogRecipes(List<CraftingInfo> crafting, Set<Item> planks, Set<Item> logs, Output output) {
		// Log-like items: the #minecraft:logs tag, plus anything that crafts into planks (bamboo blocks...).
		Set<Item> logLike = new HashSet<>(logs);

		// Logs -> planks, with the crafting ingredient as is (usually a tag such as #minecraft:oak_logs).
		for (CraftingInfo recipe : crafting) {
			if (recipe.ingredients().size() != 1 || !planks.contains(recipe.resultItem()) || recipe.result().getCount() < 2) {
				continue;
			}

			Set<Item> inputs = recipe.ingredientItems().getFirst();

			if (inputs.stream().anyMatch(planks::contains)) {
				continue;
			}

			logLike.addAll(inputs);
			output.add("planks/" + path(recipe.resultItem()) + "_from_" + path(inputs.iterator().next()),
					recipe.ingredients().getFirst(), 1, recipe.result().copy());
		}

		Set<String> conversions = new HashSet<>();

		// Log -> stripped log.
		List<Map.Entry<Block, Block>> strippables = new ArrayList<>(AxeItemAccessor.cam3sSawmill$getStrippables().entrySet());
		strippables.sort(Comparator.comparingInt(entry -> BuiltInRegistries.ITEM.getId(entry.getKey().asItem())));

		for (Map.Entry<Block, Block> entry : strippables) {
			Item log = entry.getKey().asItem();
			Item stripped = entry.getValue().asItem();

			if (log != Items.AIR && stripped != Items.AIR && log != stripped && logLike.contains(log)) {
				addConversion(log, stripped, "stripping/", conversions, output);
			}
		}

		// Log <-> wood, both ways, from "4 logs -> 3 wood" style recipes.
		for (CraftingInfo recipe : crafting) {
			Item input = singleRepeatedItem(recipe);
			Item result = recipe.resultItem();

			if (input != null && input != result && logLike.contains(input) && logLike.contains(result)) {
				addConversion(input, result, "wood/", conversions, output);
				addConversion(result, input, "wood/", conversions, output);
			}
		}
	}

	private static void addConversion(Item from, Item to, String folder, Set<String> done, Output output) {
		String key = BuiltInRegistries.ITEM.getId(from) + ">" + BuiltInRegistries.ITEM.getId(to);

		if (done.add(key)) {
			output.add(folder + path(to) + "_from_" + path(from), Ingredient.of(from), 1, new ItemStack(to));
		}
	}

	/** The item used in every slot of this recipe, when all slots accept exactly the same single item. */
	private static @Nullable Item singleRepeatedItem(CraftingInfo recipe) {
		Item found = null;

		for (Set<Item> items : recipe.ingredientItems()) {
			if (items.size() != 1) {
				return null;
			}

			Item item = items.iterator().next();

			if (found == null) {
				found = item;
			} else if (found != item) {
				return null;
			}
		}

		return found;
	}

	// ---------------------------------------------------------------- planks recipes

	private static void addPlankRecipes(List<CraftingInfo> crafting, Set<Item> planks, Set<Item> logs,
			SawmillConfig.Values config, FuelCheck fuel, Output output) {
		// Item -> (wood type or ANY -> planks per item). Planks are the base: 1 plank of their own type.
		Map<Item, Map<Item, Double>> costs = new HashMap<>();
		Map<Item, ItemStack> resultStacks = new HashMap<>();

		for (Item plank : planks) {
			Map<Item, Double> own = new LinkedHashMap<>();
			own.put(plank, 1.0);
			costs.put(plank, own);
		}

		boolean changed = true;

		for (int pass = 0; pass < MAX_PASSES && changed; pass++) {
			changed = false;

			for (CraftingInfo recipe : crafting) {
				Item result = recipe.resultItem();

				if (planks.contains(result) || logs.contains(result)) {
					continue;
				}

				Cost cost = recipeCost(recipe, costs);

				if (cost == null) {
					continue;
				}

				Map<Item, Double> known = costs.computeIfAbsent(result, item -> new LinkedHashMap<>());
				Double previous = known.get(cost.type());

				if (previous == null || cost.amount() < previous - EPSILON) {
					known.put(cost.type(), cost.amount());
					resultStacks.putIfAbsent(result, recipe.result());
					changed = true;
				}
			}
		}

		List<Item> plankList = new ArrayList<>(planks);
		List<Item> results = new ArrayList<>(resultStacks.keySet());
		results.sort(Comparator.comparingInt(item -> BuiltInRegistries.ITEM.getId(item)));

		RecipeMode mode = config.recipeMode();

		for (Item result : results) {
			// Exception to the rules, in every mode: one plank gives two sticks.
			boolean stick = result == Items.STICK;

			// One-input modes only make blocks, like the stonecutter.
			if (mode.oneInput() && !stick && !(result instanceof BlockItem)) {
				continue;
			}

			Map<Item, Double> known = costs.get(result);
			ItemStack stack = resultStacks.get(result);

			for (Map.Entry<Item, Double> entry : known.entrySet()) {
				boolean anyPlanks = entry.getKey() == ANY;
				double cost = entry.getValue();

				if (!stick && anyPlanks && !mode.anyPlankItems()) {
					// Not a variant of one wood type (ladders, crafting tables, chests...).
					continue;
				}

				if (!stick && mode.oneInput() && cost > mode.maxOneInputCost() + EPSILON && !mode.ignoresCostLimit(result)) {
					// One plank for an item that costs many planks to craft would be too cheap (chests: 8).
					continue;
				}

				int[] ratio;

				if (mode.oneInput()) {
					ratio = oneInputRatio(cost, stack.getMaxStackSize());
				} else if (config.strictDiscount()) {
					ratio = ratio(cost, config.maxDiscount(), stack.getMaxStackSize());
				} else {
					ratio = simpleRatio(cost, config.maxDiscount(), stack.getMaxStackSize());
				}

				// Every plank type that does not already have its own recipe for this item (any-planks items only).
				List<Item> candidates = anyPlanks
						? plankList.stream().filter(plank -> !known.containsKey(plank)).toList()
						: List.of(entry.getKey());

				if (ratio != null && mode == RecipeMode.REBALANCED && !config.strictDiscount()) {
					// Simple recipes: lower the discount until the fuel rule holds, instead of dropping the recipe.
					ratio = fitFuel(ratio, cost, lowestBurnTime(candidates, fuel), fuel.burnTime(stack.copyWithCount(1)));
				}

				if (ratio == null) {
					continue;
				}

				int in = ratio[0];
				ItemStack out = stack.copyWithCount(ratio[1]);

				if (!anyPlanks) {
					Item plank = entry.getKey();

					// Planks that do not burn are not checked (their own items do not burn either in vanilla).
					if (!fuel.burns(plank) || fuel.burnsNoLonger(plank, in, out)) {
						output.add("planks_to/" + path(result) + "_from_" + path(plank), Ingredient.of(plank), in, out);
					}

					continue;
				}

				List<Item> inputs = anyPlanksInputs(candidates, in, out, fuel);

				if (!inputs.isEmpty()) {
					output.add("planks_to/" + path(result) + "_from_any_planks", Ingredient.of(inputs.stream()), in, out);
				}
			}
		}
	}

	/** Shortest burn time among the planks that burn, or 0 when none of them burns. */
	private static int lowestBurnTime(List<Item> planks, FuelCheck fuel) {
		int lowest = 0;

		for (Item plank : planks) {
			int burnTime = fuel.burnTime(new ItemStack(plank));

			if (burnTime > 0 && (lowest == 0 || burnTime < lowest)) {
				lowest = burnTime;
			}
		}

		return lowest;
	}

	/**
	 * Adjusts a simple REBALANCED ratio so the output does not burn longer than the input: fewer items out
	 * (1 plank → 3 slabs becomes 1 → 2), or more planks in (3 planks → 1 boat becomes 4 → 1).
	 * Null when that would cost more than crafting. Inputs that do not burn are not checked.
	 */
	static int @Nullable [] fitFuel(int[] ratio, double cost, int inputBurnTime, int outputBurnTime) {
		long fuelIn = inputBurnTime;
		long fuelOut = outputBurnTime;
		int in = ratio[0];
		int out = ratio[1];

		if (fuelIn == 0 || fuelOut == 0 || in * fuelIn >= out * fuelOut) {
			return ratio;
		}

		if (out > 1) {
			out = (int) Math.max(1, in * fuelIn / fuelOut);
		}

		if (in * fuelIn < out * fuelOut) {
			in = (int) ((out * fuelOut + fuelIn - 1) / fuelIn);
		}

		if (in > MAX_INPUT || (double) in / out > cost + EPSILON) {
			return null;
		}

		return new int[] {in, out};
	}

	/**
	 * Which planks an "any planks" recipe accepts. The fuel rule is decided with the planks that burn;
	 * planks that do not burn (crimson, warped) then get the recipe whenever at least one burning plank
	 * does, so every wood type keeps the same list of recipes.
	 */
	private static List<Item> anyPlanksInputs(List<Item> candidates, int in, ItemStack out, FuelCheck fuel) {
		List<Item> burning = candidates.stream().filter(fuel::burns).toList();

		if (burning.isEmpty()) {
			return candidates;
		}

		List<Item> passing = burning.stream().filter(plank -> fuel.burnsNoLonger(plank, in, out)).toList();

		if (passing.isEmpty()) {
			return List.of();
		}

		List<Item> inputs = new ArrayList<>(passing);
		candidates.stream().filter(plank -> !fuel.burns(plank)).forEach(inputs::add);
		return inputs;
	}

	/**
	 * REBALANCED without strict discount: a recipe that reads naturally, one plank for several items
	 * or several planks for one item, never several for several. The discount is a target, and the
	 * rounding goes in the player's favor when it cannot be met exactly:
	 * <ul>
	 *   <li>target cost of 1 plank or more: that many planks, rounded to the nearest (halves go
	 *       down), for one item (composter 3.5 planks at 10 % → 3 planks);</li>
	 *   <li>below 1 plank: one plank for that many items, rounded to the nearest (halves go up), and
	 *       never fewer than crafting gives (bowls 0.75 planks → 1 plank for 2 bowls).</li>
	 * </ul>
	 * Never more expensive than crafting (fences 1.67 planks → 1 plank for 1 fence).
	 */
	static int @Nullable [] simpleRatio(double cost, double maxDiscount, int maxStackSize) {
		double target = cost * (1.0 - maxDiscount);

		if (target >= 1.0 - EPSILON) {
			int in = (int) Math.ceil(target - 0.5 - EPSILON);
			int cheapestCraftingPrice = (int) Math.floor(cost + EPSILON);
			in = Math.max(1, Math.min(in, cheapestCraftingPrice));
			return in > MAX_INPUT ? null : new int[] {in, 1};
		}

		int out = (int) Math.floor(1.0 / target + 0.5 + EPSILON);
		int craftingYield = (int) Math.ceil(1.0 / cost - EPSILON);
		out = Math.max(1, Math.max(out, craftingYield));
		// Items that do not stack (boats) can only come out one at a time.
		return new int[] {1, Math.min(out, maxStackSize)};
	}

	/**
	 * One-input modes ratio: always one input, and as many outputs as one input gives by crafting,
	 * rounded down but at least one. Planks give 1 stair (crafting: 0.67), 1 door (0.5), 2 slabs (2).
	 */
	static int @Nullable [] oneInputRatio(double cost, int maxStackSize) {
		int out = Math.max(1, (int) Math.floor(1.0 / cost + EPSILON));
		return new int[] {1, Math.min(out, maxStackSize)};
	}

	/** Planks per crafted item, and which wood type they must be, or null if this recipe is not made only of planks. */
	private static @Nullable Cost recipeCost(CraftingInfo recipe, Map<Item, Map<Item, Double>> costs) {
		Item type = null;
		double total = 0;

		for (Set<Item> items : recipe.ingredientItems()) {
			Cost cost = ingredientCost(items, costs);

			if (cost == null) {
				return null;
			}

			if (cost.type() != ANY) {
				if (type == null) {
					type = cost.type();
				} else if (type != cost.type()) {
					// Two different wood types in one recipe: cannot be cut from a single kind of planks.
					return null;
				}
			}

			total += cost.amount();
		}

		int count = recipe.result().getCount();

		if (count <= 0) {
			return null;
		}

		return new Cost(type == null ? ANY : type, total / count);
	}

	private static @Nullable Cost ingredientCost(Set<Item> items, Map<Item, Map<Item, Double>> costs) {
		Item type = null;
		boolean anyType = false;
		double cheapest = Double.MAX_VALUE;

		for (Item item : items) {
			Map<Item, Double> known = costs.get(item);

			if (known == null || known.isEmpty()) {
				return null;
			}

			for (Map.Entry<Item, Double> entry : known.entrySet()) {
				cheapest = Math.min(cheapest, entry.getValue());

				if (entry.getKey() == ANY) {
					anyType = true;
				} else if (type == null) {
					type = entry.getKey();
				} else if (type != entry.getKey()) {
					anyType = true;
				}
			}
		}

		return new Cost(anyType || type == null ? ANY : type, cheapest);
	}

	/**
	 * Turns a cost per item into {input count, output count}: the smallest input count for which the
	 * output is a whole number, with input/output never above {@code cost} and at most
	 * {@code maxDiscount} below it. Null when no such ratio exists within one stack.
	 */
	static int @Nullable [] ratio(double cost, double maxDiscount, int maxStackSize) {
		double cheapestAllowed = cost * (1.0 - maxDiscount) - EPSILON;

		for (int in = 1; in <= MAX_INPUT; in++) {
			int out = Math.max(1, (int) Math.ceil(in / cost - EPSILON));

			if (out > maxStackSize) {
				return null;
			}

			if ((double) in / out >= cheapestAllowed) {
				return new int[] {in, out};
			}
		}

		return null;
	}

	// ---------------------------------------------------------------- helpers

	private static Set<Item> tagItems(TagKey<Item> tag) {
		Set<Item> items = new LinkedHashSet<>();
		BuiltInRegistries.ITEM.get(tag).ifPresent(set -> set.stream().map(Holder::value).forEach(items::add));
		items.remove(Items.AIR);
		return items;
	}

	private static String path(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "/" + id.getPath();
	}

	/** Collects generated recipes with unique IDs, skipping blacklisted results and IDs already used by data packs. */
	private static final class Output {
		final List<RecipeHolder<?>> recipes = new ArrayList<>();
		private final Set<ResourceKey<Recipe<?>>> usedIds;
		private final Set<Item> blacklist;

		Output(Set<ResourceKey<Recipe<?>>> usedIds, Set<Item> blacklist) {
			this.usedIds = usedIds;
			this.blacklist = blacklist;
		}

		void add(String path, Ingredient ingredient, int inputCount, ItemStack result) {
			if (this.blacklist.contains(result.getItem())) {
				return;
			}

			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Cam3sSawmill.id("generated/" + path));

			for (int suffix = 2; this.usedIds.contains(key); suffix++) {
				key = ResourceKey.create(Registries.RECIPE, Cam3sSawmill.id("generated/" + path + "_" + suffix));
			}

			this.usedIds.add(key);
			this.recipes.add(new RecipeHolder<>(key, new WoodcuttingRecipe("", ingredient, inputCount, result)));
		}
	}
}
