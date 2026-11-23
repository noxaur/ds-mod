package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.storage.StorageOverlays;
import io.github.noxaur.dsmod.edge.storage.StoragePreviews;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep The storage overview draws its own titles, and both of its modes draw their own tooltips. */
@Mixin(AbstractContainerScreen.class)
abstract class StorageMenuMixin {
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void dsmod$storageTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (StorageOverlays.INSTANCE.tooltip(screen, graphics, mouseX, mouseY) || StoragePreviews.INSTANCE.tooltip(screen, graphics, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void dsmod$storageLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (StorageOverlays.INSTANCE.covers((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
