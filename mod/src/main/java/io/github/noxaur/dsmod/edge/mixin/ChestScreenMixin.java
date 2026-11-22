package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.ContainerScreensKt;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ContainerScreen.class)
abstract class ChestScreenMixin {
    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"), index = 3)
    private int dsmod$moved(int y) {
        ContainerScreen screen = (ContainerScreen) (Object) this;
        return y + ContainerScreensKt.getContainerTop(screen) - (screen.height - ContainerScreensKt.getContainerHeight(screen)) / 2;
    }
}
