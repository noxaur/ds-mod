package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.CommandKeys;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "59756e373619")
@Mixin(MouseHandler.class)
abstract class MouseInputMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void dragen$button(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        CommandKeys.INSTANCE.input(button.button(), action);
    }
}
