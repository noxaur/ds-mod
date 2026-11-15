package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.capture.CaptureIncompleteException
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.DsModClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Holds the one capture that may be running. Nothing records until [start], and while none runs every hook here is
 * one null check on the client thread. Whoever starts a capture keeps the session and is the only one to [stop] it.
 */
object Recorder {
    @Volatile
    private var session: CaptureSession? = null

    val running get() = session != null

    /** Starts recording into a new file in [dir]. */
    fun start(dir: Path): CaptureSession {
        check(session == null) { "A capture is already running." }
        return CaptureSession(dir, DsModClient.game).also { session = it }
    }

    /** Stops [own], then finishes its file off the client thread and tells [done] there whether the file is whole. */
    fun stop(own: CaptureSession, done: (whole: Boolean) -> Unit = {}) {
        check(session === own) { "That capture is not the running one." }
        session = null
        own.detach()
        Background.io.execute { done(finish(own)) }
    }

    private fun finish(session: CaptureSession) = try {
        session.close()
        true
    } catch (e: CaptureIncompleteException) {
        LoggerFactory.getLogger("dsmod").error("The capture {} was not fully written", session.file, e)
        false
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

    /**
     * Quitting: stops what still runs, which keeps its file, and waits for every capture still being finished,
     * because the background thread dies with the game. Whoever wants their capture gone stops it first.
     */
    fun quit() {
        session?.let(::stop)
        Background.io.shutdown()
        Background.io.awaitTermination(1, TimeUnit.MINUTES)
    }
}
