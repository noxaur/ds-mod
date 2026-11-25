package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.SlotGuards;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@InputApproved(by = "noxaur", hash = "51d154f9dfe8")
@Mixin(LocalPlayer.class)
abstract class DropKeyMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void dragen$locked(boolean all, CallbackInfoReturnable<Boolean> ci) {
        if (SlotGuards.INSTANCE.keepsHeld()) ci.setReturnValue(false);
    }
}
