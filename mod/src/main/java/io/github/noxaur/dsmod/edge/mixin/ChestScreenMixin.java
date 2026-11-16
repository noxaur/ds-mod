package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.noxaur.dsmod.edge.MenuSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A chest menu draws its texture in two parts, the rows and then the inventory, at the screen's middle rather than at
 * its own top. Themed Menus draws the whole menu with the kit in place of the first part and skips the second.
 * Otherwise both parts move by what Stable Menus moved the menu.
 */
@Mixin(ContainerScreen.class)
abstract class ChestScreenMixin {
    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V", ordinal = 0))
    private void dsmod$rows(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int tw, int th, Operation<Void> original) {
        if (MenuSkin.INSTANCE.skins((ContainerScreen) (Object) this)) MenuSkin.INSTANCE.background(graphics, (ContainerScreen) (Object) this);
        else original.call(graphics, pipeline, texture, x, y + dsmod$moved(), u, v, w, h, tw, th);
    }

    @WrapOperation(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V", ordinal = 1))
    private void dsmod$inventory(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int tw, int th, Operation<Void> original) {
        if (!MenuSkin.INSTANCE.skins((ContainerScreen) (Object) this)) original.call(graphics, pipeline, texture, x, y + dsmod$moved(), u, v, w, h, tw, th);
    }

    private int dsmod$moved() {
        ContainerScreen screen = (ContainerScreen) (Object) this;
        ContainerScreenAccessor at = (ContainerScreenAccessor) screen;
        return at.getTopPos() - (screen.height - at.getImageHeight()) / 2;
    }
}
