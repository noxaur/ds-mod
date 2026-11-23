package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.core.container.MenuButton;
import io.github.noxaur.dsmod.core.ui.Menus;
import io.github.noxaur.dsmod.edge.MenuSkin;
import io.github.noxaur.dsmod.edge.Slots;
import io.github.noxaur.dsmod.edge.input.InputApproved;
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

@InputApproved(by = "noxaur", hash = "5147eddf96ea")
@Mixin(AbstractContainerScreen.class)
abstract class ContainerScreenMixin {
    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected int imageHeight;

    @Shadow
    protected Slot hoveredSlot;

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

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void dsmod$buttonHover(CallbackInfo ci) {
        if (hoveredSlot != null && MenuSkin.INSTANCE.button((AbstractContainerScreen<?>) (Object) this, hoveredSlot.index) != null) ci.cancel();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront", "extractTooltip"}, at = @At("HEAD"), cancellable = true)
    private void dsmod$hideHover(CallbackInfo ci) {
        if (hoveredSlot != null && Slots.INSTANCE.hides(hoveredSlot.index)) ci.cancel();
    }
}
