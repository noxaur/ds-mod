package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.noxaur.dsmod.edge.input.Clicks;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "e804340006be")
@Mixin(Minecraft.class)
abstract class ClickCapMixin {
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startAttack()Z"))
    private boolean dsmod$attack(Minecraft self, Operation<Boolean> original) {
        return Clicks.INSTANCE.allows(self.options.keyAttack) && original.call(self);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V"))
    private void dsmod$use(Minecraft self, Operation<Void> original) {
        if (Clicks.INSTANCE.allows(self.options.keyUse)) original.call(self);
    }

    @Inject(method = "handleKeybinds", at = @At("RETURN"))
    private void dsmod$settle(CallbackInfo ci) {
        Clicks.INSTANCE.settle();
    }
}
