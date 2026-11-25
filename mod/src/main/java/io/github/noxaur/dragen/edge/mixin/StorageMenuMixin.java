package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.storage.StorageOverlays;
import io.github.noxaur.dragen.edge.storage.StoragePreviews;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class StorageMenuMixin {
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void dragen$storageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (StorageOverlays.INSTANCE.tooltip(screen, graphics, mouseX, mouseY) || StoragePreviews.INSTANCE.tooltip(screen, graphics, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void dragen$storageLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (StorageOverlays.INSTANCE.covers((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
