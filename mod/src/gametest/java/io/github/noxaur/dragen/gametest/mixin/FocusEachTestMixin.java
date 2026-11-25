package io.github.noxaur.dragen.gametest.mixin;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner;
import net.fabricmc.fabric.impl.client.gametest.util.WindowHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep Fabric gives the game its window focus once, as the run starts, and drops the window's own focus events. The game
 * hands a screen its mouse moves only while it has focus, so each test restores the focus Fabric gave. */
@Mixin(FabricClientGameTestRunner.class)
abstract class FocusEachTestMixin {
    @Inject(method = "setupInitialGameTestState", at = @At("TAIL"))
    private static void dragen$focus(ClientGameTestContext context, CallbackInfo ci) {
        context.runOnClient(client -> ((WindowHooks) (Object) client.getWindow()).fabric_focus());
    }
}
