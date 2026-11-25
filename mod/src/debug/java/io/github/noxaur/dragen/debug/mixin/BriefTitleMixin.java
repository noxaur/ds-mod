package io.github.noxaur.dragen.debug.mixin;

import io.github.noxaur.dragen.debug.DragenDebug;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class BriefTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void dragen$briefTitle(CallbackInfoReturnable<String> cir) {
        String name = DragenDebug.INSTANCE.briefName();
        if (name != null) cir.setReturnValue(name + " | " + cir.getReturnValue());
    }
}
