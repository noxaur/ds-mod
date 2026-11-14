package io.github.noxaur.dsmod.debug.mixin;

import io.github.noxaur.dsmod.debug.DsModDebug;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hands the attack and use buttons to the capture before the game acts on them. */
@Mixin(Minecraft.class)
abstract class CaptureInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void dsmod$swing(CallbackInfoReturnable<Boolean> cir) {
        DsModDebug.INSTANCE.onInput((Minecraft) (Object) this, "swing");
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void dsmod$use(CallbackInfo ci) {
        DsModDebug.INSTANCE.onInput((Minecraft) (Object) this, "use");
    }
}
