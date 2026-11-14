package io.github.noxaur.dsmod.debug.mixin;

import io.github.noxaur.dsmod.debug.DsModDebug;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands packets that leave no readable client state to the capture. Each handler runs twice, and
 * only the client-thread pass reaches TAIL.
 */
@Mixin(ClientPacketListener.class)
abstract class CapturePacketsMixin {
    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void dsmod$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        DsModDebug.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void dsmod$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        DsModDebug.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void dsmod$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        DsModDebug.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void dsmod$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        DsModDebug.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void dsmod$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        DsModDebug.INSTANCE.onPacket(packet);
    }
}
