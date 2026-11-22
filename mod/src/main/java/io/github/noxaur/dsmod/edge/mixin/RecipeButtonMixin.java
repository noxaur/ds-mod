package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.MenuSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu Theme: the recipe book's button on a themed menu is drawn by the kit. Its clicks stay the game's. */
@Mixin(ImageButton.class)
abstract class RecipeButtonMixin {
    @Shadow @Final protected WidgetSprites sprites;

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void dsmod$theme(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (RecipeBookComponent.RECIPE_BUTTON_SPRITES.equals(sprites) && MenuSkin.INSTANCE.recipeButton(graphics, (AbstractWidget) (Object) this)) ci.cancel();
    }
}
