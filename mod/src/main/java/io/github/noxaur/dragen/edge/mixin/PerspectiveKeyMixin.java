package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.Cameras;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@InputApproved(by = "noxaur", hash = "f323c3fd012a")
@Mixin(Minecraft.class)
abstract class PerspectiveKeyMixin {
    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V"))
    private CameraType dragen$skip(CameraType next) {
        return Cameras.INSTANCE.land(next);
    }
}
