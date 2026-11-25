package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.capture.Recorder;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep Each handler runs twice, and only the client-thread pass reaches TAIL or the point after the thread check. */
@InputApproved(by = "noxaur", hash = "a54d780856cb")
@Mixin(ClientPacketListener.class)
abstract class CapturePacketsMixin {
    private static final String ON_CLIENT_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void dragen$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void dragen$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void dragen$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void dragen$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleBlockDestruction", at = @At("TAIL"))
    private void dragen$breaking(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void dragen$levelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }
}
