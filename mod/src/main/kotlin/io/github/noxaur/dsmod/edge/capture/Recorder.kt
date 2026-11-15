package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.edge.DsModClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import java.nio.file.Path

/**
 * Holds the one capture that may be running. Nothing records until [start], which `/dscapture` and `-Pbrief`
 * in the debug build call. While none runs, every hook here is one
 * null check on the client thread.
 */
object Recorder {
    @Volatile
    private var session: CaptureSession? = null

    val running get() = session != null

    /** Starts recording into a new file in [dir], and returns the file. */
    fun start(dir: Path): Path {
        check(session == null) { "A capture is already running." }
        return CaptureSession(dir, DsModClient.game).also { session = it }.file
    }

    /**
     * Stops recording. The caller closes the returned session, which waits for the file, on a thread that may
     * wait. Null when nothing was recording.
     */
    fun stop(): CaptureSession? = session?.also {
        session = null
        it.detach()
    }

    /** Adds a raw record to the running capture. */
    fun record(type: String, data: () -> JsonObject) {
        session?.record(type, data)
    }

    /** Called by `CapturePacketsMixin` on the client thread. */
    fun onPacket(packet: Packet<*>) {
        session?.packet(packet)
    }

    /** Called by `CaptureInputMixin` on the client thread, for `swing` or `use`. */
    fun onInput(client: Minecraft, type: String) {
        session?.input(client, type)
    }

    fun register() {
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> session?.unload(entity) }
        ClientReceiveMessageEvents.CHAT.register { message, _, sender, _, _ ->
            session?.record("chat") {
                buildJsonObject {
                    sender?.let { put("sender", it.name) }
                    message.toCaptureJson().forEach { (key, value) -> put(key, value) }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client -> session?.tick(client) }
    }
}
