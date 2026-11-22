package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.SlotGuards;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@InputApproved(by = "noxaur", hash = "1b430b670807")
@Mixin(LocalPlayer.class)
abstract class DropKeyMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void dsmod$locked(boolean all, CallbackInfoReturnable<Boolean> ci) {
        if (SlotGuards.INSTANCE.keepsHeld()) ci.setReturnValue(false);
    }
}
