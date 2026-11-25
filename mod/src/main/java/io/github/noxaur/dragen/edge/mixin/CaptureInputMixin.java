package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.capture.Recorder;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@InputApproved(by = "noxaur", hash = "b3a7c890e7b3")
@Mixin(Minecraft.class)
abstract class CaptureInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void dragen$swing(CallbackInfoReturnable<Boolean> cir) {
        Recorder.INSTANCE.onInput((Minecraft) (Object) this, "swing");
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void dragen$use(CallbackInfo ci) {
        Recorder.INSTANCE.onInput((Minecraft) (Object) this, "use");
    }
}
