package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.core.container.MenuButton;
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
 * Fixed Menu Layout places a chest menu by its bottom edge, Hide Glass Panes leaves a hidden slot looking empty, and Menu Theme
 * draws the server's buttons with the kit. A click on any of these slots still reaches the server, because only the
 * drawing changes.
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
        if (Slots.INSTANCE.hides(slot.index)) {
            ci.cancel();
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MenuButton button = MenuSkin.INSTANCE.button(screen, slot.index);
        if (button != null) {
            MenuSkin.INSTANCE.drawButton(graphics, screen, slot, button);
            ci.cancel();
        }
    }

    // A button lights up itself, and keeps the server's tooltip.
    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void dsmod$buttonHover(CallbackInfo ci) {
        if (hoveredSlot != null && MenuSkin.INSTANCE.button((AbstractContainerScreen<?>) (Object) this, hoveredSlot.index) != null) ci.cancel();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront", "extractTooltip"}, at = @At("HEAD"), cancellable = true)
    private void dsmod$hideHover(CallbackInfo ci) {
        if (hoveredSlot != null && Slots.INSTANCE.hides(hoveredSlot.index)) ci.cancel();
    }
}
