package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.noxaur.dsmod.edge.AutoClicks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
abstract class ClickCapMixin {
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startAttack()Z"))
    private boolean dsmod$attack(Minecraft self, Operation<Boolean> original) {
        return AutoClicks.INSTANCE.allows(true) && original.call(self);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"))
    private void dsmod$use(Minecraft self, Operation<Void> original) {
        if (AutoClicks.INSTANCE.allows(false)) original.call(self);
    }
}
