package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.debug.capture.CaptureSession
import io.github.noxaur.dsmod.debug.capture.toCaptureJson
import io.github.noxaur.dsmod.edge.DsModClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import java.nio.file.Path

object DsModDebug : ClientModInitializer {
    @Volatile
    private var capture: CaptureSession? = null

    /** Starts a capture, or saves the running one. Returns the capture file either way. */
    fun toggleCapture(client: Minecraft): Path {
        val running = capture
        if (running != null) {
            capture = null
            running.close()
            return running.file
        }
        return CaptureSession(client.gameDirectory.toPath().resolve("dsmod/captures"), DsModClient.game).also { capture = it }.file
    }

    /** Called by CapturePacketsMixin on the client thread. */
    fun onPacket(packet: Packet<*>) {
        capture?.packet(packet)
    }

    /** Called by CaptureInputMixin on the client thread, for `swing` or `use`. */
    fun onInput(client: Minecraft, type: String) {
        capture?.input(client, type)
    }

    override fun onInitializeClient() {
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> capture?.unload(entity) }
        ClientReceiveMessageEvents.CHAT.register { message, _, sender, _, _ ->
            capture?.record("chat") {
                buildJsonObject {
                    sender?.let { put("sender", it.name) }
                    message.toCaptureJson().forEach { (key, value) -> put(key, value) }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client -> capture?.tick(client) }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("dscapture").executes { context ->
                    val wasRunning = capture != null
                    val file = toggleCapture(context.source.client)
                    context.source.sendFeedback(Component.literal(if (wasRunning) "Capture saved: $file" else "Capture started: $file"))
                    1
                },
            )
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { capture?.close() }
    }
}
