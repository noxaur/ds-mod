package io.github.noxaur.dragen.gametest.mixin;

import io.github.noxaur.dragen.gametest.TickFrame;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class TickFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void dragen$start(boolean advanceGameTime, CallbackInfo ci) {
        TickFrame.drawing = true;
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void dragen$end(boolean advanceGameTime, CallbackInfo ci) {
        TickFrame.drawing = false;
    }
}
