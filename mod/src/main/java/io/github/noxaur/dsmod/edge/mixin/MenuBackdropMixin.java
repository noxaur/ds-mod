package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.MenuSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menu Theme' Glass look: a menu opened in the world dims the world behind it with one gradient, which would leave
 * clear glass a gray sheet. A glass menu skips the gradient and nothing else, so every other screen keeps it.
 */
@Mixin(Screen.class)
abstract class MenuBackdropMixin {
    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void dsmod$glass(GuiGraphicsExtractor graphics, CallbackInfo info) {
        if ((Object) this instanceof AbstractContainerScreen<?> screen && MenuSkin.INSTANCE.glass(screen) >= 0) info.cancel();
    }
}
