# Cam3's Sawmill

A stonecutter for wood, for Minecraft **1.21.11** (Fabric). Its recipes are generated
automatically from the crafting recipes at every data pack load, so wood types added by
other mods work without any compatibility code.

## Building

Requires JDK 21 or newer.

```
./gradlew build
```

The mod jar is `build/libs/cam3s-sawmill-1.0.0.jar`.
Needed at runtime: Fabric Loader and Fabric API. Optional: Mod Menu and Cloth Config
(config screen). Install it on both the client and the server.

## The block

Same shape, placement and screen as the stonecutter. The model uses the vanilla stonecutter
model as its parent with the mod's own textures, and the screen uses the stonecutter's own
GUI textures from the game, so no vanilla file is copied into the mod.

Crafting: same pattern as the stonecutter, with stripped logs instead of stone: an iron ingot
above three stripped logs of any wood type (they can be mixed). The accepted logs are the item tag
`cam3s_sawmill:stripped_logs`: every vanilla stripped log, stem and bamboo block, plus modded ones
in the common `c:stripped_logs` tag. Like the stonecutter, which unlocks when the player gets stone,
the recipe unlocks when the player gets a stripped log. Mined faster with an axe.

## How recipes are generated

Every item of `#minecraft:planks` is a wood type, worth 1 plank. The cost of every other item
is computed from the crafting recipes whose ingredients are all made of planks (planks, slabs,
sticks, chests...), repeated until nothing changes, keeping the cheapest recipe:

- an ingredient that accepts only one wood type (oak planks) costs that type;
- an ingredient that accepts several types (`#minecraft:planks`, sticks) costs "any planks";
- recipes with two different wood types, or anything not made of planks (iron, string,
  logs...), are ignored.

The cost is then turned into a recipe according to the recipe mode.

**RESTRICTED, VANILLA_LIKE (default) and EXTENDED.** Like the stonecutter: only blocks, one plank
in, and as many items out as one plank gives by crafting, rounded down but at least one (2 slabs,
1 of everything else). They differ by the highest crafting cost an item can have to be made from a
single plank, by whether items made from any planks are included, and by a few exceptions that
ignore the cost limit:

| Mode | Highest crafting cost | Items made from any planks | Allowed whatever their cost |
|---|---|---|---|
| `RESTRICTED` | 1 plank | no, variants of one wood type only | stairs |
| `VANILLA_LIKE` | 2 planks | yes (ladders) | stairs, trapdoors, fence gates |
| `EXTENDED` | 4 planks | yes (ladders, crafting tables, composters) | stairs, trapdoors, fence gates |

**REBALANCED.** Any item, priced from its crafting cost and `maxDiscount`, never more expensive than
crafting:

- `strictDiscount` off (default): recipes stay simple, one plank for one or several items, or several
  planks for one item, never several for several. The discount is a target; when it cannot be met
  exactly, the rounding goes in the player's favor (composter, 3.5 planks, at 10 %: 3 planks; bowls:
  1 plank for 2). If the result would burn longer than the planks, the discount is reduced instead
  of dropping the recipe (slabs stay 1 → 2, boats stay 4 → 1).
- `strictDiscount` on: the smallest number of planks that gives a whole number of items within the
  discount, even when it reads oddly (7 planks for 6 ladders at 10 %).

**Every mode:** one plank gives two sticks (more with a REBALANCED discount).

