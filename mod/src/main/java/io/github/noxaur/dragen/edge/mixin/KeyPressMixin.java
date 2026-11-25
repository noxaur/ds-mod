package io.github.noxaur.dragen.edge.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.noxaur.dragen.edge.AutoClicks;
import io.github.noxaur.dragen.edge.Cameras;
import io.github.noxaur.dragen.edge.Hooks;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "39c4a6d17280")
@Mixin(KeyMapping.class)
abstract class KeyPressMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void dragen$pressed(InputConstants.Key key, CallbackInfo ci) {
        AutoClicks.INSTANCE.pressed(key);
        Cameras.INSTANCE.pressed(key);
        Hooks.pressed(key);
    }
}
