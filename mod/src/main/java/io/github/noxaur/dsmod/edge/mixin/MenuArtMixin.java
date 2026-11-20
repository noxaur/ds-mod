package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.noxaur.dsmod.edge.MenuSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class MenuArtMixin {
    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V", at = @At("HEAD"), cancellable = true)
    private void dsmod$texture(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color, CallbackInfo ci) {
        if (MenuSkin.INSTANCE.texture((GuiGraphicsExtractor) (Object) this, texture, u, v)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", at = @At("HEAD"), cancellable = true)
    private void dsmod$sprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (MenuSkin.INSTANCE.sprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height, x, y, width, height)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V", at = @At("HEAD"), cancellable = true)
    private void dsmod$part(RenderPipeline pipeline, Identifier sprite, int spriteWidth, int spriteHeight, int textureX, int textureY, int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (MenuSkin.INSTANCE.sprite((GuiGraphicsExtractor) (Object) this, sprite, x - textureX, y - textureY, spriteWidth, spriteHeight, x, y, width, height)) ci.cancel();
    }
}
