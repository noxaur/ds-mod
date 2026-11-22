package io.github.noxaur.dsmod.edge.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.noxaur.dsmod.edge.Cameras;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@InputApproved(by = "noxaur", hash = "3be6f5bde644")
@Mixin(MouseHandler.class)
abstract class MouseTurnMixin {
    @WrapWithCondition(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private boolean dsmod$freeLook(LocalPlayer player, double x, double y) {
        return !Cameras.INSTANCE.turn(x, y);
    }
}
