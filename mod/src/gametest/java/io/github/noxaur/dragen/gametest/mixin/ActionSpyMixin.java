package io.github.noxaur.dragen.gametest.mixin;

import io.github.noxaur.dragen.gametest.ActionSpy;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class ActionSpyMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void dragen$attack(CallbackInfoReturnable<Boolean> cir) {
        ActionSpy.INSTANCE.setAttacks(ActionSpy.INSTANCE.getAttacks() + 1);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void dragen$use(CallbackInfo ci) {
        ActionSpy.INSTANCE.setUses(ActionSpy.INSTANCE.getUses() + 1);
    }
}
