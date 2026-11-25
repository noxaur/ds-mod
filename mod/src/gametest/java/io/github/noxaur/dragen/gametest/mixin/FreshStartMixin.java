package io.github.noxaur.dragen.gametest.mixin;

import io.github.noxaur.dragen.gametest.FreshStart;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricClientGameTestRunner.class, remap = false)
abstract class FreshStartMixin {
    @Inject(method = "setupInitialGameTestState", at = @At("TAIL"))
    private static void dragen$freshStart(ClientGameTestContext context, CallbackInfo ci) {
        FreshStart.INSTANCE.before(context);
    }
}
