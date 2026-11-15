package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.noxaur.dsmod.edge.Cameras;
import io.github.noxaur.dsmod.edge.InventoryKey;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Free Look and Inventory Preview: each press of a key or mouse button in the world, where the game counts its own key binds' presses. */
@Mixin(KeyMapping.class)
abstract class KeyPressMixin {
    @Inject(method = "click", at = @At("HEAD"))
    private static void dsmod$pressed(InputConstants.Key key, CallbackInfo ci) {
        Cameras.INSTANCE.pressed(key);
        InventoryKey.INSTANCE.pressed(key);
    }
}
