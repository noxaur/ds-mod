package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.capture.Recorder;
import io.github.noxaur.dsmod.edge.input.InputApproved;
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

/** @keep Each handler runs twice, and only the client-thread pass reaches TAIL or the point after the thread check. A block
 * update is read there, before the level holds it, so its line can say what the block was. */
@InputApproved(by = "noxaur", hash = "7408f3e32f26")
@Mixin(ClientPacketListener.class)
abstract class CapturePacketsMixin {
    private static final String ON_CLIENT_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

    @Inject(method = "handleParticleEvent", at = @At("TAIL"))
    private void dsmod$particles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void dsmod$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void dsmod$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void dsmod$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleBlockDestruction", at = @At("TAIL"))
    private void dsmod$breaking(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }

    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void dsmod$levelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        Recorder.INSTANCE.onPacket(packet);
    }
}
