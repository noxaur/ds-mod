package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.BlockBreaks;
import io.github.noxaur.dragen.edge.Packets;
import io.github.noxaur.dragen.edge.Hooks;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @keep Each handler runs twice, and only the client-thread pass reaches TAIL, except the pong's, which the game handles on the network thread alone. */
@InputApproved(by = "noxaur", hash = "3f19315a300f")
@Mixin(ClientPacketListener.class)
abstract class ServerPacketsMixin {
    private static final String ON_CLIENT_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

    @Inject(
        method = "handleSystemChat",
        at = @At(
            value = "INVOKE",
            target = ON_CLIENT_THREAD,
            shift = At.Shift.AFTER
        )
    )
    private void dragen$systemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Packets.INSTANCE.systemChat(packet.content(), packet.overlay());
    }

    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void dragen$systemChatHandled(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Packets.INSTANCE.systemChatHandled();
    }

    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void dragen$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        Packets.INSTANCE.actionBar(packet.text());
    }

    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void dragen$blockChanging(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockBreaks.INSTANCE.block(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE", target = ON_CLIENT_THREAD, shift = At.Shift.AFTER))
    private void dragen$blocksChanging(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates(BlockBreaks.INSTANCE::block);
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void dragen$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        Packets.INSTANCE.block(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void dragen$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates(Packets.INSTANCE::block);
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void dragen$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Packets.INSTANCE.entityEvent(packet);
    }

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void dragen$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
        Packets.INSTANCE.time(packet.gameTime());
    }

    @Inject(method = "handlePongResponse", at = @At("TAIL"))
    private void dragen$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        Packets.INSTANCE.pong(packet.time());
    }

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void dragen$openScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        Hooks.menuChanged();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void dragen$containerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        Hooks.menuChanged();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void dragen$containerSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        Hooks.menuChanged();
    }
}
