package io.github.noxaur.dsmod.gametest.mixin;

import io.github.noxaur.dsmod.gametest.TickFrame;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class TickFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void dsmod$start(boolean advanceGameTime, CallbackInfo ci) {
        TickFrame.drawing = true;
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void dsmod$end(boolean advanceGameTime, CallbackInfo ci) {
        TickFrame.drawing = false;
    }
}
