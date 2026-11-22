package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.Cameras;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
abstract class PerspectiveKeyMixin {
    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V"))
    private CameraType dsmod$skip(CameraType next) {
        return Cameras.INSTANCE.land(next);
    }
}
