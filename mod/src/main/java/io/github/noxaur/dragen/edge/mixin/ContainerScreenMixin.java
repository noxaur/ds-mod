package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.Slots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class ContainerScreenMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void dragen$hideSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (Slots.INSTANCE.hides(slot.index)) ci.cancel();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront", "extractTooltip"}, at = @At("HEAD"), cancellable = true)
    private void dragen$hideHover(CallbackInfo ci) {
        if (hoveredSlot != null && Slots.INSTANCE.hides(hoveredSlot.index)) ci.cancel();
    }
}
