package io.github.noxaur.dragen.gametest.mixin;

import io.github.noxaur.dragen.gametest.TickFrame;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class TickFrameBlurMixin {
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void dragen$skip(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (TickFrame.drawing) ci.cancel();
    }
}
