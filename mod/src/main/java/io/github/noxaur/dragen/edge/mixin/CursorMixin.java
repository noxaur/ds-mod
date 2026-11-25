package io.github.noxaur.dragen.edge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.noxaur.dragen.edge.MenuCursor;
import io.github.noxaur.dragen.edge.input.InputApproved;
import kotlin.Pair;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep The server closes a menu before it opens the next, so the game takes the mouse back for a moment and then lets it
 * go at the middle of the window. */
@InputApproved(by = "noxaur", hash = "d9c797beb909")
@Mixin(MouseHandler.class)
abstract class CursorMixin {
    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Shadow
    private boolean mouseGrabbed;

    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void dragen$left(CallbackInfo ci) {
        if (!mouseGrabbed) MenuCursor.INSTANCE.getMemory().left(System.currentTimeMillis(), xpos, ypos);
    }

    @WrapOperation(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V"))
    private void dragen$back(Window window, int mode, double x, double y, Operation<Void> original) {
        Pair<Double, Double> kept = MenuCursor.INSTANCE.getMemory().reopened(System.currentTimeMillis());
        if (kept != null && MenuCursor.INSTANCE.getKeeps()) {
            xpos = kept.getFirst();
            ypos = kept.getSecond();
        }
        original.call(window, mode, xpos, ypos);
    }
}
