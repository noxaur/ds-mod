package io.github.noxaur.dsmod.gametest.mixin;

import io.github.noxaur.dsmod.gametest.ActionSpy;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class ActionSpyMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void dsmod$attack(CallbackInfoReturnable<Boolean> cir) {
        ActionSpy.INSTANCE.setAttacks(ActionSpy.INSTANCE.getAttacks() + 1);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void dsmod$use(CallbackInfo ci) {
        ActionSpy.INSTANCE.setUses(ActionSpy.INSTANCE.getUses() + 1);
    }
}
