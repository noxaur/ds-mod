package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.noxaur.dsmod.edge.AutoClicks;
import io.github.noxaur.dsmod.edge.Cameras;
import io.github.noxaur.dsmod.edge.Hooks;
import io.github.noxaur.dsmod.edge.InventoryKey;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "8ed9d9fed544")
@Mixin(KeyMapping.class)
abstract class KeyPressMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void dsmod$pressed(InputConstants.Key key, CallbackInfo ci) {
        AutoClicks.INSTANCE.pressed(key);
        Cameras.INSTANCE.pressed(key);
        InventoryKey.INSTANCE.pressed(key);
        Hooks.pressed(key);
    }
}
