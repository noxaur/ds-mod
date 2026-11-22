package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.capture.Recorder;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep Each handler runs twice, and only the client-thread pass reaches TAIL. */
@InputApproved(by = "noxaur", hash = "df3c170949d4")
@Mixin(ClientPacketListener.class)
abstract class CapturePacketsMixin {
    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void dsmod$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void dsmod$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void dsmod$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void dsmod$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }
}
