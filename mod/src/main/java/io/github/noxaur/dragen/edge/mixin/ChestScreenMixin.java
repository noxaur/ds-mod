package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.ContainerScreensKt;
import io.github.noxaur.dragen.edge.storage.StorageOverlays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
abstract class ChestScreenMixin {
    @Inject(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V", ordinal = 0), cancellable = true)
    private void dragen$storage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (StorageOverlays.INSTANCE.background((ContainerScreen) (Object) this, graphics, mouseX, mouseY)) ci.cancel();
    }

    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"), index = 3)
    private int dragen$moved(int y) {
        ContainerScreen screen = (ContainerScreen) (Object) this;
        return y + ContainerScreensKt.getContainerTop(screen) - (screen.height - ContainerScreensKt.getContainerHeight(screen)) / 2;
    }
}
