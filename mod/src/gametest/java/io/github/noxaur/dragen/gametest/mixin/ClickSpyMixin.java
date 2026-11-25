package io.github.noxaur.dragen.gametest.mixin;

import io.github.noxaur.dragen.gametest.ClickSpy;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class ClickSpyMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void dragen$record(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        ClickSpy.INSTANCE.record(slot, button, input);
    }
}
