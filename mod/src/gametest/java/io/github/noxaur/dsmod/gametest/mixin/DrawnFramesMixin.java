package io.github.noxaur.dsmod.gametest.mixin;

import io.github.noxaur.dsmod.gametest.DrawnFrames;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class DrawnFramesMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void dsmod$frame(boolean advanceGameTime, CallbackInfo ci) {
        DrawnFrames.count++;
    }
}
