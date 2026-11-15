package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.core.ui.Menus;
import io.github.noxaur.dsmod.edge.MenuSkin;
import io.github.noxaur.dsmod.edge.Slots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stable Menus places a chest menu by its bottom edge, and Hide Filler leaves a hidden slot looking empty. A click on a
 * hidden slot still reaches the server, because only the drawing is skipped.
 */
@Mixin(AbstractContainerScreen.class)
abstract class ContainerScreenMixin {
    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected int imageHeight;

    @Shadow
    protected Slot hoveredSlot;

    // A subclass adds its widgets after this, so they follow the new top.
    @Inject(method = "init", at = @At("TAIL"))
    private void dsmod$stable(CallbackInfo ci) {
        if ((Object) this instanceof ContainerScreen && MenuSkin.INSTANCE.getStable()) {
            topPos = Menus.INSTANCE.top(((Screen) (Object) this).height, imageHeight);
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void dsmod$hideSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (Slots.INSTANCE.hides(slot.index)) ci.cancel();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront", "extractTooltip"}, at = @At("HEAD"), cancellable = true)
    private void dsmod$hideHover(CallbackInfo ci) {
        if (hoveredSlot != null && Slots.INSTANCE.hides(hoveredSlot.index)) ci.cancel();
    }
}
