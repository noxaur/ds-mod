package io.github.noxaur.dragen.debug.mixin;

import io.github.noxaur.dragen.debug.dragon.DevOverlays;
import io.github.noxaur.dragen.debug.zone.ZoneTool;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class ZonePickerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void dragen$pickCorner(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        ZoneTool zones = DevOverlays.INSTANCE.getZones();
        if (zones != null && zones.button(button.button(), action)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void dragen$nudgeCorner(long handle, double x, double y, CallbackInfo ci) {
        ZoneTool zones = DevOverlays.INSTANCE.getZones();
        if (zones != null && zones.scroll(y)) ci.cancel();
    }
}
