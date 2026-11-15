package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.CommandKeys;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Command Binds: every press and release of a mouse button, with a screen open or not, before the game acts on it. */
@Mixin(MouseHandler.class)
abstract class MouseInputMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void dsmod$button(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        CommandKeys.INSTANCE.input(button.button(), action);
    }
}
