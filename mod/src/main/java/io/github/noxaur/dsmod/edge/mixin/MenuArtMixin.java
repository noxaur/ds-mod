package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.noxaur.dsmod.edge.MenuSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
abstract class MenuArtMixin {
    @ModifyVariable(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 8)
    private int dsmod$texture(
        int color, @Local(argsOnly = true) Identifier texture, @Local(ordinal = 0, argsOnly = true) float u, @Local(ordinal = 1, argsOnly = true) float v
    ) {
        return MenuSkin.INSTANCE.texture((GuiGraphicsExtractor) (Object) this, texture, u, v) ? 0 : color;
    }

    @ModifyVariable(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", at = @At("HEAD"), argsOnly = true)
    private Identifier dsmod$sprite(
        Identifier sprite, @Local(ordinal = 0, argsOnly = true) int x, @Local(ordinal = 1, argsOnly = true) int y,
        @Local(ordinal = 2, argsOnly = true) int width, @Local(ordinal = 3, argsOnly = true) int height
    ) {
        return MenuSkin.INSTANCE.sprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height, x, y, width, height);
    }

    @ModifyVariable(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V", at = @At("HEAD"), argsOnly = true)
    private Identifier dsmod$part(
        Identifier sprite, @Local(ordinal = 0, argsOnly = true) int spriteWidth, @Local(ordinal = 1, argsOnly = true) int spriteHeight,
        @Local(ordinal = 2, argsOnly = true) int textureX, @Local(ordinal = 3, argsOnly = true) int textureY,
        @Local(ordinal = 4, argsOnly = true) int x, @Local(ordinal = 5, argsOnly = true) int y,
        @Local(ordinal = 6, argsOnly = true) int width, @Local(ordinal = 7, argsOnly = true) int height
    ) {
        return MenuSkin.INSTANCE.sprite((GuiGraphicsExtractor) (Object) this, sprite, x - textureX, y - textureY, spriteWidth, spriteHeight, x, y, width, height);
    }
}
