package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.Packets;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the edge the packets that leave no client state to read. Each handler runs twice, and only the
 * client-thread pass reaches TAIL.
 */
@Mixin(ClientPacketListener.class)
abstract class ServerPacketsMixin {
    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void dsmod$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        Packets.INSTANCE.actionBar(packet.text());
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void dsmod$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Packets.INSTANCE.entityEvent(packet);
    }
}
