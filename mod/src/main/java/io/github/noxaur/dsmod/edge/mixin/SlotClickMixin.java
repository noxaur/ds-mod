package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.SkyBlockMenuClicks;
import io.github.noxaur.dsmod.edge.SlotGuards;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "ec34bb415176")
@Mixin(AbstractContainerScreen.class)
abstract class SlotClickMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void dsmod$locked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (SkyBlockMenuClicks.INSTANCE.opens(slot, input) || SlotGuards.INSTANCE.refuses((AbstractContainerScreen<?>) (Object) this, slot, button, input)) ci.cancel();
    }
}
