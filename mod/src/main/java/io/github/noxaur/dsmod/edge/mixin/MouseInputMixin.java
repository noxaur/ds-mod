package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.CommandKeys;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "b812670e4219")
@Mixin(MouseHandler.class)
abstract class MouseInputMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void dsmod$button(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        CommandKeys.INSTANCE.input(button.button(), action);
    }
}
