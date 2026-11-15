package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.noxaur.dsmod.edge.Cameras;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Free Look: the mouse's turn goes to the camera, and the player's head, which the server sees, stays as it was. */
@Mixin(MouseHandler.class)
abstract class MouseTurnMixin {
    @WrapWithCondition(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private boolean dsmod$freeLook(LocalPlayer player, double x, double y) {
        return !Cameras.INSTANCE.turn(x, y);
    }
}