| Item (oak shown) | Crafting cost | Restricted | Vanilla-like | Extended | Rebalanced 10 % | Rebalanced 35 % |
|---|---|---|---|---|---|---|
| Slab | 0.5 | 1 → 2 | 1 → 2 | 1 → 2 | 1 → 2 | 1 → 2 |
| Stick (any planks) | 0.5 | 1 → 2 | 1 → 2 | 1 → 2 | 1 → 2 | 1 → 3 |
| Button | 1 | 1 → 1 | 1 → 1 | 1 → 1 | 1 → 1 | 1 → 2 |
| Stairs | 1.5 | 1 → 1 | 1 → 1 | 1 → 1 | 1 → 1 | 1 → 1 |
| Ladder (any planks) | 1.17 | — | 1 → 1 | 1 → 1 | 1 → 1 | 1 → 1 |
| Fence | 1.67 | — | 1 → 1 | 1 → 1 | 1 → 1 | 1 → 1 |
| Door, pressure plate | 2 | — | 1 → 1 | 1 → 1 | 2 → 1 | 1 → 1 |
| Trapdoor | 3 | — | 1 → 1 | 1 → 1 | 3 → 1 | 2 → 1 |
| Fence gate | 4 | — | 1 → 1 | 1 → 1 | 4 → 1 | 3 → 1 |
| Sign | 2.17 | — | — | 1 → 1 | 2 → 1 | 1 → 1 |
| Composter (any planks) | 3.5 | — | — | 1 → 1 | 3 → 1 | 2 → 1 |
| Crafting table (any planks) | 4 | — | — | 1 → 1 | 4 → 1 | 3 → 1 |
| Chest (any planks) | 8 | — | — | — | 7 → 1 | 5 → 1 |
| Bowl (any planks) | 0.75 | — | — | — | 1 → 2 | 1 → 2 |
| Boat | 5 | — | — | — | 4 → 1 | 4 → 1 |
| Wooden tools, barrels, chest boats | | — | — | — | yes | yes |

**Fuel rule, every mode.** A recipe is dropped when its output burns longer in a furnace than its
input: a cut piece of wood cannot hold more fuel than the wood it came from. Burn times are the
server's own, including fuels added by other mods through Fabric API. The rule is decided with the
planks that burn; planks that do not burn (crimson, warped) are not checked and get the same
recipes as the others. In vanilla it removes nothing; it is a safeguard for modded items.

**From logs** (can be turned off), all without losses, in both modes:

- logs → planks, same count as crafting (4, or 2 for bamboo blocks);
- log → stripped log, from the axe stripping table;
- log ↔ wood, 1 to 1 both ways (stripped ones too).

**Excluding items:** add them to the item tag `cam3s_sawmill:blacklist` with a data pack.
**Adding recipes by hand:** data packs can add `cam3s_sawmill:woodcutting` recipes:

```json
{
  "type": "cam3s_sawmill:woodcutting",
  "ingredient": "minecraft:oak_planks",
  "input_count": 2,
  "result": { "id": "minecraft:oak_door", "count": 1 }
}
```

## Config

`config/cam3s_sawmill.json`, or in game through Mod Menu with Cloth Config installed.
Changes apply on `/reload` or when the world restarts. On a dedicated server, the
server's file is the one used.

| Option | Default | Effect |
|---|---|---|
| `recipeMode` | `VANILLA_LIKE` | `RESTRICTED`, `VANILLA_LIKE`, `EXTENDED` or `REBALANCED`, see above. |
| `maxDiscount` | `0.35` | `REBALANCED` only (greyed out otherwise in the config screen): how much cheaper than crafting a recipe should be (0.0 to 0.9). |
| `strictDiscount` | `false` | `REBALANCED` only (greyed out otherwise): follow `maxDiscount` exactly, even with odd recipes. |
| `logRecipes` | `true` | Generate logs → planks, stripping and log ↔ wood. |

## Credits

- Textures and icon: Camille. `icons/` holds the icon in two sizes for the mod page.
- Sounds: cut from sound effects on [Pixabay](https://pixabay.com/), under the
  [Pixabay Content License](https://pixabay.com/service/license-summary/).

## Technical notes

- Generation needs item tags, which are only filled at the end of a data pack load
  (`ReloadableServerResources.updateStaticRegistryTags`). `ReloadableServerResourcesMixin` triggers it
  there; `RecipeManagerMixin` adds the recipes and finalizes the recipe manager again.
- Burn times come from `FuelValues.vanillaBurnTimes`, which also fires Fabric API's fuel events.
- The server menu reads the recipe manager directly; the client reads Fabric API's synced recipes.
- Server and client sort the recipes the same way (result item ID, input count, recipe ID),
  so clicking a recipe selects the same one on both sides.
