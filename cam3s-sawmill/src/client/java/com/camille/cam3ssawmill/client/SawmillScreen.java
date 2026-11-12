package com.camille.cam3ssawmill.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.camille.cam3ssawmill.menu.SawmillMenu;
import com.camille.cam3ssawmill.recipe.WoodcuttingRecipe;

/**
 * The sawmill screen. It uses the stonecutter's own background and sprites from the game's resources
 * (nothing is copied into the mod): a 4 x 3 grid of recipe buttons with a scroll bar.
 *
 * <p>With advanced recipes, a recipe can need several input items: it is dimmed while the
 * input slot holds fewer, and the tooltip shows how many it uses.
 */
public class SawmillScreen extends AbstractContainerScreen<SawmillMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller");
	private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
	private static final Identifier RECIPE_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier RECIPE_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
	private static final Identifier RECIPE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final int TEXTURE_SIZE = 256;

	private static final int SCROLLER_X = 119;
	private static final int SCROLLER_Y = 15;
	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;
	private static final int SCROLLER_TRACK_HEIGHT = 54;

	private static final int RECIPES_X = 52;
	private static final int RECIPES_Y = 14;
	private static final int RECIPES_COLUMNS = 4;
	private static final int RECIPES_ROWS = 3;
	private static final int RECIPES_VISIBLE = RECIPES_COLUMNS * RECIPES_ROWS;
	private static final int BUTTON_WIDTH = 16;
	private static final int BUTTON_HEIGHT = 18;
	private static final int NOT_ENOUGH_INPUT_COLOR = 0x90000000;

	private float scrollOffset;
	private boolean scrolling;
	private int startIndex;
	private boolean displayRecipes;

	public SawmillScreen(SawmillMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		menu.registerUpdateListener(this::containerChanged);
		--this.titleLabelY;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int left = this.leftPos;
		int top = this.topPos;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0, 0, this.imageWidth, this.imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);

		int scrollerY = (int) ((SCROLLER_TRACK_HEIGHT - SCROLLER_HEIGHT) * this.scrollOffset);
		Identifier scroller = this.isScrollBarActive() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, scroller, left + SCROLLER_X, top + SCROLLER_Y + scrollerY, SCROLLER_WIDTH, SCROLLER_HEIGHT);

		if (this.displayRecipes) {
			this.renderButtons(graphics, mouseX, mouseY);
			this.renderRecipes(graphics);
		}
	}

	private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
		List<RecipeHolder<WoodcuttingRecipe>> recipes = this.menu.getRecipesForInput();

		for (int index = this.startIndex; index < this.startIndex + RECIPES_VISIBLE && index < recipes.size(); index++) {
			int x = this.buttonX(index);
			int y = this.buttonY(index);
			boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + BUTTON_WIDTH && mouseY < y + BUTTON_HEIGHT;

			Identifier sprite = index == this.menu.getSelectedRecipeIndex() ? RECIPE_SELECTED_SPRITE
					: hovered ? RECIPE_HIGHLIGHTED_SPRITE : RECIPE_SPRITE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);

			if (!this.menu.hasEnoughInput(index)) {
				graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, NOT_ENOUGH_INPUT_COLOR);
			}
		}
	}

	private void renderRecipes(GuiGraphics graphics) {
		List<RecipeHolder<WoodcuttingRecipe>> recipes = this.menu.getRecipesForInput();

		for (int index = this.startIndex; index < this.startIndex + RECIPES_VISIBLE && index < recipes.size(); index++) {
			ItemStack result = recipes.get(index).value().result();
			int x = this.buttonX(index);
			int y = this.buttonY(index) + 1;
			graphics.renderItem(result, x, y);
			graphics.renderItemDecorations(this.font, result, x, y);
		}
	}

	@Override
	protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderTooltip(graphics, mouseX, mouseY);

		if (!this.displayRecipes) {
			return;
		}

		List<RecipeHolder<WoodcuttingRecipe>> recipes = this.menu.getRecipesForInput();

		for (int index = this.startIndex; index < this.startIndex + RECIPES_VISIBLE && index < recipes.size(); index++) {
			int x = this.buttonX(index);
			int y = this.buttonY(index);

			if (mouseX >= x && mouseY >= y && mouseX < x + BUTTON_WIDTH && mouseY < y + BUTTON_HEIGHT) {
				WoodcuttingRecipe recipe = recipes.get(index).value();
				List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, recipe.result()));
				Component inputName = this.menu.inputSlot.getItem().getHoverName();
				ChatFormatting color = this.menu.hasEnoughInput(index) ? ChatFormatting.GRAY : ChatFormatting.RED;
				if (recipe.inputCount() > 1) {
					lines.add(Component.translatable("gui.cam3s_sawmill.uses", recipe.inputCount(), inputName).withStyle(color));
				}

				graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.scrolling = false;

		if (this.displayRecipes) {
			double mouseX = event.x();
			double mouseY = event.y();
			List<RecipeHolder<WoodcuttingRecipe>> recipes = this.menu.getRecipesForInput();

			for (int index = this.startIndex; index < this.startIndex + RECIPES_VISIBLE && index < recipes.size(); index++) {
				double relativeX = mouseX - this.buttonX(index);
				double relativeY = mouseY - this.buttonY(index);

				if (relativeX >= 0 && relativeY >= 0 && relativeX < BUTTON_WIDTH && relativeY < BUTTON_HEIGHT
						&& this.menu.clickMenuButton(this.minecraft.player, index)) {
					float pitch = this.menu.hasEnoughInput(index) ? 1.0F : 0.6F;
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, pitch));
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
					return true;
				}
			}

			int scrollerLeft = this.leftPos + SCROLLER_X;
			int scrollerTop = this.topPos + SCROLLER_Y;

			if (mouseX >= scrollerLeft && mouseX < scrollerLeft + SCROLLER_WIDTH
					&& mouseY >= scrollerTop && mouseY < scrollerTop + SCROLLER_TRACK_HEIGHT) {
				this.scrolling = true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.scrolling && this.isScrollBarActive()) {
			int trackTop = this.topPos + SCROLLER_Y;
			int trackBottom = trackTop + SCROLLER_TRACK_HEIGHT;
			this.scrollOffset = ((float) event.y() - trackTop - SCROLLER_HEIGHT / 2.0F) / (trackBottom - trackTop - SCROLLER_HEIGHT);
			this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0F, 1.0F);
			this.startIndex = (int) (this.scrollOffset * this.getOffscreenRows() + 0.5) * RECIPES_COLUMNS;
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}

		if (this.isScrollBarActive()) {
			int offscreenRows = this.getOffscreenRows();
			this.scrollOffset = Mth.clamp(this.scrollOffset - (float) scrollY / offscreenRows, 0.0F, 1.0F);
			this.startIndex = (int) (this.scrollOffset * offscreenRows + 0.5) * RECIPES_COLUMNS;
		}

		return true;
	}

	private int buttonX(int index) {
		return this.leftPos + RECIPES_X + (index - this.startIndex) % RECIPES_COLUMNS * BUTTON_WIDTH;
	}

	private int buttonY(int index) {
		return this.topPos + RECIPES_Y + (index - this.startIndex) / RECIPES_COLUMNS * BUTTON_HEIGHT + 1;
	}

	private boolean isScrollBarActive() {
		return this.displayRecipes && this.menu.getRecipesForInput().size() > RECIPES_VISIBLE;
	}

	private int getOffscreenRows() {
		return (this.menu.getRecipesForInput().size() + RECIPES_COLUMNS - 1) / RECIPES_COLUMNS - RECIPES_ROWS;
	}

	private void containerChanged() {
		this.displayRecipes = this.menu.hasRecipes();

		if (!this.displayRecipes) {
			this.scrollOffset = 0.0F;
			this.startIndex = 0;
		}
	}
}
