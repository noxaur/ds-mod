package io.github.noxaur.dsmod.gametest.mixin;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner;
import net.fabricmc.fabric.impl.client.gametest.util.WindowHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric tells the game its window has focus once, as the run starts, and drops the window's own focus events. The
 * game hands a screen its mouse moves only while it has focus, so a test that took focus away would break every drag
 * after it. Each test starts with the focus Fabric gave the run.
 */
@Mixin(FabricClientGameTestRunner.class)
abstract class FocusEachTestMixin {
    @Inject(method = "setupInitialGameTestState", at = @At("TAIL"))
    private static void dsmod$focus(ClientGameTestContext context, CallbackInfo ci) {
        context.runOnClient(client -> ((WindowHooks) (Object) client.getWindow()).fabric_focus());
    }
}
