package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.QuieterAfks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep The game draws the hovered item's tooltip after the screen, so the blackout goes in before it, and a dimmed menu skips it. */
@Mixin(AbstractContainerScreen.class)
abstract class QuietMenuMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void dsmod$blackout(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        QuieterAfks.draw((AbstractContainerScreen<?>) (Object) this, graphics);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void dsmod$dimmed(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (QuieterAfks.getDimmed()) ci.cancel();
    }
}
